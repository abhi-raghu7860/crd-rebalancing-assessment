# Portfolio Rebalancing — QA Technical Assessment

Charles River Development / Alpha Platform Engineering.

---

## The answer

**Account ABC, $100,000 total assets, 100% vested.**

| Security | target% | current% | target variance | unit price | **Shares to buy/sell** |
|---|---|---|---|---|---|
| IBM | 20 | 10 | −10 | $150 | **BUY 66** |
| MSFT | 20 | 20 | 0 | $90 | **—** |
| ORCL | 20 | 30 | +10 | $220 | **SELL 45** |
| AAPL | 20 | 20 | 0 | $450 | **—** |
| HD | 20 | 20 | 0 | $70 | **—** |

**How it is derived.** A negative variance is a shortfall to buy, a positive variance an excess to
sell. IBM is 10 percentage points light on a $100,000 account, so the gap is $10,000, and
$10,000 ÷ $150 = 66.67 shares → **66**. ORCL is 10 points heavy, so $10,000 ÷ $220 = 45.45
shares → **45**.

**Why it works out.** 66 × $150 = $9,900 and 45 × $220 = $9,900. The sale pays for the purchase to
the dollar, which matters because Account ABC is 100% invested and holds no cash.

**What it does not do.** It does not reach zero variance. IBM ends at 19.90% and ORCL at 20.10%.
Exact 20.00% would need 66.6667 and 45.4545 shares, and whole shares cannot express that.

---

## Two things I would raise before writing any code

**1. The rounding rule is unspecified, and the choice is an overdraft risk.** Round half up instead
of truncating and IBM becomes **BUY 67**, costing $10,050 against $9,900 raised — a $150 shortfall on
an account with no cash. Truncation is the recommendation: it is the only policy that neither
overshoots a target nor overdraws the account. All three policies are implemented and pinned by
tests so the decision is on record rather than accidental.

**2. "Zero target variance" is not an achievable acceptance criterion.** With whole shares there is
always a residual — 0.10 points here. The criterion has to be a tolerance band. An acceptance test
written as equality would fail a correct implementation.

Four more ambiguities, the risk analysis, and every assumption are in
[`docs/TEST-STRATEGY.md`](docs/TEST-STRATEGY.md).

---

## Running the tests

Java 21 required. Maven comes from the bundled wrapper, so there is nothing to install.

```powershell
.\mvnw.cmd test                                # full suite
.\mvnw.cmd -Dtest=GoldenScenarioTest test      # just the assessment scenario
```

The `.\` prefix is required in PowerShell. In CMD use `mvnw.cmd test`; on macOS or Linux use
`./mvnw test`. The project also imports into IntelliJ directly from `pom.xml`.

**Current status: 89 tests, all passing.**

### Test report

Every run writes an ExtentReports HTML dashboard to `target/extent-report/index.html`. Open it in a
browser; no server or CLI is needed and it works with no network connection.

```powershell
.\mvnw.cmd test; start target\extent-report\index.html
```

It shows a pass/fail donut, the run environment, and every test grouped by suite with its duration.
Failures carry the assertion message and stack trace inline. Raw JUnit XML is also written to
`target/surefire-reports/` for CI consumption.

---

## What is here

| Path | Contents |
|---|---|
| [`docs/TEST-STRATEGY.md`](docs/TEST-STRATEGY.md) | Scope, assumptions, the ambiguity log, risk analysis, techniques, traceability, exit criteria |
| [`docs/MANUAL-TEST-CASES.md`](docs/MANUAL-TEST-CASES.md) | 73 test cases in full detail |
| [`docs/manual-test-cases.csv`](docs/manual-test-cases.csv) | The same cases for Excel or a test management tool |
| `src/main/java/com/crd/rebalance/` | The rebalancing engine under test |
| `src/test/java/com/crd/rebalance/` | The JUnit 5 suite |
| `src/test/resources/test-data/` | Acceptance scenarios in CSV |

**A note on the engine.** The assessment describes an application but does not supply one, so a
reference implementation was written to test against. It is deliberately small — nine types, mostly
records and enums — and every rule the specification leaves open is a configuration parameter rather
than a baked-in assumption. That is what allows the suite to test all three rounding policies, rather than
quietly adopting one and asserting that it agrees with itself.

---

## How the suite is built

**73 documented cases run as 89 JUnit tests**, because parameterised cases expand — once per
rounding policy, once per seed, once per CSV row.

Four kinds of test, because no single kind is sufficient:

- **Example-based** — the assessment's own numbers, plus boundary and negative cases. Expected
  values were derived by hand and cross-checked against an independent model written in another
  language, before any Java existed.
- **Data-driven** — acceptance scenarios in CSV so a business analyst can add a case without
  touching code.
- **Metamorphic** — idempotence, scale invariance, permutation invariance. These change an input in
  a way whose effect is known in advance, catching defects no fixed expected value would.
- **Property-based** — seven invariants checked against 1,400 randomly generated accounts. These
  need no oracle at all, because they assert relationships rather than values: an on-target line is
  never traded, direction always opposes variance, no position ends further from target than it
  started, value is always conserved.

Money is `BigDecimal` throughout. One test asserts both the correct decimal answer *and* the wrong
binary one, so it fails the moment anyone swaps in a `double`.
