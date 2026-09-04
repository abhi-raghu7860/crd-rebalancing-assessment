package com.crd.rebalance;

import java.math.BigDecimal;

/**
 * Tunable rules the specification leaves open. Every field here corresponds to an entry in the
 * ambiguity log in {@code docs/TEST-STRATEGY.md}; making them explicit is what lets the suite
 * test the alternatives rather than silently adopt one.
 *
 * @param roundingPolicy   how fractional share quantities are reduced to whole shares (AMB-01)
 * @param toleranceBandPct variance within this many percentage points is left alone (AMB-02)
 * @param minTradeNotional orders below this cash value are suppressed as uneconomic (AMB-06)
 * @param lotSize          quantities are reduced to a multiple of this round lot (AMB-06)
 * @param sellsFirst       emit sells ahead of buys so a fully invested account can self-fund (AMB-05)
 */
public record RebalanceConfig(RoundingPolicy roundingPolicy, BigDecimal toleranceBandPct,
                              BigDecimal minTradeNotional, long lotSize, boolean sellsFirst) {

    public RebalanceConfig {
        if (roundingPolicy == null) {
            throw new IllegalArgumentException("roundingPolicy must not be null");
        }
        if (toleranceBandPct == null || toleranceBandPct.signum() < 0) {
            throw new IllegalArgumentException("toleranceBandPct must be present and non-negative");
        }
        if (minTradeNotional == null || minTradeNotional.signum() < 0) {
            throw new IllegalArgumentException("minTradeNotional must be present and non-negative");
        }
        if (lotSize < 1) {
            throw new IllegalArgumentException("lotSize must be at least 1 but was " + lotSize);
        }
    }

    /**
     * The behaviour the assessment scenario implies: truncate to whole shares, act on any
     * non-zero variance, no minimum, no round lots, sells sequenced before buys.
     */
    public static RebalanceConfig defaults() {
        return new RebalanceConfig(RoundingPolicy.TRUNCATE, BigDecimal.ZERO, BigDecimal.ZERO, 1L, true);
    }

    public RebalanceConfig withRoundingPolicy(RoundingPolicy policy) {
        return new RebalanceConfig(policy, toleranceBandPct, minTradeNotional, lotSize, sellsFirst);
    }

    public RebalanceConfig withToleranceBandPct(String pct) {
        return new RebalanceConfig(roundingPolicy, new BigDecimal(pct), minTradeNotional, lotSize, sellsFirst);
    }

    public RebalanceConfig withMinTradeNotional(String notional) {
        return new RebalanceConfig(roundingPolicy, toleranceBandPct, new BigDecimal(notional), lotSize, sellsFirst);
    }

    public RebalanceConfig withLotSize(long lot) {
        return new RebalanceConfig(roundingPolicy, toleranceBandPct, minTradeNotional, lot, sellsFirst);
    }

    public RebalanceConfig withSellsFirst(boolean value) {
        return new RebalanceConfig(roundingPolicy, toleranceBandPct, minTradeNotional, lotSize, value);
    }
}
