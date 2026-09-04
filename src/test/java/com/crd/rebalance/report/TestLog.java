package com.crd.rebalance.report;

/**
 * Console output helper for tests.
 *
 * <p>{@link #check(String, Object, Object)} prints an expected/actual line so the run narrates what
 * was verified, not just that something passed. Indentation is kept in step with
 * {@link ConsoleReportListener} so the detail lines sit under the test that produced them.
 */
public final class TestLog {

    private static final String UNIT = "  ";
    private static int indentLevel = 2;

    private TestLog() {
    }

    /** Logs one verified value as expected vs actual. */
    public static void check(String what, Object expected, Object actual) {
        line(String.format("%-38s expected %-14s actual %s", what, plain(expected), plain(actual)));
    }

    /** Trims BigDecimal scale noise so 0.1000000000 reads as 0.1. */
    private static String plain(Object value) {
        if (value instanceof java.math.BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value);
    }

    /** Logs a free form detail line. */
    public static void info(String format, Object... args) {
        line(args.length == 0 ? format : String.format(format, args));
    }

    private static void line(String text) {
        System.out.println(indent(indentLevel) + "- " + text);
    }

    static void setIndent(int level) {
        indentLevel = level;
    }

    static String indent(int level) {
        return UNIT.repeat(Math.max(level, 0));
    }

    static void banner(String title) {
        String rule = "=".repeat(Math.max(title.length(), 60));
        System.out.println();
        System.out.println(rule);
        System.out.println(title);
        System.out.println(rule);
    }
}
