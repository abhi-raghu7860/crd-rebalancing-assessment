package com.crd.rebalance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RB-001 to RB-008. The assessment scenario itself: the P0 smoke suite, and the answer to the
 * yellow output column.
 *
 * <p>Expected output, derived independently of the engine:
 * IBM is 10 points light on a $100,000 account, a $10,000 shortfall at $150 a share, so
 * 66.67 shares truncate to <b>BUY 66</b>. ORCL is 10 points heavy, a $10,000 excess at $220,
 * so 45.45 shares truncate to <b>SELL 45</b>. MSFT, AAPL and HD are on target and are not traded.
 * Both orders are worth $9,900, so the block funds itself and needs no cash.
 */
@DisplayName("RB-001..008 Golden scenario - Account ABC from the assessment")
class GoldenScenarioTest {

    private final RebalanceEngine engine = new RebalanceEngine();

    @Nested
    @DisplayName("RB-001 the output column")
    class OutputColumn {

        @Test
        @DisplayName("buys 66 IBM and sells 45 ORCL, trading nothing else")
        void producesTheExpectedOrders() {
            RebalanceResult result = engine.rebalance(Fixtures.accountAbc());

            assertThat(result.signedQuantity("IBM")).isEqualTo(66L);
            assertThat(result.signedQuantity("ORCL")).isEqualTo(-45L);
            assertThat(result.signedQuantity("MSFT")).isZero();
            assertThat(result.signedQuantity("AAPL")).isZero();
            assertThat(result.signedQuantity("HD")).isZero();
        }

        @Test
        @DisplayName("emits exactly two orders, one per off-target security")
        void emitsOnlyTheOrdersThatAreNeeded() {
            RebalanceResult result = engine.rebalance(Fixtures.accountAbc());

            assertThat(result.orders()).hasSize(2);
            assertThat(result.orders()).extracting(Order::symbol).containsExactlyInAnyOrder("IBM", "ORCL");
        }
    }

    @Nested
    @DisplayName("RB-003..005 direction follows the sign of the variance")
    class Direction {

        @Test
        @DisplayName("RB-003 a negative variance buys")
        void negativeVarianceBuys() {
            RebalanceResult result = engine.rebalance(Fixtures.accountAbc());

            assertThat(result.orderFor("IBM")).hasValueSatisfying(
                    order -> assertThat(order.side()).isEqualTo(Side.BUY));
        }

        @Test
        @DisplayName("RB-004 a positive variance sells")
        void positiveVarianceSells() {
            RebalanceResult result = engine.rebalance(Fixtures.accountAbc());

            assertThat(result.orderFor("ORCL")).hasValueSatisfying(
                    order -> assertThat(order.side()).isEqualTo(Side.SELL));
        }

        @Test
        @DisplayName("RB-005 a zero variance trades nothing")
        void zeroVarianceDoesNotTrade() {
            RebalanceResult result = engine.rebalance(Fixtures.accountAbc());

            assertThat(result.orderFor("MSFT")).isEmpty();
            assertThat(result.orderFor("AAPL")).isEmpty();
            assertThat(result.orderFor("HD")).isEmpty();
        }
    }

    @Nested
    @DisplayName("RB-006..008 sizing")
    class Sizing {

        @Test
        @DisplayName("RB-006 variance is percentage points of total assets, not a percentage of the position")
        void sizesFromPercentagePointsOfTotalAssets() {
            // The 10 point IBM gap is $10,000 of a $100,000 account. Reading it as 10% of the
            // $10,000 position would size a $1,000 order and buy 6 shares instead of 66.
            RebalanceResult result = engine.rebalance(Fixtures.accountAbc());

            assertThat(result.orderFor("IBM")).hasValueSatisfying(order ->
                    assertThat(order.notional()).isEqualByComparingTo("9900"));
        }

        @Test
        @DisplayName("RB-007 fractional share counts truncate rather than round up")
        void truncatesFractionalShares() {
            // IBM 10000/150 = 66.6667 and ORCL 10000/220 = 45.4545.
            RebalanceResult result = engine.rebalance(Fixtures.accountAbc());

            assertThat(result.signedQuantity("IBM")).isEqualTo(66L);
            assertThat(result.signedQuantity("ORCL")).isEqualTo(-45L);
        }

        @Test
        @DisplayName("RB-008 both orders are worth $9,900 so the block is cash neutral")
        void blockIsCashNeutral() {
            RebalanceResult result = engine.rebalance(Fixtures.accountAbc());

            assertThat(result.totalBuyNotional()).isEqualByComparingTo("9900");
            assertThat(result.totalSellNotional()).isEqualByComparingTo("9900");
            assertThat(result.cashImpact()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.endingCash()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("RB-002 post-trade state")
    class PostTradeState {

        @Test
        @DisplayName("residual variance is 0.10 points, the closest whole shares can get")
        void residualVarianceIsWithinATenthOfAPoint() {
            RebalanceResult result = engine.rebalance(Fixtures.accountAbc());

            // IBM lands at 19.90% and ORCL at 20.10%; exact 20% needs fractional shares.
            assertThat(result.postTradeVariancePct().get("IBM")).isEqualByComparingTo("-0.10");
            assertThat(result.postTradeVariancePct().get("ORCL")).isEqualByComparingTo("0.10");
            assertThat(result.maxAbsoluteVariancePct()).isEqualByComparingTo("0.10");
        }

        @Test
        @DisplayName("total assets are unchanged; the trade only moves value between lines")
        void conservesTotalAssets() {
            RebalanceResult result = engine.rebalance(Fixtures.accountAbc());

            BigDecimal holdings = result.postTradeValues().values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(holdings.add(result.endingCash())).isEqualByComparingTo("100000");
        }
    }
}
