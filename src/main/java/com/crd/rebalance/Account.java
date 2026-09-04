package com.crd.rebalance;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The account being rebalanced.
 *
 * <p>Structural validation happens at construction so an invalid request can never reach the
 * engine. Target percentages must sum to exactly 100; current percentages may sum to less than
 * 100, in which case the shortfall is uninvested cash (see assumption ASM-04).
 *
 * @param id          account identifier
 * @param totalAssets total assets under management, holdings plus cash
 * @param vestedPct   percentage of assets that is vested and therefore tradeable
 * @param cash        cash balance available to fund purchases, included in {@code totalAssets}
 * @param securities  holding lines; symbols must be unique
 */
public record Account(String id, BigDecimal totalAssets, BigDecimal vestedPct, BigDecimal cash,
                      List<Security> securities) {

    public Account {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("account id must not be blank");
        }
        if (totalAssets == null || totalAssets.signum() < 0) {
            throw new IllegalArgumentException("totalAssets must be present and non-negative but was " + totalAssets);
        }
        if (vestedPct == null || vestedPct.signum() < 0 || vestedPct.compareTo(Rebalancing.HUNDRED) > 0) {
            throw new IllegalArgumentException("vestedPct must be between 0 and 100 but was " + vestedPct);
        }
        if (cash == null || cash.signum() < 0) {
            throw new IllegalArgumentException("cash must be present and non-negative but was " + cash);
        }
        if (securities == null) {
            throw new IllegalArgumentException("securities must not be null; use an empty list for an empty account");
        }
        securities = List.copyOf(securities);
        rejectDuplicateSymbols(securities);
        if (!securities.isEmpty()) {
            requireTargetsSumTo100(securities);
            requireCurrentsWithin100(securities);
        }
    }

    /** The classic case from the assessment: fully vested, no starting cash. */
    public static Account fullyVested(String id, String totalAssets, List<Security> securities) {
        return new Account(id, new BigDecimal(totalAssets), Rebalancing.HUNDRED, BigDecimal.ZERO, securities);
    }

    /** Assets that may actually be traded. Unvested assets are frozen (assumption ASM-05). */
    public BigDecimal investableBase() {
        return totalAssets.multiply(vestedPct).divide(Rebalancing.HUNDRED, Rebalancing.MC);
    }

    private static void rejectDuplicateSymbols(List<Security> securities) {
        Set<String> seen = new HashSet<>();
        for (Security s : securities) {
            if (!seen.add(s.symbol())) {
                throw new IllegalArgumentException("duplicate security symbol: " + s.symbol());
            }
        }
    }

    private static void requireTargetsSumTo100(List<Security> securities) {
        BigDecimal sum = securities.stream().map(Security::targetPct)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(Rebalancing.HUNDRED) != 0) {
            throw new IllegalArgumentException("target percentages must sum to 100 but summed to "
                    + sum.toPlainString());
        }
    }

    private static void requireCurrentsWithin100(List<Security> securities) {
        BigDecimal sum = securities.stream().map(Security::currentPct)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(Rebalancing.HUNDRED) > 0) {
            throw new IllegalArgumentException("current percentages must not exceed 100 but summed to "
                    + sum.toPlainString());
        }
    }
}
