package com.crd.rebalance;

import com.crd.rebalance.report.TestLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RB-041, RB-042 and RB-044. Account ABC is 100% invested and holds no cash, so every purchase has
 * to be paid for out of the matching sales. That makes funding and sequencing part of correctness
 * rather than an operational afterthought, and it is where the most damaging defects hide: an
 * order block can be arithmetically perfect and still be unexecutable.
 */
@DisplayName("RB-041..044 Cash, funding and sequencing")
class CashFundingTest {

    private final RebalanceEngine engine = new RebalanceEngine();

    @Test
    @DisplayName("RB-042 the same orders in the wrong sequence overdraw the account")
    void sequencingDecidesWhetherTheBlockCanBeFunded() {
        Account account = Fixtures.accountAbc();

        RebalanceResult sellsFirst = engine.rebalance(account, RebalanceConfig.defaults().withSellsFirst(true));
        RebalanceResult buysFirst = engine.rebalance(account, RebalanceConfig.defaults().withSellsFirst(false));

        // Identical orders and identical net cash. Only the order of execution differs.
        assertThat(buysFirst.signedQuantity("IBM")).isEqualTo(sellsFirst.signedQuantity("IBM"));
        assertThat(buysFirst.signedQuantity("ORCL")).isEqualTo(sellsFirst.signedQuantity("ORCL"));
        assertThat(buysFirst.cashImpact()).isEqualByComparingTo(sellsFirst.cashImpact());

        TestLog.info("FINDING: identical orders, identical net cash, only the sequence differs");
        TestLog.check("sells first: min running cash", "0", sellsFirst.minimumRunningCash());
        TestLog.check("buys first: min running cash", "-9900", buysFirst.minimumRunningCash());
        TestLog.check("fundable sells-first / buys-first", "true / false",
                sellsFirst.isFundable() + " / " + buysFirst.isFundable());

        assertThat(sellsFirst.orders()).first().extracting(Order::side).isEqualTo(Side.SELL);
        assertThat(sellsFirst.isFundable()).isTrue();

        assertThat(buysFirst.orders()).first().extracting(Order::side).isEqualTo(Side.BUY);
        assertThat(buysFirst.minimumRunningCash()).isEqualByComparingTo("-9900");
        assertThat(buysFirst.isFundable())
                .as("buying before the funding sale settles leaves the account $9,900 short")
                .isFalse();
    }

    @Test
    @DisplayName("RB-041 truncation prevents overshoot but does not guarantee the block is fundable")
    void truncationCanStillLeaveAShortfall() {
        // The offsetting $10,000 gaps hide a trap. The buy line divides exactly and loses nothing
        // to truncation; the sell line divides into 3333.33 and loses $1 of proceeds. The block
        // is therefore $1 short even though every line was rounded down.
        Account account = Account.fullyVested("SHORT", "100000", List.of(
                Security.of("AAA", "60", "50", "1"),
                Security.of("BBB", "40", "50", "3")));

        RebalanceResult result = engine.rebalance(account);

        TestLog.info("FINDING: truncation is cash neutral on ABC by coincidence, not by construction");
        TestLog.check("buy leg (divides exactly)", "10000", result.totalBuyNotional());
        TestLog.check("sell leg (3333.33 -> 3333)", "9999", result.totalSellNotional());
        TestLog.check("net cash impact", "-1", result.cashImpact());
        TestLog.check("block fundable", false, result.isFundable());

        assertThat(result.signedQuantity("AAA")).isEqualTo(10_000L);
        assertThat(result.signedQuantity("BBB")).isEqualTo(-3_333L);
        assertThat(result.totalSellNotional()).isEqualByComparingTo("9999");
        assertThat(result.totalBuyNotional()).isEqualByComparingTo("10000");
        assertThat(result.cashImpact()).isEqualByComparingTo("-1");
        assertThat(result.isFundable())
                .as("a $1 shortfall is still a shortfall on a zero cash account")
                .isFalse();
    }

    @ParameterizedTest
    @EnumSource(RoundingPolicy.class)
    @DisplayName("RB-044 trading moves value between lines without creating or destroying any")
    void totalAssetsAreConserved(RoundingPolicy policy) {
        RebalanceResult result = engine.rebalance(Fixtures.accountAbc(),
                RebalanceConfig.defaults().withRoundingPolicy(policy));

        BigDecimal holdings = result.postTradeValues().values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(holdings.add(result.endingCash()))
                .as("holdings plus cash under %s", policy)
                .isEqualByComparingTo(Fixtures.TOTAL_ASSETS);
    }
}
