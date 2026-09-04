package com.crd.rebalance;

import java.math.BigDecimal;

/**
 * One holding line of a rebalancing request.
 *
 * <p>Both percentages are percentage <em>points</em> of the account's total assets, not
 * relative percentages of the position. A {@code currentPct} of 10 against a {@code targetPct}
 * of 20 on a $100,000 account is a $10,000 gap, not a $1,000 one.
 *
 * @param symbol     ticker, used as the line's identity within an account
 * @param targetPct  percentage of assets the account should hold
 * @param currentPct percentage of assets the account holds today
 * @param unitPrice  price per share, strictly positive
 */
public record Security(String symbol, BigDecimal targetPct, BigDecimal currentPct, BigDecimal unitPrice) {

    public Security {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        requirePercentage(targetPct, "targetPct", symbol);
        requirePercentage(currentPct, "currentPct", symbol);
        if (unitPrice == null) {
            throw new IllegalArgumentException("unitPrice must not be null for " + symbol
                    + " (a missing price indicates a stale or failed price feed)");
        }
        if (unitPrice.signum() <= 0) {
            throw new IllegalArgumentException("unitPrice must be strictly positive for " + symbol
                    + " but was " + unitPrice.toPlainString());
        }
    }

    /** Convenience factory so test data reads as plain numbers. */
    public static Security of(String symbol, String targetPct, String currentPct, String unitPrice) {
        return new Security(symbol, new BigDecimal(targetPct), new BigDecimal(currentPct), new BigDecimal(unitPrice));
    }

    /** Target variance in percentage points: {@code current% - target%}. */
    public BigDecimal variancePct() {
        return currentPct.subtract(targetPct);
    }

    private static void requirePercentage(BigDecimal value, String field, String symbol) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null for " + symbol);
        }
        if (value.signum() < 0 || value.compareTo(Rebalancing.HUNDRED) > 0) {
            throw new IllegalArgumentException(field + " must be between 0 and 100 for " + symbol
                    + " but was " + value.toPlainString());
        }
    }
}
