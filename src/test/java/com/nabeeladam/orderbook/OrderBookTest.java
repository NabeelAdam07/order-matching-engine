package com.nabeeladam.orderbook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderBookTest {

    private OrderBook book;

    @BeforeEach
    void setUp() {
        book = new OrderBook();
    }

    private static Order buy(long id, long price, long qty) { return Order.limit(id, Side.BUY, price, qty); }
    private static Order sell(long id, long price, long qty) { return Order.limit(id, Side.SELL, price, qty); }

    @Nested
    @DisplayName("Resting orders")
    class Resting {

        @Test
        @DisplayName("orders that don't cross rest in the book")
        void nonCrossingOrdersRest() {
            assertTrue(book.submit(buy(1, 99, 10)).isEmpty());
            assertTrue(book.submit(sell(2, 101, 10)).isEmpty());

            assertEquals(OptionalLong.of(99), book.bestBid());
            assertEquals(OptionalLong.of(101), book.bestAsk());
            assertEquals(2, book.restingOrderCount());
        }

        @Test
        @DisplayName("best bid is the highest bid, best ask is the lowest ask")
        void bestPricesAreSortedCorrectly() {
            book.submit(buy(1, 97, 5));
            book.submit(buy(2, 99, 5));
            book.submit(buy(3, 98, 5));
            book.submit(sell(4, 103, 5));
            book.submit(sell(5, 101, 5));
            book.submit(sell(6, 102, 5));

            assertEquals(OptionalLong.of(99), book.bestBid());
            assertEquals(OptionalLong.of(101), book.bestAsk());
        }

        @Test
        @DisplayName("an empty book has no best prices")
        void emptyBook() {
            assertTrue(book.bestBid().isEmpty());
            assertTrue(book.bestAsk().isEmpty());
        }
    }

    @Nested
    @DisplayName("Matching")
    class Matching {

        @Test
        @DisplayName("a crossing buy trades at the resting ask's price, not its own")
        void tradesAtRestingPrice() {
            book.submit(sell(1, 100, 10));

            List<Trade> trades = book.submit(buy(2, 105, 10));

            assertEquals(List.of(new Trade(2, 1, 100, 10, Side.BUY)), trades);
            assertEquals(0, book.restingOrderCount());
        }

        @Test
        @DisplayName("price priority: the best price is matched first")
        void pricePriority() {
            book.submit(sell(1, 102, 10));
            book.submit(sell(2, 100, 10));
            book.submit(sell(3, 101, 10));

            List<Trade> trades = book.submit(buy(4, 102, 10));

            assertEquals(1, trades.size());
            assertEquals(2, trades.get(0).sellOrderId());
            assertEquals(100, trades.get(0).price());
        }

        @Test
        @DisplayName("time priority: at the same price, the earlier order is matched first")
        void timePriority() {
            book.submit(sell(1, 100, 10));
            book.submit(sell(2, 100, 10));
            book.submit(sell(3, 100, 10));

            List<Trade> trades = book.submit(buy(4, 100, 15));

            assertEquals(List.of(
                    new Trade(4, 1, 100, 10, Side.BUY),
                    new Trade(4, 2, 100, 5, Side.BUY)), trades);
            assertEquals(List.of(2L, 3L), book.orderIdsAt(Side.SELL, 100));
            assertEquals(15, book.quantityAt(Side.SELL, 100));
        }

        @Test
        @DisplayName("a partially filled incoming order rests the remainder at its limit")
        void partialFillRestsRemainder() {
            book.submit(sell(1, 100, 4));

            List<Trade> trades = book.submit(buy(2, 100, 10));

            assertEquals(4, trades.get(0).quantity());
            assertTrue(book.bestAsk().isEmpty());
            assertEquals(OptionalLong.of(100), book.bestBid());
            assertEquals(6, book.quantityAt(Side.BUY, 100));
        }

        @Test
        @DisplayName("a partially filled resting order keeps its place in the queue")
        void partiallyFilledRestingOrderKeepsPriority() {
            book.submit(sell(1, 100, 10));
            book.submit(sell(2, 100, 10));

            book.submit(buy(3, 100, 4));

            assertEquals(List.of(1L, 2L), book.orderIdsAt(Side.SELL, 100));
            assertEquals(16, book.quantityAt(Side.SELL, 100));
        }

        @Test
        @DisplayName("a large order sweeps several price levels, each at its own price")
        void sweepsMultipleLevels() {
            book.submit(sell(1, 100, 5));
            book.submit(sell(2, 101, 5));
            book.submit(sell(3, 102, 5));

            List<Trade> trades = book.submit(buy(4, 101, 20));

            assertEquals(List.of(
                    new Trade(4, 1, 100, 5, Side.BUY),
                    new Trade(4, 2, 101, 5, Side.BUY)), trades);
            assertEquals(OptionalLong.of(102), book.bestAsk());   // 102 is above the limit, untouched
            assertEquals(OptionalLong.of(101), book.bestBid());   // 10 left over rests at 101
            assertEquals(10, book.quantityAt(Side.BUY, 101));
        }

        @Test
        @DisplayName("an incoming sell matches the highest bid first (mirror of the buy side)")
        void sellSideMirrorsBuySide() {
            book.submit(buy(1, 98, 5));
            book.submit(buy(2, 100, 5));

            List<Trade> trades = book.submit(sell(3, 97, 7));

            assertEquals(List.of(
                    new Trade(2, 3, 100, 5, Side.SELL),
                    new Trade(1, 3, 98, 2, Side.SELL)), trades);
            assertEquals(3, book.quantityAt(Side.BUY, 98));
        }

        @Test
        @DisplayName("a limit order that doesn't reach the best price does not trade")
        void limitNotReached() {
            book.submit(sell(1, 101, 10));

            assertTrue(book.submit(buy(2, 100, 10)).isEmpty());
            assertEquals(2, book.restingOrderCount());
        }
    }

    @Nested
    @DisplayName("Market orders")
    class MarketOrders {

        @Test
        @DisplayName("a market order takes the best prices regardless of level")
        void marketOrderSweeps() {
            book.submit(sell(1, 100, 5));
            book.submit(sell(2, 150, 5));

            List<Trade> trades = book.submit(Order.market(3, Side.BUY, 8));

            assertEquals(List.of(
                    new Trade(3, 1, 100, 5, Side.BUY),
                    new Trade(3, 2, 150, 3, Side.BUY)), trades);
        }

        @Test
        @DisplayName("a market order's unfilled remainder is dropped, never rested")
        void marketRemainderDropped() {
            book.submit(sell(1, 100, 5));

            book.submit(Order.market(2, Side.BUY, 50));

            assertEquals(0, book.restingOrderCount());
            assertTrue(book.bestBid().isEmpty());
        }

        @Test
        @DisplayName("a market order into an empty book does nothing")
        void marketIntoEmptyBook() {
            assertTrue(book.submit(Order.market(1, Side.SELL, 10)).isEmpty());
            assertEquals(0, book.restingOrderCount());
        }
    }

    @Nested
    @DisplayName("Cancelling")
    class Cancelling {

        @Test
        @DisplayName("cancel removes a resting order and its empty price level")
        void cancelRemovesOrder() {
            book.submit(buy(1, 99, 10));

            assertTrue(book.cancel(1));
            assertFalse(book.contains(1));
            assertTrue(book.bestBid().isEmpty());
        }

        @Test
        @DisplayName("a cancelled order can no longer be matched")
        void cancelledOrderNotMatched() {
            book.submit(sell(1, 100, 10));
            book.submit(sell(2, 100, 10));
            book.cancel(1);

            List<Trade> trades = book.submit(buy(3, 100, 10));

            assertEquals(2, trades.get(0).sellOrderId());
        }

        @Test
        @DisplayName("cancelling an unknown or already-filled order returns false")
        void cancelUnknown() {
            book.submit(sell(1, 100, 10));
            book.submit(buy(2, 100, 10)); // fills order 1 completely

            assertFalse(book.cancel(1));
            assertFalse(book.cancel(999));
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("rejects zero or negative quantity")
        void rejectsBadQuantity() {
            assertThrows(IllegalArgumentException.class, () -> buy(1, 100, 0));
            assertThrows(IllegalArgumentException.class, () -> buy(1, 100, -5));
        }

        @Test
        @DisplayName("rejects a limit order with no positive price")
        void rejectsBadPrice() {
            assertThrows(IllegalArgumentException.class, () -> sell(1, 0, 10));
        }

        @Test
        @DisplayName("rejects a duplicate id of an order already resting")
        void rejectsDuplicateId() {
            book.submit(buy(1, 99, 10));
            assertThrows(IllegalArgumentException.class, () -> book.submit(buy(1, 98, 10)));
        }
    }
}
