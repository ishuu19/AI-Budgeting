# LedgerAI — Dashboard & Insights Plan

Status: Home insights, ranking, 7-day strip, and a non-overlapping widget are in. Full Insights tabs and charts are still ahead.
Extends the Home dashboard and Insights screen. Uses data already stored (transactions with time, merchant, location, category, recurrence; budgets; bills; debts; goals) plus features in [plan-new-features.md](plan-new-features.md). UI follows [ui-brief.md](ui-brief.md).

---

## 1. Problem

Today's dashboard and Insights show **totals**: income, spent, savings rate, daily average, category split. These answer "how much", not "why" or "what next". Behaviour is where money is won or lost, so the dashboard should surface patterns, risks and one clear action.

---

## 2. Principles

1. **Insight, not statistic.** Every card states a finding and its consequence ("Weekends cost 2.3× weekdays"), not only a number.
2. **One action per insight.** Each card ends in a single next step (Set limit, Review, Move to savings).
3. **Calculated on-device.** Detection is deterministic and testable. AI only phrases and prioritises, within the existing fixed-schema rules.
4. **Non-judgemental.** Neutral wording. No shaming, no red for habits that are merely high.
5. **Few, ranked.** Home shows at most three insights. The rest live in Insights.
6. **Earned.** An insight appears only when there is enough data and the effect is meaningful.

---

## 3. Behavioural model

Money behaviour grouped into six questions the app can answer.

| Lens | Question | Why it matters |
|---|---|---|
| Rhythm | When do I spend? | Timing patterns are predictable and fixable |
| Triggers | What makes me overspend? | Mood, place, time and social context drive impulse spend |
| Leaks | Where does money disappear? | Small recurring costs outweigh rare big ones |
| Discipline | Do I follow my own plan? | Gap between budget and behaviour |
| Resilience | How safe am I? | Buffer, obligations, runway |
| Trajectory | Where am I heading? | Momentum and goal progress |

---

## 4. Insight catalogue

Each insight lists its signal, rule, threshold and action. Thresholds are defaults and tunable.

### 4.1 Rhythm

| Insight | Signal | Rule | Action |
|---|---|---|---|
| Weekend effect | Avg spend per weekday | Weekend day avg ≥ 1.5× weekday avg over 6+ weeks | Set weekend limit |
| Payday surge | Spend in 3 days after income vs rest of month | ≥ 1.4× | Schedule transfer to savings on payday |
| Late-night spending | Transactions 22:00 to 04:00 using `createdAt` | ≥ 3 in 2 weeks or ≥ 15% of discretionary | Add cool-down reminder |
| Month-end squeeze | Spend share in last 7 days vs first 7 | Skew in either direction ≥ 40% | Adjust daily guide |
| Peak day | Single heaviest weekday | Top day ≥ 25% of weekly spend | Pre-plan that day |

### 4.2 Triggers

| Insight | Signal | Rule | Action |
|---|---|---|---|
| Impulse buys | Unplanned discretionary purchases under 10 min of each other or with no matching budget line | ≥ 5 in 30 days | Enable 24 h wait on items above X |
| Place effect | Spend grouped by `location` | A place accounts for ≥ 20% of discretionary | Set place alert |
| Stress spending (opt-in) | Spend on days with heavy workload or unlogged gaps (Life Log, tasks, exams) vs light days | ≥ 1.3× on heavy days | Plan a lighter-spend routine |
| Social spend | Dining and entertainment clusters on evenings and weekends | Cluster size above personal norm | Set social budget |
| Streak breaker | A no-spend day followed by a spike ≥ 2× the average | Seen 3 times | Spread purchases |

### 4.3 Leaks

| Insight | Signal | Rule | Action |
|---|---|---|---|
| Latte factor | Merchant with ≥ 8 purchases per month under a small ticket | Annualised total shown | Set merchant cap |
| Subscription audit | Recurring merchants, same amount, regular interval | Detected automatically, flags unused or duplicate | Review, mark to cancel |
| Fee drain | Transactions tagged fees, interest, late charges | Any in last 60 days | Fix cause (autopay, reminder) |
| Price creep | Same merchant and category, amount rising ≥ 10% over 3 occurrences | Detected on recurring items | Review plan |
| Small-ticket total | Sum of purchases under a small threshold | ≥ 15% of spending | Show what it adds up to |

### 4.4 Discipline

| Insight | Signal | Rule | Action |
|---|---|---|---|
| Budget fit | Budget vs actual per category, last 3 months | Consistently over or under by ≥ 20% | Re-base the budget to reality |
| Pace | Spend progress vs month progress | Spend % exceeds month % by 10 points | Show daily allowance |
| Plan adherence | Share of days within the daily guide | Rolling 30 days | Raise or lower margin |
| Category drift | Category share shifted ≥ 8 points vs 3-month norm | Detected | Review category |
| Round-up | Hypothetical saving from rounding up each spend | Always available | Enable round-up to goal |

### 4.5 Resilience

| Insight | Signal | Rule | Action |
|---|---|---|---|
| Runway | Balance ÷ average fixed + essential spend | Weeks or months of cover | Set emergency target |
| Fixed-cost load | Bills + debt payments ÷ income | ≥ 50% warning, ≥ 65% alert | Review bills |
| Upcoming crunch | Bills, debts, speculations in next 14 days vs available | Shortfall predicted on a date | Move funds or defer |
| Income stability | Coefficient of variation of income | High variation | Build larger buffer |
| Debt pressure | Debt balance trend and interest cost | Interest paid this month shown | Prioritise highest rate |

### 4.6 Trajectory

| Insight | Signal | Rule | Action |
|---|---|---|---|
| Savings rate trend | Rolling 3-month savings rate | Direction and delta | Set auto-save |
| Goal ETA | Goal contribution pace | Date forecast versus target date | Adjust monthly amount |
| Net worth line | Assets minus debts over time | Monthly | None (context) |
| Year-end projection | Current pace extrapolated | Projected savings | Compare with goal |
| Best month | Month with the highest savings | Shown with what was different | Repeat the pattern |

### 4.7 Positive reinforcement
Streaks and wins are first-class insights: days under guide, no-spend days, bills paid early, goal milestones. Shown with equal prominence to warnings.

---

## 5. Scoring and ranking

Each insight gets a score; Home shows the top three.

```
score = impact × confidence × urgency × novelty
```
- **Impact**: monthly money effect, normalised to income.
- **Confidence**: sample size and consistency of the pattern.
- **Urgency**: time-sensitive items (upcoming crunch, bill due) rank higher.
- **Novelty**: decays for insights the user dismissed or saw repeatedly.

Rules: at most one insight per lens on Home; positive insight guaranteed one slot when available; dismissed insights hidden for 30 days; acted-on insights show a result later ("Saved $34 since").

---

## 6. Financial Health Score

A single summary number, 0 to 100, shown once on Home.

| Component | Weight | Measures |
|---|---|---|
| Spending control | 30% | Plan adherence, pace, budget fit |
| Savings | 25% | Savings rate and trend |
| Resilience | 25% | Runway, fixed-cost load, upcoming crunch |
| Obligations | 20% | Bills on time, debt trend, fees |

- Shown with the change since last month and the single largest drag on it.
- Tap opens a breakdown with each component and its fix.
- Not shown until 30 days of data exist.

---

## 7. Dashboard layout

### 7.1 Home (Money overview)

```
Safe today                $42          hero
Month ▓▓▓▓▓▓░░░░ 62%    Score 74 ▲3

[ Insight 1 ]  headline + one action
[ Insight 2 ]
[ Win ]

Soon        bills, debts, due tasks
Spend       7-day bars with guide line
Recent
```

| Block | Content |
|---|---|
| Hero | Safe-to-spend today (from Daily Spend Guide), month progress, health score |
| Insight stack | Top three ranked insights, swipe to dismiss, tap for detail |
| Soon | Next bills, debt payments, speculations |
| Spend strip | Last 7 days against the daily guide line |
| Recent | Latest transactions |

### 7.2 Insights screen

Segmented by lens: **Overview · Rhythm · Triggers · Leaks · Plan · Safety · Trend**

| Tab | Visuals |
|---|---|
| Overview | Health score breakdown, all active insights ranked, wins |
| Rhythm | Weekday bar chart, hour-of-day heatmap, payday curve |
| Triggers | Spend by place, heavy vs light days, impulse list |
| Leaks | Subscription list with annual cost, top small-ticket merchants, fees |
| Plan | Budget vs actual per category, pace line, adherence calendar |
| Safety | Runway gauge, fixed-cost load, 14-day cash outlook |
| Trend | Savings rate line, net worth, goal ETAs, year projection |

### 7.3 Insight detail sheet (`LSheet`)
Headline, evidence chart, the numbers behind it, what would change, and two buttons: the action and Dismiss.

---

## 8. Chart set

| Chart | Use | Notes |
|---|---|---|
| Weekday bars | Rhythm | Highlight the peak, dotted line at average |
| Hour heatmap | Rhythm, late-night | 7 × 24 grid, gold intensity |
| Pace line | Discipline | Spend vs ideal line, shaded overshoot |
| Category stacked trend | Drift | 6 months |
| Waterfall | Month review | Income to savings, fixed, flexible |
| Runway gauge | Resilience | Weeks of cover |
| Calendar heat | Adherence | Green under guide, gold near, red over |
| Sparkline | Tiles | No axes, last 30 days |

Chart rules: one accent colour (gold) on emerald boxes, danger red only for real overshoot, direct labels instead of legends, no 3D, no pie charts beyond five slices.

---

## 9. Data and architecture

### 9.1 Reuse existing
Transactions (`createdAt` for hour, `merchant`, `location`, `category`, `isRecurring`), budgets, bills, debts, goals, tasks, routines.

### 9.2 New inputs
| Input | Source | Purpose |
|---|---|---|
| Daily guide history | `spend_guide_days` | Adherence, pace |
| Speculations | `spend_speculations` | Upcoming crunch |
| Mood tag (optional, 1 tap on a transaction) | New nullable column | Stress and emotion triggers |
| Planned flag (optional, on a transaction) | New nullable column | Impulse detection |
| Workload signal | Tasks due, exams, Life Log gaps | Stress spending (opt-in) |
| Merchant normalisation | New `merchant_alias` table | Group "STARBUCKS #123" with "Starbucks" |

### 9.3 Engine
- `InsightEngine` runs a list of `InsightDetector`s, each pure and unit-tested with fixture data.
- Output is a typed `Insight(id, lens, headline, evidence, impact, confidence, urgency, action)`.
- Results cached in Room, recomputed on transaction change (debounced) and daily by `InsightDailyWorker`.
- AI receives the top candidates and returns phrasing plus ordering through the existing validated `INSIGHT` schema. If AI is unavailable, templated text is used.
- No raw transactions leave the device beyond what ContextBuilder already allows.

---

## 10. Interaction

| Gesture | Result |
|---|---|
| Tap insight | Detail sheet |
| Swipe | Dismiss for 30 days |
| Long-press | "Not useful" feeds ranking |
| Action button | Performs the fix in one step (opens pre-filled sheet) |
| Tap hero | Money → Today |

Notification digest: one weekly summary and time-sensitive alerts only (upcoming crunch, pace breach). Never more than one insight notification per day.

---

## 11. Build phases

| Phase | Deliverable |
|---|---|
| D1 | `InsightEngine`, detector interface, scoring, caching, unit-test fixtures |
| D2 | Rhythm and Leaks detectors (need only existing data) |
| D3 | Discipline and Resilience detectors, health score |
| D4 | Home redesign: hero, insight stack, spend strip |
| D5 | Insights screen tabs and chart set |
| D6 | Merchant aliases, subscription detection, optional mood and planned tags |
| D7 | Trigger detectors (place, workload), Trajectory detectors, goal ETA |
| D8 | AI phrasing and ranking, weekly digest, feedback loop (dismiss, not useful, acted-on results) |
| D9 | Widget integration, QA with seeded 12 months of data |

D1 to D5 deliver most of the value using data already collected. D3 and the hero depend on the Daily Spend Guide (phase 14 in [plan-new-features.md](plan-new-features.md)); until then pace is used.

---

## 12. Quality bar

- Every detector has fixture tests for firing, not firing and borderline cases.
- Minimum data: 3 weeks for Rhythm, 6 weeks for Triggers, 8 weeks for Trajectory. Below that, the card is replaced by a "Collecting" row with progress.
- No insight shown with fewer than 5 supporting transactions.
- Wording reviewed for neutrality; no insight uses "wasted", "bad" or "should".
- Numbers always match the underlying lists (tap-through shows the exact transactions).
- Performance: engine run under 300 ms for 10,000 transactions, off the main thread.

---

## 13. Risks

| Risk | Mitigation |
|---|---|
| Insight overload | Hard cap of three on Home, ranking, dismiss memory |
| False patterns on little data | Minimum thresholds and confidence scoring |
| Feels judgemental | Neutral copy, wins shown equally |
| Inaccurate merchant grouping | Alias table, user-correctable |
| Stress and location signals feel invasive | Opt-in, on-device only, off by default |
| Health score oversimplifies | Always show breakdown and largest drag |
