package com.crd.rebalance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RB-020 to RB-028. Boundary value analysis on each input dimension: the edges of the variance
 * range, of price, and of account size, where off-by-one and overflow defects live.
 */
@DisplayName("RB-020..028 Boundary values")
class BoundaryValueTest {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final RebalanceEngine engine = new RebalanceEngine();

    @Test
    @DisplayName("RB-020 the smallest variance that can still buy one share buys exactly one")
    void smallestTradeableVariance() {
        // A thousandth of a point on $100,000 is a $1 gap, and these shares cost $1.
        Account account = Account.fullyVested("EPSILON", "100000", List.of(
                Security.of("AAA", "50.001", "50", "1"),
                Security.of("BBB", "49.999", "50", "1")));

        RebalanceResult result = engine.rebalance(account);

        assertThat(result.signedQuantity("AAA")).isEqualTo(1L);
        assertThat(result.signedQuantity("BBB")).isEqualTo(-1L);
    }

    @Test
    @DisplayName("RB-021/022 a security held at 0% is bought up to a 100% target")
    void fullAllocationFromNothing() {
        Account account = new Account("NEW", new BigDecimal("10000"), HUNDRED, new BigDecimal("10000"),
                List.of(Security.of("AAA", "100", "0", "50")));

        RebalanceResult result = engine.rebalance(account);

        assertThat(result.signedQuantity("AAA")).isEqualTo(200L);
        assertThat(result.endingCash()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.isFundable()).isTrue();
    }

    @Test
    @DisplayName("RB-023 a security with a 0% target is fully liquidated")
    void fullExitToZeroTarget() {
        Account account = Account.fullyVested("EXIT", "10000", List.of(
                Security.of("OUT", "0", "100", "50"),
                Security.of("IN", "100", "0", "25")));

        RebalanceResult result = engine.rebalance(account);

        assertThat(result.signedQuantity("OUT")).isEqualTo(-200L);
        assertThat(result.signedQuantity("IN")).isEqualTo(400L);
        assertThat(result.postTradeValues().get("OUT")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.cashImpact()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("RB-024 a one-cent share price produces a very large but exact quantity")
    void pennyPriceDoesNotLosePrecision() {
        Account account = new Account("PENNY", new BigDecimal("10000"), HUNDRED, new BigDecimal("5000"),
                List.of(Security.of("PNY", "100", "50", "0.01")));

        RebalanceResult result = engine.rebalance(account);

        assertThat(result.signedQuantity("PNY")).isEqualTo(500_000L);
        assertThat(result.orderFor("PNY")).hasValueSatisfying(
                order -> assertThat(order.notional()).isEqualByComparingTo("5000"));
    }

    @Test
    @DisplayName("RB-026 an account with no assets produces no orders instead of dividing by zero")
    void zeroAssetAccountIsInert() {
        Account account = Account.fullyVested("EMPTY", "0", List.of(
                Security.of("AAA", "50", "50", "10"),
                Security.of("BBB", "50", "50", "20")));

        RebalanceResult result = engine.rebalance(account);

        assertThat(result.orders()).isEmpty();
        assertThat(result.endingCash()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("RB-027 a trillion dollar account stays exact rather than overflowing")
    void veryLargeAccountRemainsExact() {
        Account account = Account.fullyVested("BIG", "1000000000000", List.of(
                Security.of("IBM", "20", "10", "150"),
                Security.of("MSFT", "20", "20", "90"),
                Security.of("ORCL", "20", "30", "220"),
                Security.of("AAPL", "20", "20", "450"),
                Security.of("HD", "20", "20", "70")));

        RebalanceResult result = engine.rebalance(account);

        assertThat(result.signedQuantity("IBM")).isEqualTo(666_666_666L);
        assertThat(result.signedQuantity("ORCL")).isEqualTo(-454_545_454L);
    }

    @Test
    @DisplayName("RB-028 a single fully allocated security is already on target")
    void singleSecurityOnTarget() {
        Account account = Account.fullyVested("SOLO", "10000",
                List.of(Security.of("AAA", "100", "100", "10")));

        RebalanceResult result = engine.rebalance(account);

        assertThat(result.orders()).isEmpty();
        assertThat(result.maxAbsoluteVariancePct()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("RB-025 an account holding nothing at all is handled without error")
    void emptySecurityListIsHandled() {
        Account account = new Account("CASHONLY", new BigDecimal("10000"), HUNDRED,
                new BigDecimal("10000"), List.of());

        RebalanceResult result = engine.rebalance(account);

        assertThat(result.orders()).isEmpty();
        assertThat(result.postTradeValues()).isEmpty();
        assertThat(result.endingCash()).isEqualByComparingTo("10000");
    }
}
