# Framework and Test Case Reference

## Run

```powershell
.\mvnw.cmd test                             # all 54 tests
.\mvnw.cmd -Dtest=GoldenScenarioTest test   # assessment scenario only
start target\extent-report\index.html       # open the HTML report
```

Java 21. Maven via wrapper (nothing to install). `.\` prefix is required in PowerShell only.

In IntelliJ, use the shared run configurations in `.run/`: **All Tests**, **Golden Scenario**,
**Clean Test With Report**. They appear in the run dropdown with no setup.

Output:
- `target/extent-report/index.html` - ExtentReports dashboard, offline, open in any browser
- `target/surefire-reports/` - raw JUnit XML for CI

## Stack

| Layer | Choice |
|---|---|
| Language | Java 21 |
| Test runner | JUnit 5.14.4 (Jupiter) |
| Assertions | AssertJ 3.27.7 |
| Data-driven | `@ParameterizedTest` + `@CsvFileSource` |
| Property gen | `java.util.Random`, fixed seeds (no external lib) |
| Build | Maven 3.9.16 wrapper, Surefire 3.5.6 |
| Reporting | ExtentReports 5.1.2 (Spark), offline mode |
| Money type | `BigDecimal` only, never `double` |

## Code under test (`src/main/java/com/crd/rebalance`)

| Class | Role |
|---|---|
| `Security` | One holding line: symbol, target%, current%, unitPrice. Validates on construction. |
| `Account` | Total assets, vested%, cash, securities. Rejects bad structure on construction. |
| `RebalanceConfig` | The 5 rules the spec leaves open: rounding, tolerance band, min notional, lot size, sell-first. |
| `RoundingPolicy` | TRUNCATE / HALF_UP / ROUND_UP. |
| `RebalanceEngine` | The SUT. `rebalance(account, config) -> result`. Stateless. |
| `Order` | symbol, side, positive quantity, notional. Direction in `Side`, not in the sign. |
| `RebalanceResult` | Orders in execution sequence, post-trade values, post-trade variance, cash, min running cash. |
| `Side`, `Rebalancing` | Enum BUY/SELL; shared constants and MathContext. |

## Algorithm

```
variance      = current% - target%              (percentage points)
tradeNotional = -variance / 100 * totalAssets   (+ve = buy)
rawShares     = tradeNotional / unitPrice
quantity      = round(rawShares, policy)        rounded once, at this step only
```
Then: apply lot size, drop if below min notional, drop if zero. Sells emitted before buys.

## Test classes

| Class | Cases | Tests what |
|---|---|---|
| `GoldenScenarioTest` | RB-001..008 | The assessment answer: BUY 66 IBM, SELL 45 ORCL, cash neutral |
| `RoundingPolicyTest` | RB-011..016 | Each rounding policy's output and its cash consequence |
| `InputValidationTest` | RB-030, 033, 034 | Bad input rejected at the boundary with a named error |
| `CashFundingTest` | RB-041, 042, 044 | Whether the order block can actually be funded and executed |
| `BusinessRuleTest` | RB-051, 054, 055 | Vesting and the drift tolerance band |
| `PrecisionTest` | RB-060, 063 | Decimal correctness and single-step rounding |
| `IdempotenceTest` | RB-070 | Metamorphic: a second pass generates no churn |
| `InvariantPropertyTest` | RB-090..096 | 7 invariants over 1,400 generated accounts |
| `DataDrivenTest` | RB-100..101 | CSV acceptance data |
| `Fixtures` | - | Shared test data (Account ABC) |
| `report/ExtentReportListener` | - | Builds the HTML report. Auto-registered via ServiceLoader, no test class references it. |
| `report/ConsoleReportListener` | - | Prints the live suite/test tree and the run summary. |

37 documented cases run as 54 tests across 30 test methods. Parameterised cases expand: once per
policy, per seed, per CSV row.

## Test cases

### TS-01 Core calculation (GoldenScenarioTest)
| ID | Validates |
|---|---|
| RB-001 | Output column: BUY 66 IBM, SELL 45 ORCL, no other orders |
| RB-002 | Post-trade IBM 19.90%, ORCL 20.10%, residual 0.10 points |
| RB-003 | Negative variance -> BUY |
| RB-004 | Positive variance -> SELL |
| RB-005 | Zero variance -> no order |
| RB-006 | Gap sized from percentage points of total assets, not % of position |
| RB-007 | 66.6667 truncates to 66, not 67 |
| RB-008 | Buys and sells both 9,900; net cash zero |

### TS-02 Rounding (RoundingPolicyTest)
| ID | Validates |
|---|---|
| RB-011 | Buy truncates down |
| RB-012 | Sell truncates toward zero |
| RB-013 | Untraded remainder is always less than one share, under all 3 policies |
| RB-014 | HALF_UP buys 67 IBM, net cash -150, block not fundable (FINDING) |
| RB-015 | ROUND_UP sells 46 ORCL, overshoots target to 19.88% (FINDING) |
| RB-016 | Gap worth less than one share generates no order |

### TS-04 Input validation (InputValidationTest)
| ID | Validates |
|---|---|
| RB-030 | target% not summing to 100 rejected |
| RB-033 | Zero or negative price rejected before any division |
| RB-034 | Null price rejected as a failed feed, not treated as zero |

### TS-05 Cash and funding (CashFundingTest)
| ID | Validates |
|---|---|
| RB-041 | Truncation does not guarantee fundability: 1 dollar short (FINDING) |
| RB-042 | Same orders buys-first reach -9,900 and cannot execute (FINDING) |
| RB-044 | Holdings plus cash still equal total assets, under all 3 policies |

### TS-06 Business rules (BusinessRuleTest)
| ID | Validates |
|---|---|
| RB-051 | 80% vested retargets every line: BUY 40 IBM, SELL 44/63/8/57 |
| RB-054 | Variance inside the tolerance band left alone |
| RB-055 | Zero variance unreachable, so the band is the real criterion (FINDING) |

### TS-07 Precision (PrecisionTest)
| ID | Validates |
|---|---|
| RB-060 | 7000/0.07 gives 100,000 shares; double gives 99,999 (FINDING) |
| RB-063 | Rounding applied once at the share step, not on intermediates |

### TS-08 Metamorphic (IdempotenceTest)
| ID | Validates |
|---|---|
| RB-070 | Second pass over a rebalanced account generates no churn |

### TS-10 Invariants (InvariantPropertyTest)
7 seeds x 200 generated accounts. 2-8 securities, targets summing to 100, currents at most 100,
prices 0.01 to 500. Seeds fixed, so any failure replays the exact account.

| ID | Invariant |
|---|---|
| RB-090 | An on-target line is never traded |
| RB-091 | Order direction always opposes the variance sign |
| RB-092 | No position ends further from target than it started |
| RB-093 | Untraded remainder worth less than one share |
| RB-094 | Holdings plus cash equal total assets |
| RB-095 | No position sold below zero |
| RB-096 | Same input gives identical output |

### TS-11 Data driven (DataDrivenTest)
| ID | Validates |
|---|---|
| RB-100 | `account-abc.csv`: expected quantity per security, plus fixture matches the key |
| RB-101 | `two-line-scenarios.csv`: 9 scenarios covering rounding, exits, penny prices, fractions |

### TS-12 Manual only (not automated)
| ID | Activity |
|---|---|
| RB-120 | Get the rounding policy confirmed in writing by the business owner |
| RB-121 | Agree the tolerance band ("zero" is not achievable) |

## Findings

| ID | Finding |
|---|---|
| AMB-01 | Rounding rule unspecified. HALF_UP overdraws a fully invested account by 150. |
| AMB-02 | Zero variance unreachable with whole shares. Criterion must be a tolerance band. |
| AMB-03 | Sign convention counterintuitive: negative variance means buy. Highest-risk defect. |
| AMB-04 | Percentage points vs relative percent: wrong reading buys 6 IBM instead of 66. |
| AMB-05 | Funding and sequencing unspecified. Buys-first is unexecutable; truncation is not always fundable. |
| AMB-06 | No min trade size, round lot, or stale price rules given. |

Full detail in `TEST-STRATEGY.md`. Case detail in `MANUAL-TEST-CASES.md`.
