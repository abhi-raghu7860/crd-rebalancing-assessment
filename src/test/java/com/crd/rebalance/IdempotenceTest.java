package com.crd.rebalance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RB-070 to RB-073. Metamorphic tests: rather than asserting one expected answer, each of these
 * changes the input in a way whose effect on the output is known in advance, and checks that the
 * engine agrees. They catch whole classes of defect that a fixed expected value cannot.
 */
@DisplayName("RB-070..073 Metamorphic relations")
class IdempotenceTest {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final RebalanceEngine engine = new RebalanceEngine();

    @Test
    @DisplayName("RB-070 rebalancing an already rebalanced account does nothing")
    void rebalancingIsIdempotent() {
        Account account = Fixtures.accountAbc();
        RebalanceResult first = engine.rebalance(account);

        RebalanceResult second = engine.rebalance(applyTo(account, first));

        // IBM and ORCL are each 0.10 points out, worth $100, which will not buy a $150 IBM share
        // or a $220 ORCL share. The engine correctly leaves them alone rather than churning.
        assertThat(second.orders())
                .as("a second pass must not generate churn")
                .isEmpty();
    }

    @Test
    @DisplayName("RB-071 no security ends further from its target than it started")
    void varianceNeverGetsWorse() {
        Account account = Fixtures.accountAbc();
        RebalanceResult result = engine.rebalance(account);

        for (Security security : account.securities()) {
            BigDecimal before = security.variancePct().abs();
            BigDecimal after = result.postTradeVariancePct().get(security.symbol()).abs();

            assertThat(after)
                    .as("%s moved from %s to %s points of variance", security.symbol(), before, after)
                    .isLessThanOrEqualTo(before);
        }
    }

    @Test
    @DisplayName("RB-072 scaling the account by ten scales every quantity by ten")
    void quantitiesScaleWithAccountSize() {
        RebalanceResult small = engine.rebalance(Fixtures.evenlyDivisibleAccount());
        RebalanceResult large = engine.rebalance(Account.fullyVested("EVEN10", "1000000",
                Fixtures.evenlyDivisibleAccount().securities()));

        assertThat(large.signedQuantity("AAA")).isEqualTo(small.signedQuantity("AAA") * 10);
        assertThat(large.signedQuantity("BBB")).isEqualTo(small.signedQuantity("BBB") * 10);
    }

    @Test
    @DisplayName("RB-073 the order the securities arrive in does not change the answer")
    void inputOrderDoesNotAffectQuantities() {
        Account account = Fixtures.accountAbc();
        List<Security> reversed = new ArrayList<>(account.securities());
        java.util.Collections.reverse(reversed);

        RebalanceResult original = engine.rebalance(account);
        RebalanceResult shuffled = engine.rebalance(Fixtures.accountAbcWith(reversed));

        for (Security security : account.securities()) {
            assertThat(shuffled.signedQuantity(security.symbol()))
                    .as("quantity for %s", security.symbol())
                    .isEqualTo(original.signedQuantity(security.symbol()));
        }
        assertThat(shuffled.cashImpact()).isEqualByComparingTo(original.cashImpact());
    }

    /** Rebuilds an account as it would stand once every order in {@code result} has filled. */
    private static Account applyTo(Account account, RebalanceResult result) {
        List<Security> updated = account.securities().stream()
                .map(security -> new Security(
                        security.symbol(),
                        security.targetPct(),
                        result.postTradeValues().get(security.symbol())
                                .multiply(HUNDRED)
                                .divide(account.totalAssets(), 10, RoundingMode.HALF_UP),
                        security.unitPrice()))
                .toList();

        return new Account(account.id(), account.totalAssets(), account.vestedPct(),
                result.endingCash(), updated);
    }
}
