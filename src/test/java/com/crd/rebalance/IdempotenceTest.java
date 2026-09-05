package com.crd.rebalance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RB-026. A metamorphic test: rather than asserting one expected answer, it changes the input in a
 * way whose effect on the output is known in advance and checks that the engine agrees.
 */
@DisplayName("RB-026 Metamorphic relations")
class IdempotenceTest {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final RebalanceEngine engine = new RebalanceEngine();

    @Test
    @DisplayName("RB-026 rebalancing an already rebalanced account does nothing")
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
