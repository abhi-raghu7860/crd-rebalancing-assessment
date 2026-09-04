package com.crd.rebalance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RB-100 and RB-101. The acceptance data lives in CSV rather than in Java, so a business analyst
 * can add a scenario without touching code and the same files can be handed to the team that owns
 * the requirement. {@code account-abc.csv} is the assessment's own answer key.
 */
@DisplayName("RB-100..101 Data driven scenarios")
class DataDrivenTest {

    private final RebalanceEngine engine = new RebalanceEngine();

    @ParameterizedTest(name = "RB-100 {0} expects {4}")
    @CsvFileSource(resources = "/test-data/account-abc.csv", numLinesToSkip = 1)
    @DisplayName("RB-100 every line of account ABC produces its expected quantity")
    void accountAbcMatchesTheAnswerKey(String symbol, String targetPct, String currentPct,
                                       String unitPrice, long expectedSignedQuantity) {
        RebalanceResult result = engine.rebalance(Fixtures.accountAbc());

        Security security = Fixtures.accountAbc().securities().stream()
                .filter(s -> s.symbol().equals(symbol))
                .findFirst()
                .orElseThrow(() -> new AssertionError("fixture is missing " + symbol));

        // Guard against the fixture and the answer key drifting apart.
        assertThat(security.targetPct()).isEqualByComparingTo(targetPct);
        assertThat(security.currentPct()).isEqualByComparingTo(currentPct);
        assertThat(security.unitPrice()).isEqualByComparingTo(unitPrice);

        assertThat(result.signedQuantity(symbol)).isEqualTo(expectedSignedQuantity);
    }

    @ParameterizedTest(name = "RB-101 {0}")
    @CsvFileSource(resources = "/test-data/two-line-scenarios.csv", numLinesToSkip = 1)
    @DisplayName("RB-101 two line scenarios")
    void twoLineScenarios(String description, String totalAssets,
                          String targetA, String currentA, String priceA,
                          String targetB, String currentB, String priceB,
                          long expectedA, long expectedB) {
        Account account = Account.fullyVested("CSV", totalAssets, List.of(
                Security.of("A", targetA, currentA, priceA),
                Security.of("B", targetB, currentB, priceB)));

        RebalanceResult result = engine.rebalance(account);

        assertThat(result.signedQuantity("A")).as("%s: security A", description).isEqualTo(expectedA);
        assertThat(result.signedQuantity("B")).as("%s: security B", description).isEqualTo(expectedB);
    }
}
