package com.crd.rebalance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RB-080 to RB-082. A five line account proves the arithmetic; a real book does not look like
 * that. These cover the two non-functional properties that matter for an engine that will be
 * called across thousands of accounts at once.
 */
@DisplayName("RB-080..082 Non-functional")
class NonFunctionalTest {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final RebalanceEngine engine = new RebalanceEngine();

    @Test
    @DisplayName("RB-080 a five thousand line account rebalances well inside two seconds")
    void largeAccountCompletesQuickly() {
        Account account = wideAccount(5_000);

        Instant start = Instant.now();
        RebalanceResult result = engine.rebalance(account);
        Duration elapsed = Duration.between(start, Instant.now());

        assertThat(result.orders())
                .as("every line is off target by a hundredth of a point, so every line trades")
                .hasSize(5_000);
        assertThat(elapsed)
                .as("rebalance of 5,000 lines took %s", elapsed)
                .isLessThan(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("RB-081 concurrent rebalances agree with the single threaded answer")
    void engineIsSafeToShareAcrossThreads() {
        RebalanceResult expected = engine.rebalance(Fixtures.accountAbc());

        List<Callable<RebalanceResult>> jobs = IntStream.range(0, 64)
                .<Callable<RebalanceResult>>mapToObj(i -> () -> engine.rebalance(Fixtures.accountAbc()))
                .toList();

        List<RebalanceResult> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (Future<RebalanceResult> future : pool.invokeAll(jobs)) {
                results.add(future.get());
            }
        } catch (Exception e) {
            throw new AssertionError("concurrent rebalance failed", e);
        }

        assertThat(results)
                .hasSize(64)
                .allSatisfy(result -> assertThat(result.orders()).isEqualTo(expected.orders()));
    }

    @Test
    @DisplayName("RB-082 a wide account still conserves value exactly")
    void largeAccountConservesValue() {
        Account account = wideAccount(5_000);
        RebalanceResult result = engine.rebalance(account);

        BigDecimal holdings = result.postTradeValues().values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(holdings.add(result.endingCash())).isEqualByComparingTo(account.totalAssets());
    }

    /**
     * {@code lines} securities on a $100m account, each targeting an equal slice. Half sit a
     * hundredth of a point light and half a hundredth heavy, so targets sum to exactly 100,
     * currents sum to exactly 100, and every single line needs an order.
     */
    private static Account wideAccount(int lines) {
        BigDecimal target = HUNDRED.divide(BigDecimal.valueOf(lines), 10, java.math.RoundingMode.HALF_UP);
        BigDecimal drift = new BigDecimal("0.01");

        List<Security> securities = new ArrayList<>(lines);
        for (int i = 0; i < lines; i++) {
            BigDecimal current = i % 2 == 0 ? target.subtract(drift) : target.add(drift);
            securities.add(new Security("SEC" + i, target, current, new BigDecimal("100")));
        }

        return Account.fullyVested("WIDE", "100000000", securities);
    }
}
