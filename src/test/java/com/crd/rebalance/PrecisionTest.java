package com.crd.rebalance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RB-060 to RB-065. Money arithmetic has to be decimal. These tests are built from values where
 * binary floating point gives a visibly different answer, so they fail loudly if anyone ever
 * swaps a BigDecimal for a double.
 */
@DisplayName("RB-060..065 Precision and determinism")
class PrecisionTest {

    private final RebalanceEngine engine = new RebalanceEngine();

    @Test
    @DisplayName("RB-060 a price of $0.07 exposes binary floating point error")
    void decimalArithmeticBeatsBinaryFloatingPoint() {
        // The gap is $7,000 and the price is $0.07, which is exactly 100,000 shares.
        // In a double, 7000 / 0.07 evaluates to 99999.99999999999, and truncating that to a whole
        // share silently loses a share and $0.07 of exposure. Repeated across a book of accounts
        // this is how a rebalancer quietly drifts.
        Account account = Account.fullyVested("PRECISE", "100000", List.of(
                Security.of("AAA", "50", "43", "0.07"),
                Security.of("BBB", "50", "57", "10")));

        RebalanceResult result = engine.rebalance(account);

        assertThat(result.signedQuantity("AAA")).isEqualTo(100_000L);
        assertThat((long) (7000 / 0.07))
                .as("the double answer this test exists to rule out")
                .isEqualTo(99_999L);
    }

    @Test
    @DisplayName("RB-061 prices carrying cents are handled exactly")
    void pricesWithCentsAreExact() {
        // $10,000 at $150.37 is 66.50 shares, which truncates to 66.
        Account account = Account.fullyVested("CENTS", "100000", List.of(
                Security.of("AAA", "20", "10", "150.37"),
                Security.of("BBB", "80", "90", "100")));

        RebalanceResult result = engine.rebalance(account);

        assertThat(result.signedQuantity("AAA")).isEqualTo(66L);
        assertThat(result.orderFor("AAA")).hasValueSatisfying(
                order -> assertThat(order.notional()).isEqualByComparingTo("9924.42"));
    }

    @Test
    @DisplayName("RB-062 fractional target percentages are honoured, not rounded away")
    void fractionalPercentagesAreHonoured() {
        // A 0.25 point gap on $100,000 is $250, which buys 2 shares at $100 with $50 left over.
        Account account = Account.fullyVested("FRACTION", "100000", List.of(
                Security.of("AAA", "33.25", "33", "100"),
                Security.of("BBB", "66.75", "67", "100")));

        RebalanceResult result = engine.rebalance(account);

        assertThat(result.signedQuantity("AAA")).isEqualTo(2L);
        assertThat(result.signedQuantity("BBB")).isEqualTo(-2L);
    }

    @Test
    @DisplayName("RB-063 rounding happens once, at the share count, not on the way there")
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

    @Test
    @DisplayName("RB-064 the same input always gives the same output")
    void resultsAreDeterministic() {
        RebalanceResult first = engine.rebalance(Fixtures.accountAbc());
        RebalanceResult second = engine.rebalance(Fixtures.accountAbc());

        assertThat(second.orders()).isEqualTo(first.orders());
        assertThat(second.endingCash()).isEqualByComparingTo(first.endingCash());
    }

    @Test
    @DisplayName("RB-065 the engine never mutates the account it is given")
    void inputsAreNotMutated() {
        Account account = Fixtures.accountAbc();
        List<Security> before = List.copyOf(account.securities());

        engine.rebalance(account);

        assertThat(account.securities()).isEqualTo(before);
        assertThat(account.totalAssets()).isEqualByComparingTo(Fixtures.TOTAL_ASSETS);
        assertThat(account.cash()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
