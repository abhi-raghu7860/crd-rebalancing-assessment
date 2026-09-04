package com.crd.rebalance;

import com.crd.rebalance.report.TestLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RB-051, RB-054 and RB-055. Rules the assessment implies but does not spell out: what "100% is
 * vested" would mean if it were not 100%, and the drift band that has to stand in for the
 * unreachable goal of zero variance.
 */
@DisplayName("RB-051..055 Business rules")
class BusinessRuleTest {

    private final RebalanceEngine engine = new RebalanceEngine();

    @Test
    @DisplayName("RB-051 partial vesting shrinks every target and puts on-target lines into play")
    void partialVestingRetargetsEveryLine() {
        // At 80% vested only $80,000 can be allocated, so each 20% target is worth 16% of the
        // account. MSFT, AAPL and HD were exactly on target at full vesting and are now 4 points
        // heavy, which is precisely the kind of change a regression suite has to catch.
        Account account = new Account("ABC", new BigDecimal("100000"), new BigDecimal("80"),
                BigDecimal.ZERO, Fixtures.accountAbc().securities());

        RebalanceResult result = engine.rebalance(account);

        assertThat(account.investableBase()).isEqualByComparingTo("80000");
        assertThat(result.signedQuantity("IBM")).isEqualTo(40L);    // $6,000 at $150
        assertThat(result.signedQuantity("MSFT")).isEqualTo(-44L);  // $4,000 at $90
        assertThat(result.signedQuantity("ORCL")).isEqualTo(-63L);  // $14,000 at $220
        assertThat(result.signedQuantity("AAPL")).isEqualTo(-8L);   // $4,000 at $450
        assertThat(result.signedQuantity("HD")).isEqualTo(-57L);    // $4,000 at $70
    }

    @Test
    @DisplayName("RB-054 variance inside the tolerance band is left alone")
    void toleranceBandSuppressesDrift() {
        RebalanceResult inside = engine.rebalance(Fixtures.accountAbc(),
                RebalanceConfig.defaults().withToleranceBandPct("10"));
        RebalanceResult outside = engine.rebalance(Fixtures.accountAbc(),
                RebalanceConfig.defaults().withToleranceBandPct("9.99"));

        assertThat(inside.orders())
                .as("a 10 point drift sits exactly on a 10 point band and is not breached")
                .isEmpty();
        assertThat(outside.orders()).hasSize(2);
    }

    @Test
    @DisplayName("RB-055 zero variance is unreachable, so the band is the real acceptance criterion")
    void exactZeroVarianceIsUnreachable() {
        RebalanceResult result = engine.rebalance(Fixtures.accountAbc());

        TestLog.info("FINDING: zero variance is unreachable with whole shares (AMB-02)");
        TestLog.check("max residual variance (pts)", "> 0", result.maxAbsoluteVariancePct());
        TestLog.info("acceptance criterion must be a tolerance band, not equality");

        // Hitting 20.00% exactly would take 66.6667 IBM shares. Whole shares cannot express that,
        // so "zero target variance" has to be read as "inside tolerance", not as equality.
        assertThat(result.maxAbsoluteVariancePct())
                .as("some residual variance always remains")
                .isGreaterThan(BigDecimal.ZERO);
        assertThat(result.maxAbsoluteVariancePct())
                .as("but it is comfortably inside any realistic drift band")
                .isLessThanOrEqualTo(new BigDecimal("0.5"));
    }
}
