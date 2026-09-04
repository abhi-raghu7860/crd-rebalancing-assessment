package com.crd.rebalance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RB-030, RB-033 and RB-034. Bad data must be rejected at the boundary with a message that names
 * the offending field and security, not swallowed into a plausible-looking order. A rebalancer that
 * trades on a stale price feed is worse than one that refuses to run.
 */
@DisplayName("RB-030..034 Input validation")
class InputValidationTest {

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
}
