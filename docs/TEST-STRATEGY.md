# Test Strategy — Portfolio Rebalancing

**Application under test:** the rebalancing calculation that converts a target allocation into the
orders needed to reach it.
**Prepared for:** Charles River Development / Alpha Platform Engineering technical assessment.

---

## 1. What the application does

Given an account's total assets, and for each security a target percentage, a current percentage
and a unit price, it must output the number of shares to buy or sell per security.

```
variance      = current% − target%          (percentage points; −ve = buy, +ve = sell)
tradeNotional = −variance ÷ 100 × totalAssets
shares        = tradeNotional ÷ unitPrice   reduced to a whole number
```

### The answer for Account ABC

| Security | target% | current% | variance | price | notional | **Output** |
|---|---|---|---|---|---|---|
| IBM | 20 | 10 | −10 | $150 | +$10,000 | **BUY 66** |
| MSFT | 20 | 20 | 0 | $90 | — | **0** |
| ORCL | 20 | 30 | +10 | $220 | −$10,000 | **SELL 45** |
| AAPL | 20 | 20 | 0 | $450 | — | **0** |
| HD | 20 | 20 | 0 | $70 | — | **0** |

Both orders are worth exactly $9,900, so the block funds itself and needs no cash — which matters,
because the account is 100% invested and holds none. IBM ends at 19.90% and ORCL at 20.10%.

---

## 2. The oracle problem

The hardest question in testing this is not "what does the code return" but "how do we know what it
*should* return". Asserting that the implementation agrees with itself proves nothing. Three
independent sources of truth are used instead:

1. **Hand calculation**, reproduced in the tables above and in `docs/MANUAL-TEST-CASES.md`.
2. **An independent model** written in a different language and by a different route, used to derive
   every expected value before any Java was written.
3. **Invariants** (section 6) that must hold for *any* input, checked against 1,400 randomly
   generated accounts. These need no oracle at all, because they assert relationships rather than
   values.

---

## 3. Assumptions

Recorded because the specification does not state them. Each one is a question for the business
analyst, and each is testable.

| ID | Assumption |
|---|---|
| ASM-01 | `variance = current% − target%`. A negative variance buys, a positive variance sells. Taken from the PDF's own worked rows (IBM 10 − 20 = −10). |
| ASM-02 | Percentages are percentage **points of total assets**, not relative percentages of the position. IBM's 10-point gap is $10,000, not $1,000. |
| ASM-03 | Only whole shares can be traded. Fractional-share trading is out of scope. |
| ASM-04 | Target percentages must sum to exactly 100. Current percentages may sum to less, and the shortfall is uninvested cash. |
| ASM-05 | Only vested assets are tradeable, so targets apply to the vested base. At 100% vested this is the whole account, which is why the assessment scenario is unaffected. |
| ASM-06 | Unit prices are current, clean and executable. No bid/ask spread, commission, tax, or market impact. |
| ASM-07 | Orders fill completely at the quoted unit price. Partial fills are out of scope. |
| ASM-08 | Single currency, no FX, no accrued income, no settlement lag beyond sequencing sells ahead of buys. |
| ASM-09 | Rounding is truncation toward zero unless configured otherwise — see AMB-01. |

---

## 4. Ambiguity log — what I would raise before writing any code

These are the findings. They are requirement defects, not code defects, and every one is pinned by
an executable test so the decision cannot be lost.

### AMB-01 — The rounding rule is unspecified, and the choice is not cosmetic

IBM needs 66.6667 shares and ORCL 45.4545. Each policy gives a different, defensible answer:

| Policy | IBM | ORCL | Net cash | Consequence |
|---|---|---|---|---|
| **Truncate toward zero** | BUY 66 | SELL 45 | **$0** | Never overshoots. Recommended. |
| Round half up | BUY 67 | SELL 45 | **−$150** | **Overdraws an account with no cash.** |
| Round magnitude up | BUY 67 | SELL 46 | +$70 | Pushes ORCL to 19.88%, past its target. |

An unstated rounding rule is therefore a live overdraft risk. **Recommendation: truncate**, because
it is the only policy that both avoids overshooting a target and keeps this block self-funding.
Pinned by `RoundingPolicyTest`.

### AMB-02 — "Zero target variance" is unattainable, so it is the wrong acceptance criterion

Exact 20.00% would take 66.6667 IBM shares. Whole shares cannot express that, so *some* residual
always remains — 0.10 points here. The acceptance criterion has to be **variance within a tolerance
band**, which is how drift bands work in practice. Testing for equality would fail a correct
implementation. Pinned by `BusinessRuleTest.exactZeroVarianceIsUnreachable`.

### AMB-03 — The sign convention is counterintuitive and the output column is unlabelled

A *negative* variance means *buy*. The output column asks for "number of shares to buy/sell" without
saying how direction is encoded, so `-45` and `45 SELL` are equally consistent with the spec. A sign
flip is the single most likely defect in this application and the easiest to miss in review, because
the magnitudes stay right. The implementation puts direction in an explicit `Side` enum rather than
in the sign of a number, and `GoldenScenarioTest` asserts direction separately from magnitude.

### AMB-04 — Percentage points versus relative percent

Reading IBM's "−10" as 10% of the $10,000 position rather than 10 points of the $100,000 account
sizes a $1,000 order and buys 6 shares instead of 66. Both readings are grammatical English. Pinned
by `GoldenScenarioTest.sizesFromPercentagePointsOfTotalAssets`.

### AMB-05 — Funding and sequencing are unspecified, and truncation alone does not fix them

Account ABC holds no cash, so every purchase depends on the matching sale. Two distinct failures
follow:

- **Sequencing.** The same two orders sent buys-first leave the account $9,900 short. Identical
  arithmetic, identical net cash, unexecutable. The engine emits sells first.
- **Truncation does not guarantee fundability.** It is cash-neutral on *this* data by coincidence.
  Change ORCL's price to $3 and IBM's to $1 and the buy divides exactly while the sell loses $1 to
  truncation — a self-funding block that is $1 short. A real system needs a cash buffer or must size
  buys against realised proceeds.

Pinned by `CashFundingTest`.

### AMB-06 — No minimum trade size, round lot, or stale price rules

Real order management systems suppress uneconomic orders, trade in round lots, and refuse to price
off a stale feed. None are specified. All three are implemented as configuration and tested, so the
behaviour is defined the moment the business supplies a number.

---

## 5. Risk analysis and prioritisation

Testing effort is weighted by (likelihood of the defect) × (cost if it ships). This is a system that
generates real orders against real money; a wrong quantity is not a cosmetic bug.

| Risk | Impact | Likelihood | Priority | Mitigation |
|---|---|---|---|---|
| Sign flip: buys where sells belong | Severe — doubles the drift and trades twice | Medium | **P0** | Direction asserted separately from magnitude; property test over 1,400 accounts |
| Percentage points read as relative percent | Severe — orders 10× too small | Medium | **P0** | Explicit notional assertion (RB-006) |
| Rounding overdraws a fully invested account | High — order rejected or margin call | High | **P0** | All three policies pinned (RB-014/015) |
| Buys sequenced ahead of funding sells | High — block rejected at the broker | Medium | **P0** | Running-cash check (RB-042) |
| Binary float drift in money maths | Moderate individually, compounds across a book | Medium | **P1** | `BigDecimal` throughout; RB-060 fails if anyone reverts to `double` |
| Divide-by-zero on a zero or missing price | Moderate — crash or nonsense quantity | Medium | **P1** | Rejected at construction (RB-032/034) |
| Silent double-count of a duplicated symbol | Moderate | Low | **P1** | Rejected at construction (RB-037) |
| Overflow or precision loss on a large account | Moderate | Low | **P2** | Trillion-dollar case (RB-027) |
| Performance across a wide book | Low | Low | **P2** | 5,000 lines under two seconds (RB-080) |

---

## 6. Test approach

### Levels

- **Unit** — variance, notional and quantity derivation in isolation.
- **Component** — the engine end to end, which is where the assessment's acceptance criterion sits.
- **Data-driven** — acceptance scenarios in CSV so a business analyst can add cases without code.
- **Property-based** — invariants over generated accounts.
- **Non-functional** — throughput on a wide book, thread safety.

### Techniques and where each is applied

| Technique | Applied to |
|---|---|
| Equivalence partitioning | The three variance classes: negative (buy), zero (no trade), positive (sell) |
| Boundary value analysis | variance at 0, ±0.001 and ±100; price at $0.01 and above the gap; assets at 0 and $1tn |
| Decision table | The core rule, below |
| Metamorphic testing | Idempotence, scale invariance, permutation invariance |
| Property-based testing | Seven invariants over 1,400 generated accounts |
| Risk-based prioritisation | P0/P1/P2 assignment in section 5 |
| Error guessing | Duplicate symbols, null prices, blank symbols, negative assets |

### Decision table for the core rule

| Variance | Divides exactly | Gap ≥ one share | Outcome |
|---|---|---|---|
| Zero | — | — | No order |
| Negative | Yes | Yes | BUY exact quantity |
| Negative | No | Yes | BUY truncated quantity |
| Negative | No | No | No order — gap is worth less than one share |
| Positive | Yes | Yes | SELL exact quantity |
| Positive | No | Yes | SELL truncated quantity |
| Positive | No | No | No order |

### The seven invariants

Checked against every generated account. These hold regardless of input, so they need no oracle:

1. A line already on target is never traded.
2. Order direction is always the opposite of the variance sign.
3. No position ends further from its target than it started.
4. Whatever is left untraded is worth less than one share of that security.
5. Holdings plus cash still equal total assets — value is moved, never created or destroyed.
6. No position is sold below zero.
7. The same input always produces identical output.

---

## 7. Coverage and traceability

| Suite | IDs | Cases | Automated |
|---|---|---|---|
| TS-01 Core calculation | RB-001…008 | 8 | Yes |
| TS-02 Rounding and residual | RB-010…016 | 7 | Yes |
| TS-03 Boundary values | RB-020…028 | 9 | Yes |
| TS-04 Input validation | RB-030…039 | 10 | Yes |
| TS-05 Cash, funding, sequencing | RB-040…045 | 6 | Yes |
| TS-06 Business rules | RB-050…055 | 6 | Yes |
| TS-07 Precision and determinism | RB-060…065 | 6 | Yes |
| TS-08 Metamorphic relations | RB-070…073 | 4 | Yes |
| TS-09 Non-functional | RB-080…082 | 3 | Yes |
| TS-10 Invariants | RB-090…096 | 7 | Yes |
| TS-11 Data driven | RB-100…101 | 2 | Yes |
| TS-12 Manual verification | RB-120…124 | 5 | No — by design |
| **Total** | | **73** | **68 automated** |

The 73 documented cases execute as **89 JUnit tests**, because parameterised cases expand: RB-013
runs once per rounding policy, RB-090…096 once per seed, RB-101 once per CSV row.

The five manual cases are manual on purpose. They are reconciliation and sign-off activities —
agreeing the rounding policy with the front office, checking the blotter reads correctly to a
trader — that automation cannot discharge.

---

## 8. Entry and exit criteria

**Entry:** requirements available; ambiguities in section 4 raised with the business analyst; test
data and expected values derived independently of the implementation; build runs green on a clean
checkout.

**Exit:**
- All P0 and P1 cases pass. No P0 defect open.
- The golden scenario returns BUY 66 IBM and SELL 45 ORCL with zero net cash.
- All seven invariants hold across every generated account.
- Every assumption in section 3 is either confirmed by the business analyst or logged as an open
  question.
- Any residual variance is inside the agreed tolerance band.

---

## 9. Environment and tooling

Java 21, JUnit 5.14.4, AssertJ 3.27.7, Maven via the bundled wrapper. No other setup: `mvnw test`
from a clean checkout downloads what it needs and runs.

**On property-based testing:** an off-the-shelf generator library was evaluated and dropped in
favour of fixed-seed `java.util.Random` generation. It removes a third-party dependency from the
build and keeps failures exactly reproducible — a failing seed replays the precise account that
broke it.

---

## 10. Out of scope

Order routing and execution, settlement, compliance and restriction checking, tax-lot selection,
wash-sale rules, multi-currency and FX, partial fills, market impact and transaction cost analysis,
persistence, and any user interface. Each would need its own strategy; the assessment defines the
application as the share-count calculation.
