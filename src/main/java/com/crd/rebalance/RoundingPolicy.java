package com.crd.rebalance;

import java.math.RoundingMode;

/**
 * How a fractional share quantity is reduced to a whole number of shares.
 *
 * <p>The assessment specification does not state a rounding rule, yet the three defensible
 * choices produce materially different trades and different cash outcomes. The policy is
 * therefore configuration rather than a hardcoded assumption, so the suite can pin the
 * behaviour of each one. See {@code docs/TEST-STRATEGY.md} ambiguity AMB-01.
 *
 * <p>Rounding is expressed in terms of order <em>magnitude</em>, not signed value, because the
 * sign of a quantity carries direction rather than size. {@link #ROUND_UP} therefore rounds
 * away from zero on both sides rather than toward positive infinity.
 */
public enum RoundingPolicy {

    /**
     * Round the magnitude down: trade the largest whole quantity that does not exceed the gap.
     * Never overshoots the target and, when buys and sells are driven by offsetting variances,
     * keeps the block self-funding. This is the recommended default.
     */
    TRUNCATE(RoundingMode.DOWN),

    /** Round to the nearest whole share. May require cash the account does not hold. */
    HALF_UP(RoundingMode.HALF_UP),

    /** Round the magnitude up: always close the gap fully, accepting an overshoot past target. */
    ROUND_UP(RoundingMode.UP);

    private final RoundingMode mode;

    RoundingPolicy(RoundingMode mode) {
        this.mode = mode;
    }

    RoundingMode mode() {
        return mode;
    }
}
