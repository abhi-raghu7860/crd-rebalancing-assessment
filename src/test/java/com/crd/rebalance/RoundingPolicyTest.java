package com.crd.rebalance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RB-010 to RB-016. The specification never states how a fractional share count becomes a whole
 * one, and the three defensible answers disagree on this very data set. These tests pin what each
 * policy actually does so the choice is a decision on record rather than an accident of
 * implementation (ambiguity AMB-01).
 */
@DisplayName("RB-010..016 Rounding policy and residual")
class RoundingPolicyTest {

    private final RebalanceEngine engine = new RebalanceEngine();

    @Test
    @DisplayName("RB-010 when the gap divides exactly, every policy agrees and nothing is left over")
    void exactDivisionIsPolicyIndependent() {
        for (RoundingPolicy policy : RoundingPolicy.values()) {
            RebalanceResult result = engine.rebalance(Fixtures.evenlyDivisibleAccount(),
                    RebalanceConfig.defaults().withRoundingPolicy(policy));

            assertThat(result.signedQuantity("AAA")).as("AAA under %s", policy).isEqualTo(100L);
            assertThat(result.signedQuantity("BBB")).as("BBB under %s", policy).isEqualTo(-50L);
            assertThat(result.maxAbsoluteVariancePct()).as("residual under %s", policy)
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Test
    @DisplayName("RB-011/012 TRUNCATE rounds both sides toward zero and stays cash neutral here")
    void truncateRoundsTowardZero() {
        RebalanceResult result = engine.rebalance(Fixtures.accountAbc(),
                RebalanceConfig.defaults().withRoundingPolicy(RoundingPolicy.TRUNCATE));

        assertThat(result.signedQuantity("IBM")).isEqualTo(66L);    // from 66.6667
        assertThat(result.signedQuantity("ORCL")).isEqualTo(-45L);  // from 45.4545
        assertThat(result.cashImpact()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.isFundable()).isTrue();
    }

    @Test
    @DisplayName("RB-014 HALF_UP buys 67 IBM and overdraws the account by $150")
    void halfUpOverdrawsAFullyInvestedAccount() {
        RebalanceResult result = engine.rebalance(Fixtures.accountAbc(),
                RebalanceConfig.defaults().withRoundingPolicy(RoundingPolicy.HALF_UP));

        // 66.6667 rounds up to 67 but 45.4545 rounds down to 45, so the buy costs $10,050
        // against $9,900 raised. Account ABC is 100% invested and holds no cash to cover it.
        assertThat(result.signedQuantity("IBM")).isEqualTo(67L);
        assertThat(result.signedQuantity("ORCL")).isEqualTo(-45L);
        assertThat(result.cashImpact()).isEqualByComparingTo("-150");
        assertThat(result.isFundable())
                .as("a fully invested account cannot absorb a $150 shortfall")
                .isFalse();
    }

    @Test
    @DisplayName("RB-015 ROUND_UP funds itself but overshoots ORCL past its target")
    void roundUpOvershootsTheTarget() {
        RebalanceResult result = engine.rebalance(Fixtures.accountAbc(),
                RebalanceConfig.defaults().withRoundingPolicy(RoundingPolicy.ROUND_UP));

        assertThat(result.signedQuantity("IBM")).isEqualTo(67L);
        assertThat(result.signedQuantity("ORCL")).isEqualTo(-46L);
        assertThat(result.cashImpact()).isEqualByComparingTo("70");

        // ORCL started 0.10 points overweight and ends 0.12 points underweight: the position has
        // crossed its target rather than approached it.
        assertThat(result.postTradeVariancePct().get("ORCL")).isEqualByComparingTo("-0.12");
        assertThat(result.postTradeVariancePct().get("IBM")).isEqualByComparingTo("0.05");
    }

    @ParameterizedTest
    @EnumSource(RoundingPolicy.class)
    @DisplayName("RB-013 whatever the policy, the unfilled remainder is smaller than one share")
    void residualIsAlwaysSmallerThanOneShare(RoundingPolicy policy) {
        Account account = Fixtures.accountAbc();
        RebalanceResult result = engine.rebalance(account, RebalanceConfig.defaults().withRoundingPolicy(policy));

        for (Security security : account.securities()) {
            BigDecimal targetValue = security.targetPct().multiply(Fixtures.TOTAL_ASSETS)
                    .divide(new BigDecimal("100"), 10, java.math.RoundingMode.HALF_UP);
            BigDecimal residual = result.postTradeValues().get(security.symbol())
                    .subtract(targetValue).abs();

            assertThat(residual)
                    .as("%s residual under %s must be less than one share at %s",
                            security.symbol(), policy, security.unitPrice())
                    .isLessThan(security.unitPrice());
        }
    }

    @Test
    @DisplayName("RB-016 a gap worth less than one share produces no order at all")
    void gapSmallerThanOneShareIsNotTradeable() {
        // AAA is 0.1 points light, a $100 gap, but a single share costs $450.
        // BBB is 0.1 points heavy, a $100 gap, and its shares cost $10 so it does trade.
        Account account = Account.fullyVested("TINY", "100000", List.of(
                Security.of("AAA", "20.1", "20", "450"),
                Security.of("BBB", "79.9", "80", "10")));

        RebalanceResult result = engine.rebalance(account);

        assertThat(result.orderFor("AAA")).isEmpty();
        assertThat(result.signedQuantity("BBB")).isEqualTo(-10L);
    }
}
