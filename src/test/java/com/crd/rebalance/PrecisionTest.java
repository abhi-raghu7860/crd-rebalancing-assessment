package com.crd.rebalance;

import com.crd.rebalance.report.TestLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RB-024 and RB-025. Money arithmetic has to be decimal, and it has to round exactly once. Both
 * tests are built from values where a wrong approach gives a visibly different answer, so they fail
 * loudly rather than drifting quietly.
 */
@DisplayName("RB-024..025 Precision")
class PrecisionTest {

    private final RebalanceEngine engine = new RebalanceEngine();

    @Test
    @DisplayName("RB-024 a price of $0.07 exposes binary floating point error")
    void decimalArithmeticBeatsBinaryFloatingPoint() {
        // The gap is $7,000 and the price is $0.07, which is exactly 100,000 shares.
        // In a double, 7000 / 0.07 evaluates to 99999.99999999999, and truncating that to a whole
        // share silently loses a share and $0.07 of exposure. Repeated across a book of accounts
        // this is how a rebalancer quietly drifts.
        Account account = Account.fullyVested("PRECISE", "100000", List.of(
                Security.of("AAA", "50", "43", "0.07"),
                Security.of("BBB", "50", "57", "10")));

        RebalanceResult result = engine.rebalance(account);

        TestLog.info("FINDING: binary floating point loses a share on this input");
        TestLog.check("BigDecimal 7000/0.07", 100_000, result.signedQuantity("AAA"));
        TestLog.check("double     7000/0.07", 99_999, (long) (7000 / 0.07));

        assertThat(result.signedQuantity("AAA")).isEqualTo(100_000L);
        assertThat((long) (7000 / 0.07))
                .as("the double answer this test exists to rule out")
                .isEqualTo(99_999L);
    }

    @Test
    @DisplayName("RB-025 rounding happens once, at the share count, not on the way there")
    void roundingIsAppliedOnlyAtTheShareStep() {
        // The gap is $3,333.33... If the notional were rounded to cents before dividing, the
        // quantity would come out one share short of the exact division.
        Account account = Account.fullyVested("CHAIN", "100000", List.of(
                Security.of("AAA", "40", "36.66667", "1"),
                Security.of("BBB", "60", "63.33333", "1")));

        RebalanceResult result = engine.rebalance(account);

        assertThat(result.signedQuantity("AAA")).isEqualTo(3_333L);
        assertThat(result.signedQuantity("BBB")).isEqualTo(-3_333L);
    }
}
