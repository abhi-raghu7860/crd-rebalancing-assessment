package com.crd.rebalance.report;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prints a live tree of suites, tests and results to the console as the run progresses.
 *
 * <p>Surefire on its own only reports per-class totals, which says nothing about what was actually
 * checked. Registered by ServiceLoader alongside {@link ExtentReportListener}.
 *
 * <p>Turn it off with {@code -Dtest.console=off}.
 */
public class ConsoleReportListener implements TestExecutionListener {

    private final Map<String, Instant> startTimes = new ConcurrentHashMap<>();

    private TestPlan testPlan;
    private boolean enabled;
    private int passed;
    private int failed;
    private int skipped;

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        this.testPlan = testPlan;
        this.enabled = !"off".equalsIgnoreCase(System.getProperty("test.console", "on"));
        if (enabled) {
            TestLog.banner("PORTFOLIO REBALANCING - TEST RUN");
        }
    }

    @Override
    public void executionStarted(TestIdentifier id) {
        if (!enabled) {
            return;
        }
        if (id.isTest()) {
            startTimes.put(id.getUniqueId(), Instant.now());
            TestLog.setIndent(depthOf(id) + 1);
            // Name first, so any detail the test logs is clearly nested under it.
            System.out.println(TestLog.indent(depthOf(id)) + "> " + id.getDisplayName());
        } else if (depthOf(id) > 0) {
            System.out.println();
            System.out.println(TestLog.indent(depthOf(id)) + "[ " + id.getDisplayName() + " ]");
        }
    }

    @Override
    public void executionFinished(TestIdentifier id, TestExecutionResult result) {
        if (!enabled || !id.isTest()) {
            return;
        }

        String pad = TestLog.indent(depthOf(id) + 1);
        String took = elapsed(id);

        switch (result.getStatus()) {
            case SUCCESSFUL -> {
                passed++;
                System.out.println(pad + "PASS  (" + took + ")");
            }
            case FAILED -> {
                failed++;
                System.out.println(pad + "FAIL  (" + took + ")  " + id.getDisplayName());
                result.getThrowable().ifPresent(t -> {
                    String message = t.getMessage() == null ? t.toString() : t.getMessage();
                    for (String line : message.strip().split("\\R")) {
                        System.out.println(pad + "      " + line);
                    }
                });
            }
            case ABORTED -> {
                skipped++;
                System.out.println(pad + "SKIP");
            }
        }
    }

    @Override
    public void executionSkipped(TestIdentifier id, String reason) {
        if (enabled && id.isTest()) {
            skipped++;
            System.out.println(TestLog.indent(depthOf(id)) + "SKIP  " + id.getDisplayName()
                    + (reason == null || reason.isBlank() ? "" : " - " + reason));
        }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        if (!enabled) {
            return;
        }
        TestLog.banner(String.format("RESULT: %d passed, %d failed, %d skipped  (%d total)",
                passed, failed, skipped, passed + failed + skipped));
    }

    /** 0 for the engine, 1 for a top level class, 2 for its methods, and so on. */
    private int depthOf(TestIdentifier id) {
        int depth = 0;
        Optional<TestIdentifier> current = testPlan.getParent(id);
        while (current.isPresent()) {
            depth++;
            current = testPlan.getParent(current.get());
        }
        return depth;
    }

    private String elapsed(TestIdentifier id) {
        Instant start = startTimes.remove(id.getUniqueId());
        return start == null ? "n/a" : Duration.between(start, Instant.now()).toMillis() + " ms";
    }
}
