package com.crd.rebalance.report;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Writes an ExtentReports HTML report for the whole run.
 *
 * <p>Registered by ServiceLoader through
 * {@code META-INF/services/org.junit.platform.launcher.TestExecutionListener}, so no test class
 * needs to know it exists. Output defaults to {@code target/extent-report/index.html} and can be
 * moved with {@code -Dextent.report.path=...}.
 *
 * <p>Offline mode is on, so the report renders with no network access.
 */
public class ExtentReportListener implements TestExecutionListener {

    private static final String DEFAULT_PATH = "target/extent-report/index.html";

    private final Map<String, ExtentTest> nodes = new ConcurrentHashMap<>();
    private final Map<String, Instant> startTimes = new ConcurrentHashMap<>();

    private ExtentReports extent;
    private TestPlan testPlan;
    private File reportFile;
    private int passed;
    private int failed;
    private int skipped;

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        this.testPlan = testPlan;

        reportFile = new File(System.getProperty("extent.report.path", DEFAULT_PATH));
        File parent = reportFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        ExtentSparkReporter spark = new ExtentSparkReporter(reportFile);
        spark.config().setTheme(Theme.STANDARD);
        spark.config().setDocumentTitle("Rebalancing Test Results");
        spark.config().setReportName("Portfolio Rebalancing - QA Assessment");
        spark.config().setTimeStampFormat("yyyy-MM-dd HH:mm:ss");
        spark.config().setOfflineMode(true);

        extent = new ExtentReports();
        extent.attachReporter(spark);
        extent.setSystemInfo("Project", "CRD / Alpha Platform rebalancing assessment");
        extent.setSystemInfo("Java", System.getProperty("java.version"));
        extent.setSystemInfo("JVM", System.getProperty("java.vm.name"));
        extent.setSystemInfo("OS", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        extent.setSystemInfo("Test framework", "JUnit 5 (Jupiter) + AssertJ");
        extent.setSystemInfo("Expected output", "BUY 66 IBM, SELL 45 ORCL");
    }

    @Override
    public void executionStarted(TestIdentifier id) {
        if (id.isTest()) {
            startTimes.put(id.getUniqueId(), Instant.now());
        }
    }

    @Override
    public void executionFinished(TestIdentifier id, TestExecutionResult result) {
        if (extent == null) {
            return;
        }

        if (!id.isTest()) {
            // Containers only matter here when lifecycle setup itself blew up.
            if (result.getStatus() == TestExecutionResult.Status.FAILED) {
                result.getThrowable().ifPresent(t -> nodeFor(id).fail(t));
            }
            return;
        }

        ExtentTest node = nodeFor(id);
        String duration = elapsed(id);

        switch (result.getStatus()) {
            case SUCCESSFUL -> {
                passed++;
                node.pass("Passed in " + duration);
            }
            case FAILED -> {
                failed++;
                node.fail("Failed after " + duration);
                result.getThrowable().ifPresent(node::fail);
            }
            case ABORTED -> {
                skipped++;
                node.skip("Aborted after " + duration);
                result.getThrowable().ifPresent(node::skip);
            }
        }
    }

    @Override
    public void executionSkipped(TestIdentifier id, String reason) {
        if (extent == null) {
            return;
        }
        skipped++;
        nodeFor(id).skip(reason == null || reason.isBlank() ? "Skipped" : reason);
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        if (extent == null) {
            return;
        }
        extent.setSystemInfo("Result", passed + " passed, " + failed + " failed, " + skipped + " skipped");
        extent.flush();
        System.out.println("[extent] report written to " + reportFile.getAbsolutePath());
    }

    /**
     * Mirrors the JUnit tree into Extent: a test class becomes a top level entry and its methods,
     * nested classes and parameterized invocations become child nodes.
     */
    private ExtentTest nodeFor(TestIdentifier id) {
        ExtentTest existing = nodes.get(id.getUniqueId());
        if (existing != null) {
            return existing;
        }

        Optional<TestIdentifier> parent = testPlan.getParent(id);
        ExtentTest node;
        if (parent.isPresent() && testPlan.getParent(parent.get()).isPresent()) {
            node = nodeFor(parent.get()).createNode(id.getDisplayName());
        } else {
            node = extent.createTest(id.getDisplayName());
        }

        nodes.put(id.getUniqueId(), node);
        return node;
    }

    private String elapsed(TestIdentifier id) {
        Instant start = startTimes.remove(id.getUniqueId());
        if (start == null) {
            return "n/a";
        }
        return Duration.between(start, Instant.now()).toMillis() + " ms";
    }
}
