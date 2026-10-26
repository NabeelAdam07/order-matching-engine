package com.nabeeladam.orderbook;

/**
 * An order sent to the book.
 *
 * Prices are whole numbers of "ticks" (for example pence), never doubles.
 * Floating point cannot represent most decimal prices exactly (0.1 + 0.2 != 0.3),
 * and an exchange must compare prices exactly.
 *
 * Only remainingQuantity changes after creation, as the order is filled.
 */
public final class Order {
    private final long id;
    private final Side side;
    private final OrderType type;
    private final long price;          // in ticks; ignored for MARKET orders
    private final long quantity;       // original size
    private long remainingQuantity;

    private Order(long id, Side side, OrderType type, long price, long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive, got " + quantity);
        }
        if (type == OrderType.LIMIT && price <= 0) {
            throw new IllegalArgumentException("limit price must be positive, got " + price);
        }
        this.id = id;
        this.side = side;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
        this.remainingQuantity = quantity;
    }

    public static Order limit(long id, Side side, long price, long quantity) {
        return new Order(id, side, OrderType.LIMIT, price, quantity);
    }

    public static Order market(long id, Side side, long quantity) {
        return new Order(id, side, OrderType.MARKET, 0, quantity);
    }

    public long id() { return id; }
    public Side side() { return side; }
    public OrderType type() { return type; }
    public long price() { return price; }
    public long quantity() { return quantity; }
    public long remainingQuantity() { return remainingQuantity; }
    public long filledQuantity() { return quantity - remainingQuantity; }
    public boolean isFilled() { return remainingQuantity == 0; }

    void fill(long amount) {
        if (amount <= 0 || amount > remainingQuantity) {
            throw new IllegalStateException("invalid fill " + amount + " for order " + id);
        }
        remainingQuantity -= amount;
    }

    @Override
    public String toString() {
        return type + " " + side + " #" + id + " " + remainingQuantity + "/" + quantity
                + (type == OrderType.LIMIT ? " @ " + price : "");
    }
}
