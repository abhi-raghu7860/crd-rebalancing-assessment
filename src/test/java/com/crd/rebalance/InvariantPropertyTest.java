package com.crd.rebalance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RB-090 to RB-096. Property-based testing. Instead of asserting one expected answer, each seed
 * generates hundreds of randomly shaped but structurally valid accounts and checks the rules that
 * must hold for every one of them. This is where a defect that only shows up on an unusual price
 * or an eight-line portfolio gets caught.
 *
 * <p>Seeds are fixed, so a failure is always reproducible: the reported seed replays the exact
 * account that broke. Generation uses only {@link Random} from the JDK, so the suite carries no
 * property-testing dependency.
 */
@DisplayName("RB-090..096 Invariants over generated accounts")
class InvariantPropertyTest {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int ACCOUNTS_PER_SEED = 200;

    private final RebalanceEngine engine = new RebalanceEngine();

    @ParameterizedTest(name = "seed {0}")
    @ValueSource(longs = {1L, 7L, 42L, 99L, 2024L, 31337L, 8675309L})
    @DisplayName("every generated account satisfies all seven rebalancing invariants")
    void invariantsHoldForEveryGeneratedAccount(long seed) {
        Random random = new Random(seed);

        for (int i = 0; i < ACCOUNTS_PER_SEED; i++) {
            Account account = randomAccount(random, i);
            RebalanceResult result = engine.rebalance(account);
            String context = "seed " + seed + " account " + i + " " + describe(account);

            assertZeroVarianceNeverTrades(account, result, context);
            assertDirectionOpposesVariance(account, result, context);
            assertVarianceNeverWorsens(account, result, context);
            assertResidualIsUnderOneShare(account, result, context);
            assertValueIsConserved(account, result, context);
            assertNoPositionGoesNegative(result, context);
            assertDeterministic(account, result, context);
        }
    }

    /** RB-090 a line already on target is never traded, whatever else is happening around it. */
    private void assertZeroVarianceNeverTrades(Account account, RebalanceResult result, String context) {
        for (Security security : account.securities()) {
            if (security.variancePct().signum() == 0) {
                assertThat(result.orderFor(security.symbol()))
                        .as("%s: %s is on target and must not trade", context, security.symbol())
                        .isEmpty();
            }
        }
    }

    /** RB-091 an underweight line can only ever buy, and an overweight line can only ever sell. */
    private void assertDirectionOpposesVariance(Account account, RebalanceResult result, String context) {
        for (Security security : account.securities()) {
            long quantity = result.signedQuantity(security.symbol());
            if (quantity == 0) {
                continue;
            }
            int expected = -security.variancePct().signum();
            assertThat(Long.signum(quantity))
                    .as("%s: %s has variance %s so its order must be %s",
                            context, security.symbol(), security.variancePct(), expected > 0 ? "a buy" : "a sell")
                    .isEqualTo(expected);
        }
    }

    /** RB-092 truncation moves a position toward its target and never past it. */
    private void assertVarianceNeverWorsens(Account account, RebalanceResult result, String context) {
        for (Security security : account.securities()) {
            BigDecimal before = security.variancePct().abs();
            BigDecimal after = result.postTradeVariancePct().get(security.symbol()).abs();
            assertThat(after)
                    .as("%s: %s went from %s to %s points out", context, security.symbol(), before, after)
                    .isLessThanOrEqualTo(before);
        }
    }

    /** RB-093 whatever is left untraded is worth less than a single share of that security. */
    private void assertResidualIsUnderOneShare(Account account, RebalanceResult result, String context) {
        for (Security security : account.securities()) {
            BigDecimal targetValue = security.targetPct()
                    .multiply(account.totalAssets())
                    .divide(HUNDRED, 10, RoundingMode.HALF_UP);
            BigDecimal residual = result.postTradeValues().get(security.symbol()).subtract(targetValue).abs();

            assertThat(residual)
                    .as("%s: %s left %s untraded at a unit price of %s",
                            context, security.symbol(), residual, security.unitPrice())
                    .isLessThan(security.unitPrice());
        }
    }

    /** RB-094 rebalancing rearranges value; it never creates or destroys any. */
    private void assertValueIsConserved(Account account, RebalanceResult result, String context) {
        BigDecimal holdings = result.postTradeValues().values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(holdings.add(result.endingCash()))
                .as("%s: holdings plus cash must still equal total assets", context)
                .isEqualByComparingTo(account.totalAssets());
    }

    /** RB-095 the engine never sells more of a security than the account holds. */
    private void assertNoPositionGoesNegative(RebalanceResult result, String context) {
        result.postTradeValues().forEach((symbol, value) ->
                assertThat(value)
                        .as("%s: %s must not end short", context, symbol)
                        .isGreaterThanOrEqualTo(BigDecimal.ZERO));
    }

    /** RB-096 the same account rebalanced twice gives byte-identical orders. */
    private void assertDeterministic(Account account, RebalanceResult result, String context) {
        assertThat(engine.rebalance(account).orders())
                .as("%s: repeated runs must agree", context)
                .isEqualTo(result.orders());
    }

    // ---------------------------------------------------------------- generation

    /**
     * Builds a structurally valid account: two to eight securities, target percentages summing to
     * exactly 100, current percentages summing to at most 100 with the shortfall held as cash,
     * and prices anywhere from a penny to $500.
     */
    private static Account randomAccount(Random random, int index) {
        int lines = 2 + random.nextInt(7);
        int[] targets = composition(random, lines, 100);
        int currentTotal = random.nextInt(101);
        int[] currents = composition(random, lines, currentTotal);

        BigDecimal totalAssets = BigDecimal.valueOf((1 + random.nextInt(1000)) * 1000L);

        List<Security> securities = new ArrayList<>(lines);
        for (int i = 0; i < lines; i++) {
            BigDecimal price = BigDecimal.valueOf(1 + random.nextInt(50_000), 2);
            securities.add(new Security("S" + i,
                    BigDecimal.valueOf(targets[i]), BigDecimal.valueOf(currents[i]), price));
        }

        BigDecimal cash = totalAssets
                .multiply(BigDecimal.valueOf(100L - currentTotal))
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);

        return new Account("GEN-" + index, totalAssets, HUNDRED, cash, securities);
    }

    /** Splits {@code total} into {@code parts} non-negative integers that sum to exactly it. */
    private static int[] composition(Random random, int parts, int total) {
        if (parts == 1) {
            return new int[]{total};
        }
        int[] cuts = new int[parts - 1];
        for (int i = 0; i < cuts.length; i++) {
            cuts[i] = random.nextInt(total + 1);
        }
        Arrays.sort(cuts);

        int[] result = new int[parts];
        result[0] = cuts[0];
        for (int i = 1; i < parts - 1; i++) {
            result[i] = cuts[i] - cuts[i - 1];
        }
        result[parts - 1] = total - cuts[parts - 2];
        return result;
    }

    private static String describe(Account account) {
        StringBuilder sb = new StringBuilder("[assets=").append(account.totalAssets().toPlainString());
        for (Security s : account.securities()) {
            sb.append(' ').append(s.symbol())
                    .append(" t=").append(s.targetPct().toPlainString())
                    .append(" c=").append(s.currentPct().toPlainString())
                    .append(" p=").append(s.unitPrice().toPlainString());
        }
        return sb.append(']').toString();
    }
}
