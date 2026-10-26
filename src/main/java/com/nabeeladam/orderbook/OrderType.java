package com.nabeeladam.orderbook;

/**
 * LIMIT  - trade at this price or better; any unfilled quantity rests in the book.
 * MARKET - trade immediately at the best available prices; any unfilled quantity is dropped
 *          (it never rests, because a market order has no price to rest at).
 */
public enum OrderType {
    LIMIT,
    MARKET
}
