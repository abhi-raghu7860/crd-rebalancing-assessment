package com.crd.rebalance;

/**
 * Direction of a generated order.
 *
 * <p>Derived from the sign of the target variance, which the specification defines as
 * {@code current% - target%}. A negative variance means the account is underweight and must
 * {@link #BUY}; a positive variance means it is overweight and must {@link #SELL}.
 */
public enum Side {
    BUY,
    SELL
}
