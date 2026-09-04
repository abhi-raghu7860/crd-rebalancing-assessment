package com.crd.rebalance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RB-030 to RB-039. Bad data must be rejected at the boundary with a message that names the
 * offending field and security, not swallowed into a plausible-looking order or surfaced as a
 * bare NullPointerException. A rebalancer that trades on a stale price feed is worse than one
 * that refuses to run.
 */
@DisplayName("RB-030..039 Input validation")
class InputValidationTest {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final RebalanceEngine engine = new RebalanceEngine();

    @Test
    @DisplayName("RB-030 target percentages that do not sum to 100 are rejected")
    void targetsMustSumToOneHundred() {
        assertThatThrownBy(() -> Account.fullyVested("BAD", "100000", List.of(
                Security.of("AAA", "50", "50", "10"),
                Security.of("BBB", "30", "50", "20"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sum to 100")
                .hasMessageContaining("80");
    }

    @Test
    @DisplayName("RB-031 current percentages summing under 100 are allowed; the rest is cash")
    void currentsMaySumToLessThanOneHundred() {
        Account account = new Account("PARTIAL", new BigDecimal("100000"), HUNDRED,
                new BigDecimal("40000"), List.of(
                Security.of("AAA", "50", "30", "100"),
                Security.of("BBB", "50", "30", "100")));

        RebalanceResult result = engine.rebalance(account);

        // Each line is 20 points light, so $20,000 each, funded from the $40,000 cash balance.
        assertThat(result.signedQuantity("AAA")).isEqualTo(200L);
        assertThat(result.signedQuantity("BBB")).isEqualTo(200L);
        assertThat(result.endingCash()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("RB-032 current percentages summing above 100 are rejected")
    void currentsMayNotExceedOneHundred() {
        assertThatThrownBy(() -> Account.fullyVested("OVER", "100000", List.of(
                Security.of("AAA", "50", "60", "10"),
                Security.of("BBB", "50", "60", "20"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not exceed 100");
    }

    @ParameterizedTest(name = "RB-033 unit price {0} is rejected")
    @CsvSource({"0", "-1", "-150.50"})
    @DisplayName("RB-033 a zero or negative price is rejected rather than dividing by zero")
    void invalidPricesAreRejected(String price) {
        assertThatThrownBy(() -> Security.of("AAA", "100", "50", price))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unitPrice must be strictly positive");
    }

    @Test
    @DisplayName("RB-034 a missing price is treated as a failed feed, not as zero")
    void nullPriceIsRejected() {
        assertThatThrownBy(() -> new Security("AAA", new BigDecimal("100"), new BigDecimal("50"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stale or failed price feed");
    }

    @ParameterizedTest(name = "RB-035/036 {0} of {1} is rejected")
    @CsvSource({"targetPct, -1", "targetPct, 101", "currentPct, -0.01", "currentPct, 100.01"})
    @DisplayName("RB-035/036 percentages outside 0 to 100 are rejected")
    void percentagesOutsideRangeAreRejected(String field, String value) {
        String target = field.equals("targetPct") ? value : "100";
        String current = field.equals("currentPct") ? value : "50";

        assertThatThrownBy(() -> Security.of("AAA", target, current, "10"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(field)
                .hasMessageContaining("between 0 and 100");
    }

    @Test
    @DisplayName("RB-037 the same symbol twice is rejected rather than silently double counted")
    void duplicateSymbolsAreRejected() {
        assertThatThrownBy(() -> Account.fullyVested("DUP", "100000", List.of(
                Security.of("IBM", "50", "50", "150"),
                Security.of("IBM", "50", "50", "150"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate security symbol: IBM");
    }

    @Test
    @DisplayName("RB-038 a blank symbol is rejected")
    void blankSymbolIsRejected() {
        assertThatThrownBy(() -> Security.of("  ", "100", "50", "10"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("symbol must not be blank");
    }

    @Test
    @DisplayName("RB-039 null arguments give a named error, not a NullPointerException")
    void nullArgumentsAreNamed() {
        assertThatThrownBy(() -> engine.rebalance(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("account must not be null");

        assertThatThrownBy(() -> engine.rebalance(Fixtures.accountAbc(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("config must not be null");

        assertThatThrownBy(() -> new Account("X", new BigDecimal("1"), HUNDRED, BigDecimal.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("use an empty list");
    }

    @Test
    @DisplayName("RB-039b negative total assets and negative cash are rejected")
    void negativeAccountValuesAreRejected() {
        assertThatThrownBy(() -> new Account("X", new BigDecimal("-1"), HUNDRED, BigDecimal.ZERO, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalAssets");

        assertThatThrownBy(() -> new Account("X", BigDecimal.ZERO, HUNDRED, new BigDecimal("-1"), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cash");
    }
}
