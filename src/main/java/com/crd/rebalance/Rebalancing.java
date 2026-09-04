package com.crd.rebalance;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Shared numeric constants for the rebalancing domain.
 *
 * <p>Every monetary and percentage value in this package is {@link BigDecimal}. Binary floating
 * point is never used: {@code 0.1 + 0.2} is not {@code 0.3} in a double, and a rebalancer that
 * drifts by fractions of a cent per line drifts by real money across a book of accounts.
 */
final class Rebalancing {

    static final BigDecimal HUNDRED = new BigDecimal("100");

    /** Working precision for divisions that need not terminate, such as value-to-percentage. */
    static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

    /** Scale used when reporting percentages, generous enough to expose sub-basis-point drift. */
    static final int PCT_SCALE = 10;

    private Rebalancing() {
    }
}
