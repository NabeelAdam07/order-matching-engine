package com.nabeeladam.orderbook;

/** Which side of the book an order sits on. */
public enum Side {
    BUY,
    SELL;

    public Side opposite() {
        return this == BUY ? SELL : BUY;
    }
}
