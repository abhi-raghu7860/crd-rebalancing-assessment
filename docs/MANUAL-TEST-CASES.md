# Manual Test Cases — Portfolio Rebalancing

73 cases across 12 suites. 68 are automated in `src/test/java/com/crd/rebalance/`; the last five are
manual by design and explained in TS-12.

**Standing precondition for every case:** the rebalancing engine is available, prices are current,
and the account passes structural validation unless the case says otherwise.
**Default configuration:** truncate toward zero, no tolerance band, no minimum trade size, lot size
of 1, sells sequenced before buys.

**Account ABC** (used throughout): $100,000 total assets, 100% vested, $0 cash.

| Security | target% | current% | variance | unit price |
|---|---|---|---|---|
| IBM | 20 | 10 | −10 | $150 |
| MSFT | 20 | 20 | 0 | $90 |
| ORCL | 20 | 30 | +10 | $220 |
| AAPL | 20 | 20 | 0 | $450 |
| HD | 20 | 20 | 0 | $70 |

---

## TS-01 — Core calculation (the assessment's own scenario)

### RB-001 — Output column for Account ABC · P0 · Smoke
**Preconditions:** Account ABC as above.
**Steps:** Rebalance Account ABC with default configuration.
**Expected:** Exactly two orders. **BUY 66 IBM** and **SELL 45 ORCL**. MSFT, AAPL and HD produce no
order.
**Notes:** This is the answer to the assessment's yellow output column. IBM's 10-point shortfall is
$10,000, and $10,000 ÷ $150 = 66.67 shares, truncated to 66. ORCL's 10-point excess is $10,000, and
$10,000 ÷ $220 = 45.45 shares, truncated to 45.
**Technique:** Specification-based. **Automated:** Yes.

### RB-002 — Post-trade variance is 0.10 points · P0
**Steps:** Rebalance Account ABC and inspect post-trade holdings.
**Expected:** IBM $19,900 (19.90%, variance −0.10). ORCL $20,100 (20.10%, variance +0.10). The other
three unchanged at $20,000 (20.00%, variance 0). Holdings plus cash still total $100,000.
**Notes:** Demonstrates AMB-02 — exact zero variance is unreachable with whole shares.
**Automated:** Yes.

### RB-003 — Negative variance generates a BUY · P0
**Steps:** Rebalance Account ABC; inspect the IBM order.
**Expected:** Side is BUY.
**Notes:** Guards the sign convention (AMB-03), the highest-risk defect in this application.
**Technique:** Equivalence partitioning — the negative-variance class. **Automated:** Yes.

### RB-004 — Positive variance generates a SELL · P0
**Steps:** Rebalance Account ABC; inspect the ORCL order.
**Expected:** Side is SELL.
**Technique:** Equivalence partitioning — the positive-variance class. **Automated:** Yes.

### RB-005 — Zero variance generates no order · P0
**Steps:** Rebalance Account ABC; look for MSFT, AAPL and HD orders.
**Expected:** No order for any of the three. An on-target line must not churn.
**Technique:** Equivalence partitioning — the zero class. **Automated:** Yes.

### RB-006 — Sizing uses percentage points of total assets · P0
**Steps:** Rebalance Account ABC; inspect the IBM order notional.
**Expected:** $9,900, derived from a $10,000 gap.
**Notes:** Guards AMB-04. Reading "−10" as 10% of the $10,000 IBM position would give a $1,000 gap
and buy 6 shares. Both readings are grammatical; only one is right.
**Automated:** Yes.

### RB-007 — Fractional quantities truncate rather than round up · P0
**Steps:** Rebalance Account ABC.
**Expected:** 66.6667 becomes 66, not 67. 45.4545 becomes 45.
**Automated:** Yes.

### RB-008 — The block is cash neutral · P0
**Steps:** Rebalance Account ABC; total the buy and sell notionals.
**Expected:** $9,900 bought, $9,900 sold, net cash impact $0, ending cash $0.
**Notes:** Account ABC holds no cash, so this is what makes the block executable at all.
**Automated:** Yes.

---

## TS-02 — Rounding policy and residual

### RB-010 — Exact division is policy independent · P1
**Preconditions:** Two-line account, $100,000. AAA target 50 / current 40 / $100. BBB target 50 /
current 60 / $200.
**Steps:** Rebalance once under each of the three rounding policies.
**Expected:** BUY 100 AAA and SELL 50 BBB every time, with zero residual variance.
**Technique:** Boundary value — the point where the policies must agree. **Automated:** Yes.

### RB-011 — A buy truncates downward · P0
**Steps:** Rebalance Account ABC.
**Expected:** IBM 66.6667 → BUY 66. Never 67 under the default policy.
**Automated:** Yes.

### RB-012 — A sell truncates toward zero · P0
**Steps:** Rebalance Account ABC.
**Expected:** ORCL 45.4545 → SELL 45.
**Notes:** Toward zero, not toward negative infinity. On a sell the two differ.
**Automated:** Yes.

### RB-013 — The untraded remainder is smaller than one share · P1
**Steps:** For each policy, compare each post-trade holding against its target value.
**Expected:** Every residual is strictly less than that security's unit price. If it were not, the
engine left a whole tradeable share on the table.
**Automated:** Yes — runs once per policy.

### RB-014 — Round-half-up overdraws the account · P0 · **Finding**
**Steps:** Rebalance Account ABC with round-half-up.
**Expected:** **BUY 67 IBM** and SELL 45 ORCL. Net cash **−$150**. The block is reported as not
fundable.
**Notes:** 66.6667 rounds up to 67 but 45.4545 rounds down to 45, so the buy costs $10,050 against
$9,900 raised. Account ABC is 100% invested and has no cash to cover the gap. This is the concrete
consequence of AMB-01 and the reason truncation is recommended.
**Automated:** Yes.

### RB-015 — Rounding the magnitude up overshoots the target · P1 · **Finding**
**Steps:** Rebalance Account ABC with round-magnitude-up.
**Expected:** BUY 67 IBM, SELL 46 ORCL, net cash +$70. ORCL lands at 19.88%, variance −0.12.
**Notes:** ORCL started 0.10 points overweight and ends 0.12 points underweight — it has crossed its
target rather than approached it, so the position is now worse than a smaller trade would have left
it.
**Automated:** Yes.

### RB-016 — A gap worth less than one share does not trade · P1
**Preconditions:** Two-line account, $100,000. AAA target 20.1 / current 20 / $450. BBB target 79.9 /
current 80 / $10.
**Steps:** Rebalance.
**Expected:** No AAA order — its $100 gap will not buy a $450 share. SELL 10 BBB — its $100 gap is
ten $10 shares.
**Technique:** Boundary value on price against gap. **Automated:** Yes.

---

## TS-03 — Boundary values

### RB-020 — The smallest variance that can still trade · P2
**Preconditions:** $100,000 account. AAA target 50.001 / current 50 / $1. BBB target 49.999 /
current 50 / $1.
**Expected:** BUY 1 AAA, SELL 1 BBB. A thousandth of a point is a $1 gap and these shares cost $1.
**Automated:** Yes.

### RB-021 — Variance of the full 100 points · P1
**Expected:** Handled without overflow or truncation error; see RB-022.
**Automated:** Yes.

### RB-022 — A security held at 0% is bought to a 100% target · P1
**Preconditions:** $10,000 account, $10,000 cash. AAA target 100 / current 0 / $50.
**Expected:** BUY 200 AAA. Ending cash $0. Block is fundable.
**Technique:** Boundary value — the lower edge of current%. **Automated:** Yes (with RB-021).

### RB-023 — A 0% target liquidates the position fully · P1
**Preconditions:** $10,000 account. OUT target 0 / current 100 / $50. IN target 100 / current 0 / $25.
**Expected:** SELL 200 OUT, BUY 400 IN. OUT ends at $0. Net cash $0.
**Technique:** Boundary value — the lower edge of target%. **Automated:** Yes.

### RB-024 — A one-cent share price · P1
**Preconditions:** $10,000 account, $5,000 cash. PNY target 100 / current 50 / $0.01.
**Expected:** BUY 500,000 PNY, notional exactly $5,000. No precision loss on the large quantity.
**Automated:** Yes.

### RB-025 — An account holding no securities · P2
**Preconditions:** $10,000 account, all in cash, empty security list.
**Expected:** No orders, no post-trade holdings, ending cash $10,000. No exception.
**Technique:** Error guessing — the empty collection. **Automated:** Yes.

### RB-026 — An account with zero assets · P2
**Preconditions:** Total assets $0, five on-target securities.
**Expected:** No orders. No divide-by-zero when expressing post-trade values as percentages.
**Automated:** Yes.

### RB-027 — A one trillion dollar account · P2
**Preconditions:** Account ABC's percentages and prices, total assets $1,000,000,000,000.
**Expected:** BUY 666,666,666 IBM and SELL 454,545,454 ORCL. Exact, no overflow.
**Technique:** Boundary value — the upper edge of account size. **Automated:** Yes.

### RB-028 — A single fully allocated security · P2
**Preconditions:** $10,000 account, AAA target 100 / current 100 / $10.
**Expected:** No orders, zero variance.
**Automated:** Yes.

---

## TS-04 — Input validation

All cases in this suite expect an `IllegalArgumentException` naming the offending field and, where
relevant, the security. A rebalancer that trades on bad data is worse than one that refuses to run.

### RB-030 — Target percentages must sum to 100 · P1
**Steps:** Build an account whose targets sum to 80.
**Expected:** Rejected. Message states the requirement and the actual sum.
**Automated:** Yes.

### RB-031 — Current percentages may sum to less than 100 · P1
**Preconditions:** $100,000 account, $40,000 cash. AAA and BBB both target 50 / current 30 / $100.
**Expected:** Accepted. BUY 200 of each, funded from cash, ending cash $0.
**Notes:** Confirms ASM-04 — the shortfall against 100 is uninvested cash, not an error.
**Automated:** Yes.

### RB-032 — Current percentages may not exceed 100 · P1
**Expected:** Rejected. An account cannot hold 120% of itself.
**Automated:** Yes.

### RB-033 — A zero or negative price is rejected · P1
**Steps:** Attempt prices of 0, −1 and −150.50.
**Expected:** All rejected before any division occurs.
**Technique:** Error guessing plus boundary value at zero. **Automated:** Yes — one run per price.

### RB-034 — A missing price is rejected as a failed feed · P1
**Steps:** Supply a null unit price.
**Expected:** Rejected, with a message identifying it as a stale or failed price feed rather than
treating the absence as zero.
**Automated:** Yes.

### RB-035 — Target percentages outside 0 to 100 are rejected · P2
**Steps:** Attempt −1 and 101.
**Expected:** Both rejected, message naming `targetPct`.
**Automated:** Yes.

### RB-036 — Current percentages outside 0 to 100 are rejected · P2
**Steps:** Attempt −0.01 and 100.01.
**Expected:** Both rejected, message naming `currentPct`.
**Technique:** Boundary value just outside each edge. **Automated:** Yes.

### RB-037 — A duplicated symbol is rejected · P1
**Steps:** Build an account listing IBM twice.
**Expected:** Rejected naming IBM. Silently aggregating the two lines would double the order.
**Automated:** Yes.

### RB-038 — A blank symbol is rejected · P2
**Expected:** Rejected. A security with no identity cannot be traded.
**Automated:** Yes.

### RB-039 — Null and negative account fields are rejected by name · P1
**Steps:** Pass a null account, a null configuration, a null security list, negative total assets,
and negative cash.
**Expected:** Each rejected with a message naming the field. No bare `NullPointerException` reaches
the caller; a null security list specifically suggests using an empty list.
**Automated:** Yes — two test methods.

---

## TS-05 — Cash, funding and sequencing

### RB-040 — The assessment block raises exactly what it spends · P0
**Expected:** $9,900 raised, $9,900 spent, lowest running cash balance $0, block fundable.
**Automated:** Yes.

### RB-041 — Truncation does not guarantee fundability · P0 · **Finding**
**Preconditions:** $100,000 account, $0 cash. AAA target 60 / current 50 / **$1**. BBB target 40 /
current 50 / **$3**.
**Steps:** Rebalance with the default truncating policy.
**Expected:** BUY 10,000 AAA ($10,000) and SELL 3,333 BBB ($9,999). Net cash **−$1**. Not fundable.
**Notes:** The buy divides exactly and loses nothing to truncation; the sell divides into 3,333.33
and loses $1 of proceeds. Truncation is cash-neutral on Account ABC by coincidence, not by
construction. A production system needs a cash buffer or must size buys against realised proceeds.
**Technique:** Error guessing informed by the invariant analysis. **Automated:** Yes.

### RB-042 — Sequencing decides whether the block can be funded · P0 · **Finding**
**Steps:** Rebalance Account ABC twice, once emitting sells first and once buys first.
**Expected:** Identical quantities and identical net cash both times. Sells-first reaches a lowest
running balance of $0 and is fundable; buys-first reaches **−$9,900** and is not.
**Notes:** Arithmetically perfect orders can still be unexecutable. Correctness here is a property
of the sequence, not just of the numbers.
**Technique:** State transition over the running cash balance. **Automated:** Yes.

### RB-043 — An existing cash balance funds purchases · P1
**Preconditions:** $100,000 account, $50,000 cash. AAA target 50 / current 25 / $100. BBB target 50 /
current 25 / $250.
**Expected:** BUY 250 AAA and BUY 100 BBB, both funded from cash. No sells. Ending cash $0.
**Automated:** Yes.

### RB-044 — Trading conserves total assets · P0
**Steps:** Under each rounding policy, total the post-trade holdings and add ending cash.
**Expected:** $100,000 every time. Rebalancing moves value between lines; it never creates or
destroys it.
**Automated:** Yes — once per policy.

### RB-045 — No position is sold below zero · P1
**Expected:** Every post-trade holding value is zero or positive. The engine cannot short a position
it does not hold.
**Automated:** Yes.

---

## TS-06 — Business rules

### RB-050 — Full vesting makes the whole account tradeable · P1
**Expected:** Investable base $100,000; the assessment's two orders.
**Automated:** Yes.

### RB-051 — Partial vesting retargets every line · P1
**Preconditions:** Account ABC at **80% vested**.
**Expected:** Investable base $80,000, so each 20% target is worth 16% of the account. BUY 40 IBM,
SELL 44 MSFT, SELL 63 ORCL, SELL 8 AAPL, SELL 57 HD.
**Notes:** MSFT, AAPL and HD were exactly on target at full vesting and are now 4 points heavy. This
is the case that shows why "100% is vested" was worth writing down as an assumption (ASM-05).
**Automated:** Yes.

### RB-052 — Orders below the minimum notional are suppressed · P2
**Steps:** Rebalance Account ABC with a $10,000 floor, then with a $5,000 floor.
**Expected:** No orders at $10,000 (both are worth $9,900); both orders at $5,000.
**Technique:** Boundary value either side of the order value. **Automated:** Yes.

### RB-053 — Quantities are cut down to a whole round lot · P2
**Steps:** Rebalance Account ABC with a lot size of 10, then 100.
**Expected:** At 10, BUY 60 IBM and SELL 40 ORCL. At 100, no orders at all — neither line reaches a
full lot.
**Automated:** Yes.

### RB-054 — Variance inside the tolerance band is left alone · P1
**Steps:** Rebalance Account ABC with a 10-point band, then a 9.99-point band.
**Expected:** No orders at 10 points — a 10-point drift sits exactly on the band and does not breach
it. Both orders at 9.99.
**Technique:** Boundary value on the band itself, including the inclusive-versus-exclusive edge.
**Automated:** Yes.

### RB-055 — Zero variance is unreachable, so the band is the real criterion · P0 · **Finding**
**Expected:** Residual variance is greater than zero but no more than 0.5 points.
**Notes:** Hitting 20.00% exactly needs 66.6667 IBM shares. "Get to zero target variance" has to be
read as "get inside tolerance"; an acceptance test written as equality would fail a correct
implementation (AMB-02).
**Automated:** Yes.

---

## TS-07 — Precision and determinism

### RB-060 — Decimal arithmetic beats binary floating point · P1 · **Finding**
**Preconditions:** $100,000 account. AAA target 50 / current 43 / **$0.07**. BBB target 50 /
current 57 / $10.
**Expected:** BUY exactly **100,000** AAA. The gap is $7,000 and $7,000 ÷ $0.07 is exactly 100,000.
**Notes:** In a `double`, `7000 / 0.07` evaluates to 99999.99999999999, and truncating that loses a
share. The test asserts the correct decimal answer *and* pins the wrong binary answer, so it fails
immediately if anyone swaps a `BigDecimal` for a `double`.
**Automated:** Yes.

### RB-061 — Prices carrying cents are exact · P1
**Preconditions:** AAA target 20 / current 10 / **$150.37** on a $100,000 account.
**Expected:** BUY 66 AAA ($10,000 ÷ $150.37 = 66.50), notional exactly $9,924.42.
**Automated:** Yes.

### RB-062 — Fractional target percentages are honoured · P2
**Preconditions:** AAA target 33.25 / current 33 / $100. BBB target 66.75 / current 67 / $100.
**Expected:** BUY 2 AAA and SELL 2 BBB. A 0.25-point gap is $250, which is 2.5 shares at $100.
**Automated:** Yes.

### RB-063 — Rounding happens once, at the share count · P1
**Preconditions:** AAA target 40 / current 36.66667 / $1. BBB target 60 / current 63.33333 / $1.
**Expected:** BUY 3,333 AAA and SELL 3,333 BBB.
**Notes:** Rounding the notional to cents before dividing would cost a share. Intermediate rounding
is a classic source of compounding error.
**Automated:** Yes.

### RB-064 — The same input always gives the same output · P1
**Steps:** Rebalance Account ABC twice.
**Expected:** Identical orders and identical ending cash. No dependence on iteration order, hashing
or time.
**Automated:** Yes.

### RB-065 — The engine never mutates its input · P1
**Steps:** Rebalance Account ABC, then re-inspect the account object.
**Expected:** Securities, total assets and cash all unchanged. A caller can safely rebalance the
same account twice or share it across threads.
**Automated:** Yes.

---

## TS-08 — Metamorphic relations

These change an input in a way whose effect is known in advance, rather than asserting a fixed
expected value. They catch whole classes of defect that a single expected answer cannot.

### RB-070 — Rebalancing an already rebalanced account does nothing · P1
**Steps:** Rebalance Account ABC, apply the fills, then rebalance the resulting account.
**Expected:** No orders on the second pass.
**Notes:** IBM and ORCL are each 0.10 points out, worth $100, which will not buy a $150 IBM share or
a $220 ORCL share. Proves the engine settles instead of churning — a churning rebalancer generates
commission for no benefit.
**Automated:** Yes.

### RB-071 — No security ends further from target than it started · P0
**Expected:** For every line, post-trade absolute variance is at most the pre-trade value.
**Notes:** The single most important safety property. A rebalancer that can make drift worse is
worse than no rebalancer.
**Automated:** Yes.

### RB-072 — Scaling the account by ten scales quantities by ten · P2
**Steps:** Rebalance the evenly divisible account at $100,000 and again at $1,000,000.
**Expected:** Every quantity is exactly ten times larger.
**Automated:** Yes.

### RB-073 — Input order does not change the answer · P2
**Steps:** Rebalance Account ABC, then rebalance it with the securities listed in reverse.
**Expected:** Identical quantity per symbol and identical net cash.
**Notes:** Guards against a defect where an accumulator or a running cash balance leaks between
lines.
**Automated:** Yes.

---

## TS-09 — Non-functional

### RB-080 — A 5,000 line account rebalances inside two seconds · P2
**Preconditions:** $100,000,000 account, 5,000 equally weighted securities, each 0.01 points off
target.
**Expected:** 5,000 orders, elapsed time under two seconds.
**Automated:** Yes.

### RB-081 — Concurrent rebalances agree with the single threaded answer · P2
**Steps:** Run 64 rebalances of Account ABC across an 8-thread pool.
**Expected:** All 64 results identical to the single threaded result. The engine holds no state.
**Automated:** Yes.

### RB-082 — A wide account still conserves value exactly · P2
**Expected:** Across 5,000 lines, holdings plus cash still equal total assets to the cent. Rounding
error must not accumulate with position count.
**Automated:** Yes.

---

## TS-10 — Invariants over generated accounts

Seven properties checked against 1,400 randomly generated but structurally valid accounts — two to
eight securities, targets summing to exactly 100, currents summing to at most 100, prices from one
cent to $500. Seeds are fixed, so any failure replays the exact account that caused it.

| ID | Invariant | Priority |
|---|---|---|
| RB-090 | A line already on target is never traded | P0 |
| RB-091 | Order direction is always opposite to the variance sign | P0 |
| RB-092 | No position ends further from its target than it started | P0 |
| RB-093 | Whatever is left untraded is worth less than one share of that security | P1 |
| RB-094 | Holdings plus cash still equal total assets | P0 |
| RB-095 | No position is sold below zero | P1 |
| RB-096 | The same input always produces identical output | P1 |

**Automated:** Yes — seven seeds × 200 accounts.

---

## TS-11 — Data driven acceptance

### RB-100 — Account ABC matches the answer key · P0
**Data:** `src/test/resources/test-data/account-abc.csv`, one row per security with its expected
signed quantity.
**Expected:** Each row's quantity matches, and the fixture's own inputs match the row — so the
fixture and the answer key cannot drift apart unnoticed.
**Automated:** Yes — one run per row.

### RB-101 — Two-line scenario table · P1
**Data:** `src/test/resources/test-data/two-line-scenarios.csv`, nine scenarios covering exact
division, truncation on each side, sub-share gaps, full exit and entry, penny prices, prices with
cents, fractional percentages, and an already-on-target pair.
**Expected:** Each scenario's two quantities match.
**Notes:** Scenarios live in CSV so a business analyst can add one without touching Java, and the
same file can be reviewed by the team that owns the requirement.
**Automated:** Yes — one run per row.

---

## TS-12 — Manual verification (not automatable)

These are reconciliation and sign-off activities. Automation cannot discharge them because the thing
being checked is human agreement, not program behaviour.

### RB-120 — Confirm the rounding policy with the business owner · P0
**Steps:** Present AMB-01 and the RB-014 result to the product owner or front office. Get the
intended policy in writing.
**Expected:** A recorded decision. Until then, the $150 overdraft under round-half-up is an open
requirement defect, not a closed one.

### RB-121 — Agree the tolerance band · P0
**Steps:** Present AMB-02. Agree the band within which an account counts as rebalanced.
**Expected:** A number. "Zero" is not an achievable answer and must be challenged.

### RB-122 — Reconcile against the analyst's spreadsheet · P1
**Steps:** Independently recompute Account ABC by hand or in Excel and compare line by line with the
engine output.
**Expected:** BUY 66 IBM, SELL 45 ORCL, nothing else. Any difference is investigated before sign-off.
**Notes:** An independent oracle. Two implementations agreeing is worth far more than one
implementation agreeing with itself.

### RB-123 — Review the order blotter with a trader · P1
**Steps:** Show the generated block to someone who would actually trade it. Check that direction,
quantity and sequence read unambiguously.
**Expected:** No misreading of direction — this is where AMB-03 would bite a real user.

### RB-124 — Exploratory session on price feed behaviour · P2
**Steps:** Time-boxed session probing stale, missing, zero and negative prices arriving mid-run, and
prices that change between calculation and execution.
**Expected:** No silent bad orders. Findings feed back as new automated cases.
**Notes:** Charter-based exploratory testing; the output is new test cases, not a pass or fail.
