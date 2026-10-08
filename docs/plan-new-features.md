# LedgerAI — New Features Plan

Status: Implemented through Phase 19 (local). Room v6, Life tabs, plan/habit alarms, focus FGS, spend/check-in/note workers, calendar AI suggest, note nudges, job share. Not done: location trail, Supabase sync (Phase 20).
Builds on the existing stack in [PLAN.md](../PLAN.md): Kotlin, Compose, Room (offline-first), Hilt, WorkManager, Supabase, `ai-proxy` Edge Function. UI follows [ui-brief.md](ui-brief.md).

---

## 1. Scope

| # | Feature | Summary |
|---|---|---|
| F1 | Study Planner | Enter a topic, get the best free slots, start a focus session with a timer. Nothing is written to the calendar. |
| F2 | Habit Sessions | Exercise, reading, prayer, etc. Created with AI. Escalating nudges before start. |
| F3 | Note Nudges | AI scans free-form notes and proposes notifications. |
| F4 | Calendar-aware AI | AI reads calendar, classes and tasks and drafts items for what is missing. |
| F5 | Daily Spend Guide | AI recommends a safe amount to spend each day, factoring behaviour, budget, upcoming bills and user speculations. |
| F6 | Leave-by and Life Log | Leave-by reminders (time + location), location trail, 3-hourly activity check-ins with gap marking. |
| F7 | Job Tracker | Paste job names and links, applications are logged as applied today. |

---

## 2. Principles

1. **AI proposes, user confirms.** This keeps the rule in PLAN.md §1.6: AI has no direct write access. To honour "add automatically", F4 gets an opt-in setting that auto-accepts drafts from trusted sources (calendar, classes). Everything else needs one tap.
2. **Planning happens on-device.** Slot finding, nudge timing and spend maths are deterministic Kotlin. AI is used only to interpret text and phrase results, which keeps tokens low.
3. **Plans are not calendar events.** Study blocks and habit sessions live in their own tables and screens. They read from the calendar but never write to it.
4. **Offline first.** Every feature works without network except AI interpretation, which queues.
5. **Privacy.** Location and activity data stay in Room by default. Sync is opt-in per feature. AI receives summaries only, never raw location trails.

---

## 3. Navigation

Existing tabs: Home · Money · Voice · Life · You.

| Feature | Home | Entry points |
|---|---|---|
| F1 Study Planner | **Life → Plan** | Home "Next up" card, Voice intent "plan study for…" |
| F2 Habit Sessions | **Life → Plan** | Same list as F1, filtered by chip |
| F3 Note Nudges | Life → Notes | Per-note action "Nudge" |
| F4 Calendar-aware AI | Life → Calendar | "Suggestions" sheet, Ask tab |
| F5 Daily Spend Guide | **Money → Today** | Home KPI tile, evening notification |
| F6 Leave-by | Life → Calendar and Tasks | Task/event sheet field "Leave by" |
| F6 Life Log | **Life → Log** | Check-in notification, Home "Gaps" badge |
| F7 Job Tracker | **Life → Jobs** | Share-sheet target, clipboard prompt |
| Focus mode | Full screen over everything | Start on any plan block |

Life becomes a segmented screen: **Calendar · Plan · Log · Jobs**. Notes and Tasks remain reachable as today.

---

## 4. Feature specs

### F1 Study Planner

**Flow**
1. Plan → Add → *Study*. Fields: Topic, Hours needed, Deadline (optional), Sessions length (default 50 min).
2. Planner reads calendar events, classes, tasks, alarms, and the user's waking hours.
3. Free-block finder returns ranked slots. Result sheet shows 3 to 5 options, each as a time range with a one-line reason (e.g. "After lecture, 2h free").
4. User picks slots. They become **Study blocks** in Plan. Alerts are created per block.
5. At start time: notification → Focus mode.

**Free-block finder**
- Inputs: busy intervals (events, class occurrences, tasks with times), sleep window, meal and prayer buffers, minimum block 25 min.
- Scoring: fits within preferred hours, before deadline, spread across days, avoids back-to-back fatigue, respects max daily study load.
- Splits total hours into sessions with 10 min breaks.
- Re-plans when calendar changes: affected blocks are flagged "Moved?" with a suggested replacement.

**Screens**
- *Plan list*: grouped Today / Tomorrow / Later. Row: title, time, duration, type chip.
- *New study sheet* (`LSheet`).
- *Slot picker sheet*.
- *Block detail sheet*: Start, Reschedule, Skip, Delete.

**Focus mode**
- Full-screen timer with topic, elapsed ring, pause, end.
- Presets: Pomodoro (25/5), Deep (50/10), Custom.
- Do-not-disturb request (optional), keeps screen on, ongoing foreground notification.
- On finish: log actual minutes, mark block Done, prompt one-tap "Add next block".

### F2 Habit Sessions

**Flow**
1. Plan → Add → *Habit*, or type/speak "gym 6pm Mon Wed Fri". AI parses to a draft: title, category, days, time, duration.
2. Confirm sheet. Category sets defaults (Exercise, Reading, Prayer, Custom).
3. Schedule creates **nudge chain**: T-30, T-25, T-20, T-15, T-10, T-5, T-0.
4. Nudges repeat until the session is started or skipped.

**Nudge rules**
- Each nudge has actions: **Start**, **Snooze 5**, **Skip today**.
- Starting or skipping cancels the remaining chain.
- Escalation: wording and channel importance rise as start approaches (quiet, default, high).
- Uses exact alarms (`AlarmManager`) for reliability. WorkManager is not precise enough for 5-minute cadence.
- Respect a Quiet Hours setting, except for prayer and user-flagged "Always".

**Tracking**: streak, weekly completion, per-habit history in the detail sheet.

### F3 Note Nudges

**Flow**
1. Notes → any note → *Nudge*, or global "Scan notes" in settings (daily).
2. AI reads the note text and returns zero or more proposals: message, suggested time, reason.
3. Proposals appear in **Suggestions** (badge on Notes). Accept schedules a notification, Dismiss teaches the model what to ignore.

**Constraints**
- Only notes the user marks "Nudge-enabled" are sent to AI (default off per note, one global toggle).
- Maximum 3 proposals per note.

### F4 Calendar-aware AI

- ContextBuilder gains a **schedule slice**: next 14 days of events, class occurrences, tasks, plan blocks, as compact lines.
- Capabilities: answer "when am I free", detect conflicts, draft tasks/reminders for things implied by notes or voice that are not yet on the calendar, avoid duplicates by matching title + time against existing items.
- Output goes through the existing draft → validator → confirm path.
- Setting **Auto-add from calendar**: when on, drafts that mirror an existing calendar or class entry (i.e. already exists) are created silently with an undo toast. All other drafts still need confirmation.

### F5 Daily Spend Guide

**Output**: one number per day: *Safe to spend today*, with a status (Under, Near, Over) and the reasoning in a short list.

**Calculation (deterministic)**
```
Remaining budget (period)
 − committed upcoming bills and debt payments
 − planned large payments (speculations)
 − savings goal contribution
 = Spendable pool
Spendable pool ÷ days left, weighted by weekday spending profile
 → Daily guide, then reduced by a safety margin (default 15%)
```
- **Behaviour profile**: average spend by weekday and category over the last 8 weeks, flexible vs fixed split.
- **Safety margin**: the guide is intentionally conservative so that exceeding it on one day still leaves the period within budget. The app shows "Buffer" as the gap between guide and hard limit.
- **Carry-over**: unspent amount rolls forward at 50%. Overspend is redistributed across remaining days, never more than a set cap per day.
- AI role: explains the number and suggests one adjustment (e.g. "Dining is 40% above usual this week"). It does not compute the figure.

**Speculations**
- Money → Today → *Add speculation*. Fields: Label, Amount, Expected date (or month), Confidence (Low / Medium / High), Direction (Expense / Income).
- Weighting by confidence: High 100%, Medium 60%, Low 30%. Incomes are weighted lower than expenses to stay conservative.
- Shown in Forecast alongside bills.

**Screens**: *Today* (hero number, progress against guide, today's spend rows), *Speculations* list, *Guide settings* (margin, carry-over, weekly reset day).

**Notifications**: morning "Safe today: X". Mid-day alert at 80% of guide. Evening recap.

### F6 Leave-by and Life Log

**Leave-by**
1. In a task or event sheet, add **Place** (existing `PlaceSearchService`) and toggle *Leave-by*.
2. App computes travel time (maps/routing API, with a fallback fixed buffer if offline) and sets a reminder: *Leave by 8:40*.
3. Reminder refreshes using current location and traffic at T-60 and T-15 when permission is granted.
4. Notification actions: **On my way**, **Snooze 5**, **Running late** (informs only in-app).

**Life Log**
- **Check-in every 3 hours** (configurable, within waking hours). Notification: "What did you do 12:00 to 15:00?" with quick-reply, voice reply, and suggestion chips built from calendar, tasks and location.
- **Gap handling**: any unanswered window becomes a **red gap** in the Log timeline. Tapping a gap opens the entry sheet pre-filled with the time range, calendar items and visited places.
- **Location trail**: periodic low-power samples, clustered into **visits** (place, arrival, departure). Visits are suggested as entries ("At Library 13:10–15:20").
- **Timeline screen**: day view, entries as blocks, gaps in red, day navigation, total logged vs unlogged hours.
- **Daily summary**: AI recap of time use against plan, optional.

**Permissions**
- Foreground location first. Background location only after user opts in on a clear rationale screen.
- Per-feature switches: Leave-by, Trail, Check-ins. Each can be off independently.
- Retention setting for raw location points (default 30 days, visits kept).

### F7 Job Tracker

**Capture**
- Paste into the *Quick add* field: company, role, link, notes. One line or several. AI parser splits multiple entries and extracts company, title, URL, source site.
- Share-sheet target: sharing a job link from the browser creates a draft.
- Every new entry defaults to **Applied, today**. Date is editable.

**Pipeline statuses**: Applied → Screening → Interview → Offer → Rejected / Withdrawn.

**Screens**
- *Jobs list*: filter chips by status, search, sort by date. Row: role, company, applied date, status chip.
- *Detail sheet*: link (open), notes, status, dates, contact, follow-up date.
- *Stats strip*: applied this week, response rate, interviews.
- Follow-up reminders: default 7 days after applying, editable.
- Duplicate detection by URL or company + role.

---

## 5. UI plan

Style: royal green boxes, gold accents, white page, per [ui-brief.md](ui-brief.md). Use only existing `L*` components. Add flows via `LSheet`. Titles one word. No helper text.

### 5.1 Screens

| Screen | Title | Key elements |
|---|---|---|
| Life container | Life | Segmented tabs: Calendar, Plan, Log, Jobs |
| Plan | Plan | `LHero` next session countdown, grouped rows, FAB Add |
| Add sheet | Add | Two options: Study, Habit |
| Study sheet | Study | Topic, Hours, Deadline, Find time |
| Slots sheet | Times | Ranked options, Select, Confirm |
| Focus | Focus | Timer ring, topic, Pause, End |
| Log | Log | Day timeline, red gaps, check-in FAB |
| Check-in sheet | Check-in | Range label, text/voice field, chips, Save |
| Jobs | Jobs | Status chips, list, FAB Add |
| Job sheet | Job | Paste field, parsed preview rows, Save |
| Today (Money) | Today | `LHero` safe-to-spend, progress, spend rows |
| Speculation sheet | Speculate | Label, Amount, Date, Confidence |
| Suggestions | Suggest | List of AI drafts, Accept / Dismiss |

### 5.2 Core flows

**Study**
```
Plan → Add → Study → fill topic/hours → Find time
  → Times sheet (pick) → Confirm → blocks appear in Plan
  → notification at start → Focus → End → logged
```

**Habit**
```
Plan → Add → Habit (type or speak) → AI draft → Confirm
  → nudges T-30…T-0 every 5 min → Start → Focus  |  Skip
```

**Spend**
```
Notification "Safe today" → Money → Today
  → add spend (voice or sheet) → progress updates
  → Add speculation → guide recalculates
```

**Check-in**
```
Notification (every 3h) → reply inline or open sheet
  → Save   |  ignored → red gap in Log → tap gap → fill later
```

**Job**
```
Share link or paste text → Job sheet (parsed rows) → Save
  → appears as Applied today → follow-up reminder in 7 days
```

**Leave-by**
```
Task/Event sheet → Place → Leave-by on → reminder computed
  → T-60 / T-15 refresh → "Leave by 8:40" → On my way
```

### 5.3 States and edge cases
- Empty: `LEmpty` with two or three words ("No sessions", "No gaps", "No jobs").
- No free slot found: show nearest options with the conflict named.
- Permission missing: inline row with a single **Allow** button, feature keeps working in reduced mode.
- Offline: AI actions queue, deterministic features run normally.

---

## 6. Data model (Room, mirrored to Supabase with `user_id`, `updated_at`, `deleted_at`)

| Table | Key fields |
|---|---|
| `plan_blocks` | id, kind (study/habit), title, topic, start, end, status, source_plan_id, actual_minutes |
| `study_plans` | id, topic, hours_total, deadline, session_len, status |
| `habits` | id, title, category, days_mask, start_time, duration, nudge_enabled, quiet_override |
| `habit_logs` | id, habit_id, date, outcome (done/skipped/missed), minutes |
| `focus_sessions` | id, block_id, started_at, ended_at, preset |
| `nudge_proposals` | id, note_id, message, suggested_at, state |
| `spend_speculations` | id, label, amount, direction, expected_date, confidence |
| `spend_guide_days` | date, guide_amount, spent, buffer, margin_used |
| `leave_rules` | id, ref_type, ref_id, place_id, lat, lng, travel_minutes, buffer_minutes |
| `location_points` | id, ts, lat, lng, accuracy (local only by default) |
| `visits` | id, place_name, lat, lng, arrived_at, left_at |
| `activity_entries` | id, start, end, text, source (manual/voice/suggested), visit_id |
| `checkin_windows` | id, start, end, state (answered/gap) |
| `job_applications` | id, company, title, url, source, status, applied_on, follow_up_on, notes |

Room version bump with migrations. Supabase RLS `user_id = auth.uid()` on all synced tables. `location_points` excluded from sync.

---

## 7. AI contracts

All responses use fixed schemas validated on-device, per PLAN.md §3.2.

| Intent | Input slice | Output |
|---|---|---|
| `parse_study` | user text | topic, hours, deadline |
| `parse_habit` | user text | title, category, days, time, duration |
| `scan_notes` | opted-in note text + now | proposals[] (message, time, reason) |
| `parse_jobs` | pasted text | applications[] (company, title, url) |
| `explain_guide` | computed guide + deltas | headline, up to 3 reasons |
| `suggest_schedule` | 14-day schedule slice | drafts[] (type, title, time) |
| `summarize_day` | entries + plan | summary, unlogged hours |

The free-block finder, nudge scheduler and spend guide never call AI.

---

## 8. Background work and permissions

| Need | Mechanism |
|---|---|
| Nudge chains, leave-by, check-ins | `AlarmManager` exact alarms (`SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` as appropriate) |
| Daily guide, note scans, replans, follow-ups | WorkManager |
| Focus timer | Foreground service with ongoing notification |
| Location trail | Foreground service type `location` or fused provider batching, opt-in |
| Boot and timezone changes | Receiver to rebuild alarms (extends existing boot handling) |

New notification channels: Plan, Nudges, Check-in, Spend, Jobs. Each user-controllable. New permissions requested only in context: notifications, exact alarms, fine location, background location, microphone (existing).

---

## 9. Build phases

| Phase | Deliverable | Depends on |
|---|---|---|
| 10 | Schema, DAOs, repositories, migrations, nav shell for Life segments | none |
| 11 | Free-block finder, Plan screen, Study flow, alerts | 10 |
| 12 | Focus mode and timer service | 11 |
| 13 | Habit sessions and nudge chains | 10, 12 |
| 14 | Daily Spend Guide and speculations | 10 |
| 15 | Job Tracker with paste/share capture | 10 |
| 16 | Calendar-aware AI context, suggestions, auto-add setting | 11, 13 |
| 17 | Note Nudges | 16 |
| 18 | Leave-by | 10 |
| 19 | Life Log, check-ins, gaps, location trail | 18 |
| 20 | Sync, RLS, settings, QA on device (battery, exact alarms, OEM limits) | all |

Order rationale: ship the highest-value, lowest-risk features first (F1, F2, F5, F7). Location features go last because of permission, battery and privacy cost.

---

## 10. Risks

| Risk | Mitigation |
|---|---|
| OEM battery killers drop alarms | Exact alarms, boot rebuild, in-app health check with link to battery settings |
| Notification fatigue from nudges and check-ins | Quiet hours, per-channel controls, auto-pause after 3 consecutive ignores with a prompt |
| Background location rejection or battery drain | Opt-in, visit-based sampling, low-power provider |
| Spend guide seen as authoritative | Show buffer and inputs, margin adjustable |
| AI cost and latency | Deterministic core, compact context, queued requests |
| Auto-add conflicting with confirm-first rule | Scoped to items that already exist in the calendar, undo always available |

---

## 11. Open decisions

1. Routing provider for travel time (Google Directions vs on-device estimate).
2. Check-in cadence: fixed 3 hours or aligned to waking hours.
3. Whether Job Tracker syncs link previews (needs a fetch) or stores pasted text only.
