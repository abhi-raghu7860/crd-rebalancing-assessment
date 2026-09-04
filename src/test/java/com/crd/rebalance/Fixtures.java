package com.crd.rebalance;

import java.math.BigDecimal;
import java.util.List;

/** Test data shared across the suite, centred on the account given in the assessment. */
final class Fixtures {

    static final BigDecimal TOTAL_ASSETS = new BigDecimal("100000");

    private Fixtures() {
    }

    /**
     * Account ABC exactly as printed in the assessment: $100,000, 100% vested, no starting cash.
     * IBM is 10 points underweight and ORCL 10 points overweight; the other three are on target.
     */
    static Account accountAbc() {
        return Account.fullyVested("ABC", "100000", List.of(
                Security.of("IBM", "20", "10", "150"),
                Security.of("MSFT", "20", "20", "90"),
                Security.of("ORCL", "20", "30", "220"),
                Security.of("AAPL", "20", "20", "450"),
                Security.of("HD", "20", "20", "70")));
    }

    /** Account ABC with one field replaced, for boundary and negative cases. */
    static Account accountAbcWith(List<Security> securities) {
        return Account.fullyVested("ABC", "100000", securities);
    }

    /** A two-line account whose gaps divide exactly, so rounding plays no part. */
    static Account evenlyDivisibleAccount() {
        return Account.fullyVested("EVEN", "100000", List.of(
                Security.of("AAA", "50", "40", "100"),
                Security.of("BBB", "50", "60", "200")));
    }
}
