package com.nabeeladam.orderbook;

/**
 * One match between an incoming (aggressor) order and a resting order.
 * The trade always happens at the resting order's price: the order that was
 * already waiting in the book set the price, so it gets the price it asked for.
 */
public record Trade(long buyOrderId, long sellOrderId, long price, long quantity, Side aggressorSide) {
}
