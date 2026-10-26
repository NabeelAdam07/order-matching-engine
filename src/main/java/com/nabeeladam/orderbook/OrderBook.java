package com.nabeeladam.orderbook;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.OptionalLong;
import java.util.TreeMap;

/**
 * A limit order book for one instrument, matching with price-time priority:
 *   1. Price: the best price trades first (highest bid, lowest ask).
 *   2. Time:  at the same price, the order that arrived first trades first.
 *
 * Data structures
 *   bids / asks : TreeMap from price -> FIFO queue of orders at that price.
 *                 A TreeMap keeps prices sorted, so the best price is firstEntry() in O(log n).
 *                 Bids are sorted high-to-low and asks low-to-high, so "first" always means "best".
 *                 Each queue is an ArrayDeque: arrival order IS time priority, no timestamps needed.
 *   ordersById  : HashMap from id -> resting order, so cancel can find an order in O(1).
 *
 * Not thread-safe by design: a real matching engine processes orders for one instrument
 * on a single thread, in sequence, which keeps results deterministic and avoids locking.
 */
public final class OrderBook {

    private final NavigableMap<Long, ArrayDeque<Order>> bids = new TreeMap<>(Comparator.reverseOrder());
    private final NavigableMap<Long, ArrayDeque<Order>> asks = new TreeMap<>();
    private final Map<Long, Order> ordersById = new HashMap<>();

    /**
     * Matches an incoming order against the opposite side of the book.
     * Any unfilled quantity of a LIMIT order then rests in the book; a MARKET order's remainder is dropped.
     *
     * @return the trades produced, in the order they happened (empty if nothing matched)
     */
    public List<Trade> submit(Order incoming) {
        if (ordersById.containsKey(incoming.id())) {
            throw new IllegalArgumentException("duplicate order id " + incoming.id());
        }

        List<Trade> trades = new ArrayList<>();
        NavigableMap<Long, ArrayDeque<Order>> opposite = incoming.side() == Side.BUY ? asks : bids;

        while (!incoming.isFilled() && !opposite.isEmpty()) {
            Map.Entry<Long, ArrayDeque<Order>> bestLevel = opposite.firstEntry();
            long bestPrice = bestLevel.getKey();

            if (incoming.type() == OrderType.LIMIT && !crosses(incoming, bestPrice)) {
                break; // the best opposite price is worse than this order's limit
            }

            ArrayDeque<Order> queue = bestLevel.getValue();
            Order resting = queue.peekFirst();
            long fillQuantity = Math.min(incoming.remainingQuantity(), resting.remainingQuantity());

            incoming.fill(fillQuantity);
            resting.fill(fillQuantity);
            trades.add(tradeBetween(incoming, resting, bestPrice, fillQuantity));

            if (resting.isFilled()) {
                queue.pollFirst();
                ordersById.remove(resting.id());
                if (queue.isEmpty()) {
                    opposite.pollFirstEntry(); // remove the now-empty price level
                }
            }
        }

        if (!incoming.isFilled() && incoming.type() == OrderType.LIMIT) {
            rest(incoming);
        }
        return trades;
    }

    /**
     * Removes a resting order from the book.
     *
     * @return true if the order was found and cancelled, false if it was unknown or already filled
     */
    public boolean cancel(long orderId) {
        Order order = ordersById.remove(orderId);
        if (order == null) {
            return false;
        }
        NavigableMap<Long, ArrayDeque<Order>> side = order.side() == Side.BUY ? bids : asks;
        ArrayDeque<Order> queue = side.get(order.price());
        queue.remove(order); // O(k) in the number of orders at this one price level
        if (queue.isEmpty()) {
            side.remove(order.price());
        }
        return true;
    }

    public OptionalLong bestBid() {
        return bids.isEmpty() ? OptionalLong.empty() : OptionalLong.of(bids.firstKey());
    }

    public OptionalLong bestAsk() {
        return asks.isEmpty() ? OptionalLong.empty() : OptionalLong.of(asks.firstKey());
    }

    /** Total resting quantity at one price on one side (0 if the level is empty). */
    public long quantityAt(Side side, long price) {
        ArrayDeque<Order> queue = (side == Side.BUY ? bids : asks).get(price);
        if (queue == null) {
            return 0;
        }
        long total = 0;
        for (Order order : queue) {
            total += order.remainingQuantity();
        }
        return total;
    }

    /** Ids of the orders resting at one price, in time-priority order. */
    public List<Long> orderIdsAt(Side side, long price) {
        ArrayDeque<Order> queue = (side == Side.BUY ? bids : asks).get(price);
        if (queue == null) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>(queue.size());
        for (Order order : queue) {
            ids.add(order.id());
        }
        return ids;
    }

    public int restingOrderCount() {
        return ordersById.size();
    }

    public boolean contains(long orderId) {
        return ordersById.containsKey(orderId);
    }

    private static boolean crosses(Order incoming, long oppositePrice) {
        return incoming.side() == Side.BUY
                ? oppositePrice <= incoming.price()   // a buyer accepts any ask at or below its limit
                : oppositePrice >= incoming.price();  // a seller accepts any bid at or above its limit
    }

    private static Trade tradeBetween(Order incoming, Order resting, long price, long quantity) {
        return incoming.side() == Side.BUY
                ? new Trade(incoming.id(), resting.id(), price, quantity, Side.BUY)
                : new Trade(resting.id(), incoming.id(), price, quantity, Side.SELL);
    }

    private void rest(Order order) {
        NavigableMap<Long, ArrayDeque<Order>> side = order.side() == Side.BUY ? bids : asks;
        side.computeIfAbsent(order.price(), price -> new ArrayDeque<>()).addLast(order);
        ordersById.put(order.id(), order);
    }
}
