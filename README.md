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

## Two things to highlight

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

**Current status: 54 tests, all passing.**

### In IntelliJ

Three shared Maven run configurations ship in `.run/` and appear in the run dropdown as soon as the
project is opened. No setup, and they are version controlled rather than living in local IDE state.

| Configuration | Goals | Use |
|---|---|---|
| **All Tests** | `test` | The full suite, 54 tests |
| **Golden Scenario** | `test -Dtest=GoldenScenarioTest` | Just the assessment answer, 10 tests, about 2 seconds |
| **Clean Test With Report** | `clean test` | Full rebuild, then a fresh HTML report |

IntelliJ picks up the Maven wrapper automatically from `.mvn/wrapper/`, so it uses the same Maven
version as the command line. If it ever prompts for a Maven home, point it at the wrapper rather
than a system install.

### Console output

The run narrates itself. Each suite, each test and each result is printed as it happens, and the
tests that matter most log their actual computed values so you can see what was verified rather
than just that something passed.

```
  [ RB-001 the output column ]
      > buys 66 IBM and sells 45 ORCL, trading nothing else
        - IBM signed quantity        expected 66    actual 66
        - ORCL signed quantity       expected -45   actual -45
        - MSFT / AAPL / HD           expected 0 each  actual 0 / 0 / 0
        PASS  (19 ms)
```

The six cases that exposed a specification problem announce themselves with a `FINDING:` line, so
`.\mvnw.cmd test | findstr FINDING` lists them all. Failures print the assertion diff inline.

Silence it with `-Dtest.console=off`.

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
| [`docs/FRAMEWORK.md`](docs/FRAMEWORK.md) | One-page reference: stack, classes, algorithm, case index |
| [`docs/TEST-STRATEGY.md`](docs/TEST-STRATEGY.md) | Scope, assumptions, the ambiguity log, risk analysis, techniques, traceability, exit criteria |
| [`docs/MANUAL-TEST-CASES.md`](docs/MANUAL-TEST-CASES.md) | The same 37 cases with preconditions, steps and expected results |
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

**37 documented cases run as 54 JUnit tests** across 30 test methods, because parameterised cases
expand — once per rounding policy, once per seed, once per CSV row.

Four kinds of test, because no single kind is sufficient:

- **Example-based** — the assessment's own numbers, plus boundary and negative cases. Expected
  values were derived by hand and cross-checked against an independent model written in another
  language, before any Java existed.
- **Data-driven** — acceptance scenarios in CSV so a business analyst can add a case without
  touching code.
- **Metamorphic** — idempotence. Changes an input in a way whose effect is known in advance,
  catching defects no fixed expected value would.
- **Property-based** — seven invariants checked against 1,400 randomly generated accounts. These
  need no oracle at all, because they assert relationships rather than values: an on-target line is
  never traded, direction always opposes variance, no position ends further from target than it
  started, value is always conserved.

Money is `BigDecimal` throughout. One test asserts both the correct decimal answer *and* the wrong
binary one, so it fails the moment anyone swaps in a `double`.

---

## Test cases

All 37 cases and what each one proves. Rows marked **FINDING** are the ones that surfaced a real
problem with the specification rather than just confirming expected behaviour.

### TS-01 Core calculation (`GoldenScenarioTest`)

| # | Test case | Validates |
|---|---|---|
| RB-001 | Output column for Account ABC | Exactly two orders: BUY 66 IBM and SELL 45 ORCL, nothing for MSFT, AAPL, HD |
| RB-002 | Post-trade position | IBM 19.90%, ORCL 20.10%, residual 0.10 points, total assets still 100,000 |
| RB-003 | Negative variance buys | The IBM order side is BUY |
| RB-004 | Positive variance sells | The ORCL order side is SELL |
| RB-005 | Zero variance does not trade | No order generated for the three on-target lines |
| RB-006 | Sizing basis | Gap read as percentage points of total assets (9,900), not 10% of the position (990) |
| RB-007 | Fractional truncation | 66.6667 becomes 66 and 45.4545 becomes 45 |
| RB-008 | Cash neutrality | Buys total 9,900, sells total 9,900, net cash impact zero |

### TS-02 Rounding policy (`RoundingPolicyTest`)

| # | Test case | Validates |
|---|---|---|
| RB-011 | Buy truncates down | 66 shares, never 67 |
| RB-012 | Sell truncates toward zero | 45 shares, not 46 |
| RB-013 | Residual bound | Untraded remainder is smaller than one share, under every policy |
| RB-014 | **FINDING** HALF_UP overdraft | BUY 67 IBM costs 10,050 against 9,900 raised; net cash -150 and the block is flagged unfundable |
| RB-015 | **FINDING** ROUND_UP overshoot | SELL 46 ORCL pushes it to 19.88%, crossing the target instead of approaching it |
| RB-016 | Sub-share gap | A 100 gap at 450/share produces no order; the same gap at 10/share sells 10 |

### TS-04 Input validation (`InputValidationTest`)

| # | Test case | Validates |
|---|---|---|
| RB-030 | Target sum | Targets not summing to 100 rejected, message quotes the actual sum |
| RB-033 | Bad price | 0, -1 and -150.50 all rejected before any division happens |
| RB-034 | Missing price | null rejected as a failed feed, never coerced to zero |

### TS-05 Cash, funding and sequencing (`CashFundingTest`)

| # | Test case | Validates |
|---|---|---|
| RB-041 | **FINDING** Truncation shortfall | Buy divides exactly, sell loses 1 to truncation; block is 1 short and unfundable |
| RB-042 | **FINDING** Sequencing | The same orders sent buys-first hit -9,900 running cash and cannot execute |
| RB-044 | Value conservation | Holdings plus cash equal 100,000 under all three rounding policies |

### TS-06 Business rules (`BusinessRuleTest`)

| # | Test case | Validates |
|---|---|---|
| RB-051 | Partial vesting | At 80% vested each target becomes 16%; BUY 40 IBM plus sells on all four other lines |
| RB-054 | Tolerance band | A 10 point band leaves a 10 point drift alone; 9.99 trades it |
| RB-055 | **FINDING** Zero variance unreachable | Residual is above zero yet inside 0.5 points, so the band is the real criterion |

### TS-07 Precision (`PrecisionTest`)

| # | Test case | Validates |
|---|---|---|
| RB-060 | **FINDING** Decimal vs double | 7000/0.07 gives 100,000 shares; the double result 99,999 is asserted as the wrong answer |
| RB-063 | Single rounding step | Rounding applied only at the share count, intermediates stay exact |

### TS-08 Metamorphic relations (`IdempotenceTest`)

| # | Test case | Validates |
|---|---|---|
| RB-070 | Idempotence | A second pass over a rebalanced account generates no churn |

### TS-10 Invariants (`InvariantPropertyTest`)

Seven properties, each checked against 1,400 generated accounts (7 fixed seeds x 200 accounts).

| # | Test case | Validates |
|---|---|---|
| RB-090 | On-target lines never trade | No order wherever variance is zero |
| RB-091 | Direction opposes variance | Underweight lines only buy, overweight lines only sell |
| RB-092 | Variance never worsens | No position ends further from its target than it started |
| RB-093 | Residual under one share | Untraded remainder is always worth less than one share |
| RB-094 | Value conserved | Holdings plus cash always equal total assets |
| RB-095 | No short positions | No position is ever sold below zero |
| RB-096 | Deterministic output | The same account rebalanced twice gives identical orders |

### TS-11 Data driven (`DataDrivenTest`)

| # | Test case | Validates |
|---|---|---|
| RB-100 | Answer key | Every ABC line matches `account-abc.csv`, and the fixture inputs match the row so they cannot drift apart |
| RB-101 | Scenario table | Nine two-line scenarios from CSV covering rounding both ways, full exit and entry, penny prices, cents, and fractional percentages |

### TS-12 Manual verification (not automated)

Sign-off activities. Automation cannot settle these because what is being checked is human
agreement, not program behaviour.

| # | Test case | Validates |
|---|---|---|
| RB-120 | Rounding policy sign-off | The intended policy is confirmed in writing by the business owner |
| RB-121 | Tolerance band sign-off | A real number is agreed; "zero" is challenged as unachievable |
