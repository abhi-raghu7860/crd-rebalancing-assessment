# Framework and Test Case Reference

## Run

```powershell
.\mvnw.cmd test                             # all 89 tests
.\mvnw.cmd -Dtest=GoldenScenarioTest test   # assessment scenario only
start target\extent-report\index.html       # open the HTML report
```

Java 21. Maven via wrapper (nothing to install). `.\` prefix is required in PowerShell only.

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
| `RoundingPolicyTest` | RB-010..016 | Each rounding policy's output and its cash consequence |
| `BoundaryValueTest` | RB-020..028 | Edges of variance, price, and account size |
| `InputValidationTest` | RB-030..039 | Bad input rejected at the boundary with a named error |
| `CashFundingTest` | RB-040..045 | Whether the order block can actually be funded and executed |
| `BusinessRuleTest` | RB-050..055 | Vesting, min notional, round lots, tolerance band |
| `PrecisionTest` | RB-060..065 | Decimal correctness, determinism, immutability |
| `IdempotenceTest` | RB-070..073 | Metamorphic relations |
| `NonFunctionalTest` | RB-080..082 | Throughput and thread safety |
| `InvariantPropertyTest` | RB-090..096 | 7 invariants over 1,400 generated accounts |
| `DataDrivenTest` | RB-100..101 | CSV acceptance data |
| `Fixtures` | - | Shared test data (Account ABC) |
| `report/ExtentReportListener` | - | Builds the HTML report. Auto-registered via ServiceLoader, no test class references it. |

73 documented cases run as 89 tests. Parameterised cases expand: once per policy, per seed, per CSV row.

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
| RB-010 | Exact division gives the same answer under all 3 policies |
| RB-011 | Buy truncates down |
| RB-012 | Sell truncates toward zero |
| RB-013 | Untraded remainder is always less than one share |
| RB-014 | HALF_UP buys 67 IBM, net cash -150, block not fundable (FINDING) |
| RB-015 | ROUND_UP sells 46 ORCL, overshoots target to 19.88% (FINDING) |
| RB-016 | Gap worth less than one share generates no order |

### TS-03 Boundary values (BoundaryValueTest)
| ID | Validates |
|---|---|
| RB-020 | 0.001 point variance buys exactly 1 share |
| RB-021 | Variance of the full 100 points |
| RB-022 | current% of 0 bought up to a 100% target |
| RB-023 | target% of 0 fully liquidates the position |
| RB-024 | 0.01 price gives 500,000 shares with no precision loss |
| RB-025 | Empty security list handled without error |
| RB-026 | Zero total assets: no orders, no divide-by-zero |
| RB-027 | 1e12 account exact, no overflow |
| RB-028 | Single security already on target |

### TS-04 Input validation (InputValidationTest)
| ID | Validates |
|---|---|
| RB-030 | target% not summing to 100 rejected |
| RB-031 | current% under 100 allowed, remainder is cash |
| RB-032 | current% over 100 rejected |
| RB-033 | Zero or negative price rejected before any division |
| RB-034 | Null price rejected as a failed feed, not treated as zero |
| RB-035 | target% outside 0..100 rejected |
| RB-036 | current% outside 0..100 rejected |
| RB-037 | Duplicate symbol rejected, not silently aggregated |
| RB-038 | Blank symbol rejected |
| RB-039 | Null and negative fields give a named error, not an NPE |

### TS-05 Cash and funding (CashFundingTest)
| ID | Validates |
|---|---|
| RB-040 | Block raises exactly what it spends; min running cash zero |
| RB-041 | Truncation does not guarantee fundability: 1 dollar short (FINDING) |
| RB-042 | Same orders buys-first reach -9,900 and cannot execute (FINDING) |
| RB-043 | Existing cash balance funds purchases |
| RB-044 | Holdings plus cash still equal total assets, under all 3 policies |
| RB-045 | No position sold below zero |

### TS-06 Business rules (BusinessRuleTest)
| ID | Validates |
|---|---|
| RB-050 | 100% vested makes the whole account tradeable |
| RB-051 | 80% vested retargets every line: BUY 40 IBM, SELL 44/63/8/57 |
| RB-052 | Orders below min notional suppressed |
| RB-053 | Quantity cut down to a whole round lot |
| RB-054 | Variance inside the tolerance band left alone |
| RB-055 | Zero variance unreachable, so the band is the real criterion (FINDING) |

### TS-07 Precision (PrecisionTest)
| ID | Validates |
|---|---|
| RB-060 | 7000/0.07 gives 100,000 shares; double gives 99,999 (FINDING) |
| RB-061 | Price of 150.37 exact, notional 9,924.42 |
| RB-062 | Fractional target% (33.25) honoured |
| RB-063 | Rounding applied once at the share step, not on intermediates |
| RB-064 | Same input always gives same output |
| RB-065 | Engine never mutates the account it is given |

### TS-08 Metamorphic (IdempotenceTest)
| ID | Validates |
|---|---|
| RB-070 | Second pass over a rebalanced account generates no churn |
| RB-071 | No security ends further from target than it started |
| RB-072 | 10x account size gives 10x quantities |
| RB-073 | Input order does not change the answer |

### TS-09 Non-functional (NonFunctionalTest)
| ID | Validates |
|---|---|
| RB-080 | 5,000 lines rebalance in under 2 seconds |
| RB-081 | 64 concurrent runs match the single-threaded result |
| RB-082 | Value conserved exactly across 5,000 lines |

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
| RB-122 | Reconcile against an independently built spreadsheet |
| RB-123 | Review the order blotter with a trader for direction ambiguity |
| RB-124 | Exploratory session on stale, missing and changing prices |

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
