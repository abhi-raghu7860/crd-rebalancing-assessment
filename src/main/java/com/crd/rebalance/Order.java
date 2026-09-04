package com.crd.rebalance;

import java.math.BigDecimal;

/**
 * A generated order. Quantity is always a positive whole number of shares; direction lives in
 * {@link #side()} rather than in the sign, so an order can never be silently misread.
 *
 * @param symbol   security traded
 * @param side     buy or sell
 * @param quantity whole shares, strictly positive
 * @param notional {@code quantity * unitPrice}, the cash value of the order
 */
public record Order(String symbol, Side side, long quantity, BigDecimal notional) {

    public Order {
        if (quantity <= 0) {
            throw new IllegalArgumentException("an order must have a positive quantity; "
                    + "suppress the line instead of emitting a zero-quantity order");
        }
    }

    /** Signed effect on the cash balance: sells add cash, buys consume it. */
    public BigDecimal cashEffect() {
        return side == Side.SELL ? notional : notional.negate();
    }

    /** Signed effect on the held position, for post-trade valuation. */
    public BigDecimal signedQuantity() {
        return BigDecimal.valueOf(side == Side.SELL ? -quantity : quantity);
    }
}
