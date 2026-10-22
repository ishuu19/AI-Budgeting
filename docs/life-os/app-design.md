# LedgerAI rebuild: app design

Status: **interactive phone screens started** in `app/src/main/java/com/ledgerai/app/presentation/paper/` (launcher **Ledger screens**). Sample data only. The working ledger is unchanged. `web/` is removed. `.design/` and `design-system/ledgerai/` were rebuilt for these screens instead of deleted.
Date: 2026-10-22.
Replaces: the earlier untracked draft of this file and the web mockups it was based on.

Scope: restructure the whole Android app around one feature list, add eight new features, and remove everything web-related. The stack does not change ([ADR 0001](adr/0001-evolve-existing-android.md)). The visual language does not change (existing Compose theme, deep blue and one amber mark). Data rules stay [data-model.md](data-model.md) and the architecture rules in `CLAUDE.md`.

Screen layouts, wireframes and the visual system are in [screens.md](screens.md).

How to read this: section 1 is what the app really contains today (checked against the code). Section 2 lists what is wrong. Section 3 is the single feature list with one home per feature. Sections 4 to 13 design every screen. Sections 14 to 17 are the shared machinery (speech, reminders, data, assistant). Section 18 is web removal. Section 19 is the build order with a check per step. Section 22 lists user-behaviour edge cases for each part, and whether a unit test already covers them.

---

## 0. Decisions to confirm before building

Defaults are what this plan assumes. Each one that removes a built feature is marked.

| # | Question | Default in this plan | Why |
|---|---|---|---|
| D1 | Keep tasks and alarms inside `calendar_events`, or move them to their own tables? | **Keep.** Split them in the UI only. | The single table already honours "one brain". Moving them means a data migration, a server change, widget changes and sync risk, for no user-visible gain. |
| D2 | One speech path (the phone's own speech screen) everywhere, or keep the in-app offline engines (Vosk, Whisper, Sherpa, Zipformer)? | **One path through a single `SpeechInput` interface.** Phone speech is the default. The offline engines stay behind a Settings switch "Offline voice" until measured, then are removed if unused. | Five transcribers and a separate Voice Log screen are why one sentence can land in five places. The offline engines cost model downloads and code. Removing them is a decision for you, not me. |
| D3 | Retire the Life Log (check-in windows, gap nags, `CheckinWorker`)? **Removes a built feature.** | **Retire the screen and the nag worker. Keep the rows.** | Its job is split between App time (which apps), Achievements (what I finished) and Focus (time I chose to spend). |
| D4 | Birthday "celebration time" default | **00:00 (midnight).** Day-level reminders fire at a separate reminder hour, default **09:00**. | Section 9.1 shows why measuring "5 hours before" from 09:00 would wake you at 04:00. |
| D5 | Show birthdays and trips on the agenda? | **Off by default**, a switch in Settings → Plan. They always show on Home as one "Coming up" line. | You asked for birthdays to be outside the calendar. |
| D6 | Daily quote (`QuoteDailyWorker`, `quotes.json`, quote line on the widget) | **Retire.** **Removes a built feature.** | Nothing else in the app depends on it and it adds a worker and a widget line. |
| D7 | Password backup | **Encrypted export file with a passphrase. No cloud copy.** | The Room database is excluded from Android backup and device transfer (`data_extraction_rules.xml`), so a new phone starts empty. Without an export the passwords are lost on every phone change. |

---

## 1. What the app contains today

Checked in the code on 2026-10-22: `AppNavigation.kt`, `Segments.kt`, `LedgerDatabase.kt` (Room v21, 41 entities), `AndroidManifest.xml`, `worker/`, `widget/`, `supabase/`.

### 1.1 Screens

The bottom bar is **Home · Plan · Speak · Money · Life**. Home is the chat screen (`Screen.Chat`, labelled Home). Segment lists below come from `Segments.kt`.

| Tab | Segment | What it does today |
|---|---|---|
| Home | (chat) | A chat transcript. Speak or type, the voice pipeline turns words into cards. Cooking, outfit and shopping questions are answered here (`AskService`). Chat threads are stored with `origin = 'home'`. |
| Plan | Calendar | Month grid and agenda over `calendar_events` (kinds `EVENT`, `TASK`, `EXAM`, `CLASS`, `ROUTINE`, `ALARM`, `PERSONAL`), study plan blocks, habits, recurrence, up to 10 reminders per event, leave-by times, schedule import from CSV or image. |
| Plan | Tasks | Task list. Tasks are calendar rows of kind `TASK`. |
| Plan | Notes | Opens the Notebook: Board (clips), Chats, and a Notes tab with the classic notes. |
| Plan | Jobs | Job application tracker. Share a job post into the app. Screenshot text is read on the phone. |
| Plan | Log (under More) | Life log: check-in windows, "what did you do" entries, gap nags. |
| Money | Overview | Month summary, charts, safe-to-spend link, insight card. |
| Money | Spend | Transactions: search, filters, add sheet. Receipt icon opens the Receipts list. |
| Money | Budget (`Plan`) | Budgets per category, goals, forecast chart. |
| Money | Owed | Debts and bills together (`OpenKind.Bill` and `Debt` both land here). |
| Money | Receipts | Saved receipts, scan new, review screen. Creates no transaction until confirmed. |
| Life | Pantry | Inventory items, expiry, shopping list. |
| Life | Wardrobe | Garments, outfit suggestions (weather). |
| Life | People | People, memories, moments (with photo), promises. |
| Life | Household | Members, shared lists. |

Routes outside the tabs: Today (full-day view), Voice Log (the old multi-mode voice screen), Insights, Safe to spend, Search, Focus, Ask (full-screen assistant), Receipt review, Person, Subscriptions, Settings, Notebook.

### 1.2 Ways in from outside the app

| Entry | What it opens |
|---|---|
| Assistant gesture (`VoiceInteractionService`) | Quick capture sheet |
| Quick Settings tiles | Capture tile, "Add copied text" clip tile |
| Launcher shortcuts | Speak, Snap, Notebook |
| Share → image | Quick capture in camera mode |
| Share → text | Clip capture, and separately "Save as job" |
| Text selection menu | "Ledger AI" notebook panel and "Add to Notebook" clip capture |
| Floating bubble | Clip capture |
| Three Glance widgets | Home (date, safe-to-spend, next items, tasks, habits, quote), quick actions, focus. Their mics open the phone speech screen. |
| Notifications | Event reminders, debt reminders, leave-by, plan blocks, habit nudges, alarms (full-screen ring) |

### 1.3 Background work

Exact alarms: event reminders, debt reminders, alarms, leave-by, plan blocks, habit nudges, and a 15-minute pulse (`AppPulse`) that runs bills, check-in, budget, morning spend, insight and note scan checks. WorkManager periodic jobs: sync, bill, budget, check-in, insight, quote, clip cleanup, clip digest, note scan, widget refresh. One-time: capture upload, clip filing, notebook ask.

### 1.4 Data

41 Room tables in these groups: money (`transactions`, `budgets`, `debts`, `goals`, `bills`, `subscriptions`, `receipts`, `receipt_lines`, `spend_speculations`, `spend_guide_days`), time (`calendar_events`, `event_reminders`, `study_plans`, `plan_blocks`, `habits`, `habit_logs`, `focus_sessions`, `nudge_proposals`, `leave_rules`), words (`notes`, `clips`, `chat_threads`, `chat_messages`, `voice_history`), people and house (`people`, `interactions`, `commitments`, `memories`, `households`, `household_members`, `items`, `shopping_lists`, `shopping_items`, `wardrobe_items`, `outfits`), work (`job_applications`), files (`media_assets`), life log (`activity_entries`, `checkin_windows`), assistant (`assistant_actions`, `event_log`).

Three facts that shape the plan:

1. **Server sync is only live for migrations 001 to 011.** `supabase/planned/` holds 012 to 021 (people, households, inventory, receipts, subscriptions, wardrobe, media, clips, notebook), all unapplied. Those tables exist on the phone only today. New synced tables must wait behind them (section 19, step 0).
2. **`location_points` and `visits` have a table and a DAO but no collector anywhere in the code.** They are dead schema. They are not a feature and are not in the feature list.
3. **The Room database is excluded from cloud backup and device transfer.** Anything that is not synced is lost on a new phone.

### 1.5 Assistant spine

`ActionPlan`, `Risk` (LOW, MEDIUM, HIGH), `ActionRegistry`, `Orchestrator`, `Executor`, `PlanParser` exist. Registered action types today: `capture.*` (file food, garment, person, receipt, log expense, stock from receipt, track subscription) and `clip.*` (file bill, debt, event, job, memory, note, place, shopping, task, transaction). Voice parsing is rules first, a fast model second (`QuickParse`, `RuleEngine`, `AiProviderRouter`), with `ai-proxy` as the release path.

---

## 2. What is wrong

### 2.1 Repetition in the current app

| Clash | Today | Fix in this plan |
|---|---|---|
| Three answers to "what is next" | Home chat, Today route, Calendar agenda | Home answers "what needs me". Plan → Agenda answers "what is on date X". Today route is removed. |
| Five ways to speak | Widget mic, bottom mic, Voice Log (4 modes), Home chat, Ask | One speech path, one suggestion sheet (section 14). |
| Notes in three products | Notes, Notebook board, chat threads | Vault holds all written things. Chats become Ask history. |
| Money has too many doors | Overview, Spend, Budget, Owed, Receipts, plus routes for Insights, Forecast, Safe to spend, Subscriptions | Four segments. Insights, forecast and safe-to-spend become sections inside Overview. |
| Bills vs subscriptions vs debts | Three screens, three layouts | One "Pay" segment, one row layout, three filters. Three tables stay. |
| Plan holds a notes door and a jobs door | Notes and Jobs are not planning | Notes go to Vault. Jobs go to Library. |
| Life log vs habits vs focus | Four answers to "how did I spend myself" | Habits and Focus stay. Life log retires (D3). App time and Achievements are new and distinct. |
| Widget has a quote, habit dots, log gaps, jobs count | Widget is a second app | Widget cut to what answers "what needs me" (section 13). |

### 2.2 Logical errors in the requested features (found while planning)

These are mistakes in how the features were first described. Each is resolved in the section noted.

| Error | Why it fails | Resolution |
|---|---|---|
| Birthday reminders "5 hrs, 30 mins, 10 mins" before a birthday that is only a *date* | There is no instant to count back from. Using 09:00 puts "5 hours before" at 04:00 | A birthday gets a celebration time (default 00:00). The three clock reminders count back from it (19:00, 23:30, 23:50 the evening before). The five day-level reminders fire at the reminder hour (09:00). §9.1 |
| The ladder ends 10 minutes *before* and nothing happens *at* the time | The last signal is a warning for a thing that then goes unmarked | A ninth switch, **At the time**, default on, added by this plan. §9.1 |
| One exact alarm per reminder | 100 birthdays × 9 reminders is 900 alarms. Android allows an app 500 pending alarms and throws past that | One rolling alarm for the earliest due reminder across the whole app. §15 |
| Birthday stored as a name *and* a person | Two copies of a name drift | A birthday always points at a person. Adding a birthday for a new name creates the person. §9.1 |
| Appointments as a table beside the calendar, with a "block this hour" copy back into the calendar | A second record of the same meeting. Editing one leaves the other stale | An appointment is a calendar row of a new kind with a person and a status. One table, two views. §9.4 |
| "Most important information" kept in plain notes and files | A passport scan or a PIN list in a normal folder is searchable, synced and readable by the assistant | Folders can be **Locked**: encrypted, never uploaded, never indexed, never sent to the model. §11 |
| "Password tracking" with no way out | Database is excluded from backup and device transfer. New phone, no passwords | Passphrase-protected export and import (D7). §10.2 |
| Passwords visible to Notebook screen reading | The notebook panel reads the screen when called. If called on the Passwords screen it could read secrets | That screen is marked secure and the screen reader refuses on it. §10.2 |
| App time label lookup | Android 11+ hides other apps from `PackageManager` unless declared | A launcher-intent `<queries>` entry, not the Play-restricted all-packages permission. §12 |
| Trip "tracking dates" growing into budgets, itineraries and bookings | Scope creep into Money and Calendar | Trips are dates, a place and notes. Spend linking is optional and later. §9.3 |
| Achievements mixed with goals, habits, streaks | A second progress system | A plain log. No points, streaks or reminders. §9.2 |

---

## 3. Master feature list

One row per product feature. The **Home screen** column names the only place that creates or edits the record. Everywhere else shows or links. If something is not in this table, it is not a feature.

Status: **Keep** (exists, unchanged in behaviour), **Move** (exists, new place), **Merge** (exists, folded into another), **New**, **Retire**.

### 3.1 Features

| ID | Feature | Status | Home screen | Table |
|---|---|---|---|---|
| F01 | Briefing (next item, safe-to-spend, coming up) | Merge (Home chat, Today, Dashboard) | Home | reads only |
| F02 | Agenda (one day, month grid) | Merge (Calendar, Today) | Plan → Agenda | `calendar_events` |
| F03 | Calendar blocks (event, class, exam, routine) | Keep | Plan → Agenda | `calendar_events` |
| F04 | Tasks | Move | Plan → Tasks | `calendar_events` kind `TASK` |
| F05 | **Appointments** | **New** (new kind) | Plan → Appointments | `calendar_events` kind `APPOINTMENT` |
| F06 | Alarms | Move | Plan → Routines | `calendar_events` kind `ALARM` |
| F07 | Habits and study plans | Move | Plan → Routines | `habits`, `habit_logs`, `study_plans`, `plan_blocks` |
| F08 | Focus timer | Move | Plan → Routines | `focus_sessions` |
| F09 | Leave-by times | Keep (a toggle on an event) | event editor | `leave_rules` |
| F10 | **Birthdays** | **New** | Library → Dates | `birthdays` |
| F11 | **Trips** | **New** | Library → Dates | `trips` |
| F12 | Spend (income, expense) | Keep | Money → Spend | `transactions` |
| F13 | Safe to spend, insights, forecast | Merge | Money → Overview | computed |
| F14 | Receipts | Keep | Money → Spend toolbar | `receipts`, `receipt_lines` |
| F15 | Bills, subscriptions, owed | Merge (UI only) | Money → Pay | `bills`, `subscriptions`, `debts` |
| F16 | Budgets | Keep | Money → Plan | `budgets` |
| F17 | Goals (savings) | Keep | Money → Plan | `goals` |
| F18 | **Future purchase wishes** | **New** | Money → Plan | `wishes` |
| F19 | **Vault** (folders, notes, files, inbox) | **New** (absorbs Notes, Notebook board) | Library → Vault | `folders`, `notes`, `media_assets` |
| F20 | **Passwords** | **New** | Library → Records | `passwords` (device only) |
| F21 | **Achievements** | **New** | Library → Records | `achievements` |
| F22 | People, memories, promises | Keep | Library → People | `people`, `interactions`, `commitments`, `memories` |
| F23 | Jobs | Move | Library → Work | `job_applications` |
| F24 | Pantry and shopping | Move | Library → Home | `items`, `shopping_*` |
| F25 | Wardrobe and outfits | Move | Library → Home | `wardrobe_items`, `outfits` |
| F26 | Household sharing | Keep | Library → Home | `households`, `household_members` |
| F27 | **App time** | **New** | Library → Phone | `app_usage_days` (device only) |
| F28 | Ask (questions, meal, outfit, history) | Merge (Home chat questions, Ask screen, Notebook chats) | Ask screen | `chat_threads`, `chat_messages` |
| F29 | Speech and suggestions | Merge | Mic, one sheet | `voice_history` |
| F30 | Photo capture and filing | Keep | Camera entry | `media_assets` |
| F31 | Ledger AI panel (screen read, selection menu) | Keep | Panel activity | `clips` |
| F32 | Search | Keep (extended) | Top bar | reads only |
| F33 | Settings, sign-in, sync | Keep (reorganised) | Avatar | preferences |
| F34 | Widgets | Keep (cut down) | Launcher | reads only |

### 3.2 Retired or folded

| Surface | Fate |
|---|---|
| Today route | Removed. Agenda and Home replace it. |
| Voice Log screen | Removed. History goes to Settings → Voice and AI → History. |
| Life log screen, `CheckinWorker`, gap nags | Retired (D3). Rows stay in the database. |
| Insights, Forecast, Safe-to-spend routes | Removed as routes. Their composables become Overview sections. |
| Subscriptions route | Becomes Money → Pay → Repeats. |
| Notebook screen | Removed. Board becomes Vault → Inbox. Notes tab becomes Vault → Notes. Chats become Ask → History. |
| Quote of the day | Retired (D6). |
| `PERSONAL` calendar kind | Stays as a read alias of `EVENT`. New rows use `EVENT`. |
| `location_points`, `visits` | Dead schema. Left alone, flagged for a later cleanup. |

### 3.3 Explicitly not features

No points, levels or streaks on achievements. No wake word. No background screen reading (screen text is read only when the user calls the panel). No password autofill and no password sync. No per-app limits or blocking in App time. No price tracking, scraping or link previews for wishes. No web app, web dashboard or browser extension.

---

## 4. Structure

### 4.1 Bottom bar

Five items, mic in the centre, the same shape as today so muscle memory survives.

| Position | Item | Replaces |
|---|---|---|
| 1 | **Home** | Home chat, Today |
| 2 | **Plan** | Plan (Calendar, Tasks), minus Notes and Jobs |
| 3 | **Mic** | Speak (same control: tap to talk, hold to talk) |
| 4 | **Money** | Money |
| 5 | **Library** | Life |

Top bar on every tab: search field on the left, avatar (Settings) on the right. No Settings or Search in the bottom bar.

### 4.2 Segments

Segment controls hold at most four options (existing rule, see CHANGELOG 2026-10-19).

| Tab | Segments |
|---|---|
| Plan | **Agenda · Tasks · Appointments · Routines** |
| Money | **Overview · Spend · Pay · Plan** |
| Home, Library | none |

### 4.3 Library index

Library is a grouped list of names. Each row shows one live status line (a count or the next date), never a preview of the data.

| Group | Row | Status line example |
|---|---|---|
| Dates | Birthdays | "Next: Maya, 12 Nov" |
| | Trips | "Dhaka, in 9 days" |
| Records | Vault | "4 folders, 2 locked" |
| | Passwords | "12 saved" (count only, shown only when unlocked) |
| | Achievements | "Last: Passed IELTS, 3 Sep" |
| | People | "31 people" |
| Work | Jobs | "2 interviews this week" |
| Home | Pantry and shopping | "5 expiring soon, 8 to buy" |
| | Wardrobe | "42 pieces" |
| | Household | "2 members" or "Not set up" |
| Phone | App time | "Today 3 h 10 min" or "Allow usage access" |

### 4.4 Rules that keep it one app

1. **One fact, one record, one home.** Other screens show the record and link to it. They do not store a copy.
2. **Projection screens have no save button.** Home, Agenda and Library index only read.
3. **Create where the thing lives.** The Agenda's add button offers Task, Appointment, Event, Alarm only. Birthday, Trip, Wish, Achievement, Password and Vault entries are created in their own lists.
4. **One add path for text and voice.** Typed words and spoken words use the same categoriser and the same suggestion sheet.
5. **Reminders come from one engine** (section 15).

---

## 5. Home

Job: answer "what needs me" in one glance. Order, top to bottom: date, **Next**, safe-to-spend, coming up, ask bar.

### 5.1 Layout

1. **Date line.** Weekday and date. Tap opens Plan → Agenda on today.
2. **Next card.** One card or none. Rule: take every item that is due or starts in the next 24 hours and show the one with the earliest time.
   - Candidates: a reminder about to fire (any source), an appointment, a calendar event, a task due today, a bill due today, a trip starting tomorrow.
   - Ties: appointment, then bill, then task.
   - The card shows time, title, and one action (Open, or Done for a task).
3. **Safe to spend.** One large amount, caption "today". Tap opens Money → Overview. Hidden when the user has no transactions.
4. **Coming up.** One line, only if something exists in the next 14 days: nearest birthday or trip ("Maya's birthday, 12 Nov · Dhaka trip, 20 Nov"). Tap opens that list. This is how birthdays and trips are visible without being on the calendar (D5).
5. **Ask bar.** One text field and one mic button. Typed text and speech both go to section 14. A question gets an answer in the Ask screen. A statement gets a suggestion sheet.

Nothing else: no wardrobe, pantry counts, insight essays or quote. The home chat transcript is gone; threads live in Ask → History.

### 5.2 Empty state

Date, "Nothing due", safe-to-spend if money exists, ask bar.

---

## 6. Plan

### 6.1 Agenda

Job: show one day as a list of links, and move between days.

Header: a seven-day strip (today centred) with a month-grid toggle. Dots on the grid mean the day has a **calendar block, appointment or task**. Birthdays and trips never add dots.

Rows, sorted by start time, then all-day rows:

| Source | Row shows | Tap opens |
|---|---|---|
| Calendar block (`EVENT`, `CLASS`, `EXAM`, `ROUTINE`) | Time, title, kind chip | Event sheet |
| Appointment | Time, title, person or place | Appointment sheet |
| Task due that day | Checkbox, title | Task sheet; the box writes the task |
| Study plan block | Time, topic, status | Plan block sheet (start focus) |
| Habit due that day | Checkbox, title | Habit sheet |
| Alarm ringing that weekday | Time, label | Alarm sheet (Routines) |

If **Settings → Plan → Show dates here** is on, an all-day strip above the list adds birthdays whose month and day match and trips whose range covers the day. Default off (D5).

Add button: **Task, Appointment, Event, Alarm.** It does not offer anything else.

Conflict hint: when a trip covers the day, appointments and classes on that day show a small "during trip" note. Nothing is blocked.

Month grid, schedule import (CSV or image), recurrence delete scopes, leave-by and the event detail sheet are unchanged from today.

### 6.2 Tasks

Same list as today, now its own segment. Fields: title, notes, due date (optional), due time (optional), reminder offset (none, 1 day, 1 hour), done at. Sections: **Open** (dated first, then Later), **Done** (last 30 days shown). Checking a task sets `isCompleted` and `completedAt`. **Save as achievement** on a done task creates an achievement prefilled from it (title, date) and stores `source_task_id`. The task stays a task.

### 6.3 Appointments

See section 9.4 for the record. The segment shows a list: **Upcoming** by start, then **Past**. Filter chips: Upcoming, Past, Cancelled. A past appointment that is still "upcoming" shows a prompt row: Mark done, Reschedule, Cancel. The app never sets Done by itself.

### 6.4 Routines

One scroll with four sections, each a short list with its own add:

| Section | Content | Existing code kept |
|---|---|---|
| Alarms | Time, label, repeat days, on or off, tone | Alarm ringing service, tone picker |
| Habits | Daily habits, today's check, streak (habits only) | `HabitRepository`, nudges |
| Study plans | Plan, blocks, move or skip a block | `PlanRepository` |
| Focus | Start timer (25, 50, custom), recent sessions | `FocusForegroundService` |

Streaks exist only here, on habits. Nowhere else in the app has one.

---

## 7. Money

Large number at the top of Overview is safe to spend. The money rules in `SpendGuideCalculator` do not change.

### 7.1 Overview

Sections, top to bottom:

1. **Safe to spend today.** Amount, spent today, guide progress. Tap opens the breakdown sheet (the old Safe-to-spend screen).
2. **This month.** Spent, budget left, bills still due. Category chart from `Charts.kt`.
3. **Insight.** The single daily insight card (`InsightEngine`).
4. **Forecast.** Collapsed. Opens the line-plus-projection chart (the old Forecast screen).

### 7.2 Spend

Transactions list, search, filters, add sheet. Toolbar receipt icon opens **Receipts**: list of saved receipts, scan new, review screen. A receipt creates no transaction until the review is confirmed. Receipt food lines offer **Add to pantry** as a second, separate confirm.

### 7.3 Pay

One list sorted by due date, three filter chips: **Bills** (once), **Repeats** (subscriptions), **Owed** (debts).

| Chip | Row shows | Add |
|---|---|---|
| Bills | Name, amount, due date, paid state | Name, amount, due date, category |
| Repeats | Name, amount, period, next renewal, status | As today (`SubscriptionsScreen` fields) |
| Owed | Person, direction (I owe or they owe), remaining | Person (pick or create), direction, amount, note |

A payment against an owed row is a transaction linked to it, not a second debt.

### 7.4 Plan (money)

Three sections: **Budget** (one bar per category), **Goals** (saved of target), **Wishes** (see 9.5). They stay separate because they answer different questions: a budget is a monthly cap, a goal is a target amount and date, a wish is a thing I might buy.

---

## 8. Library screens that already exist

Moved and slightly reorganised, behaviour unchanged unless stated.

| Screen | Change |
|---|---|
| People | Person detail gains three read-only links: **Birthday**, **Appointments with this person**, **Owed with this person**. Memories, moments and promises as today. A memory gains **Copy to Vault** (a one-off snapshot, not a live link). |
| Jobs | Moved from Plan. **Add interview** creates an appointment with `job_id` set. |
| Pantry and shopping | Moved. Bought does not create an expense. A receipt does. |
| Wardrobe | Moved. Outfit suggestions stay inside this screen and never appear on Home or the widget. |
| Household | Moved. Never shares passwords, vault, achievements, birthdays or app time. |

---

## 9. New features: records

Every form below is a bottom sheet in the existing `LSheet` style. Every list has an empty state that says what the list is for and offers one button.

### 9.1 Birthdays

**What it is.** A yearly date for a person, with a reminder ladder. Not a calendar event. Not a task.

**Where.** Library → Dates → Birthdays.

**Record** (`birthdays`):

| Field | Required | Rule |
|---|---|---|
| Person | Yes | A `people` row. Typing a new name creates one. The name is shown from the person, so it is edited in one place. |
| Month, day | Yes | Valid pair. 29 Feb is allowed. |
| Birth year | No | If set, reminders add the age. Never guessed. |
| Celebration time | Yes | Minute of day. **Default 00:00** (D4). Change it for a party time such as 18:00. |
| Gift or plan note | No | 500 characters. Private. Never sent to the model. |
| Ladder switches | Yes | Nine switches below. New birthdays copy the defaults from Settings → Reminders. |
| Visibility | Yes | Private only. |

**The ladder.** Day-level reminders fire at the reminder hour (Settings, default 09:00) on the date shown. Clock-level reminders count back from the celebration time.

| Switch | Fires | Example: 14 Nov, celebration 00:00 | Example: 14 Nov, celebration 18:00 |
|---|---|---|---|
| 1 month | Same day-of-month one month earlier, at the reminder hour. If that day does not exist it uses the last day of that month. | 14 Oct 09:00 | 14 Oct 09:00 |
| 2 weeks | 14 days earlier, at the reminder hour | 31 Oct 09:00 | 31 Oct 09:00 |
| 1 week | 7 days earlier, at the reminder hour | 7 Nov 09:00 | 7 Nov 09:00 |
| 3 days | 3 days earlier, at the reminder hour | 11 Nov 09:00 | 11 Nov 09:00 |
| 1 day | 1 day earlier, at the reminder hour | 13 Nov 09:00 | 13 Nov 09:00 |
| 5 hours | 5 hours before the celebration time | 13 Nov 19:00 | 14 Nov 13:00 |
| 30 minutes | 30 minutes before | 13 Nov 23:30 | 14 Nov 17:30 |
| 10 minutes | 10 minutes before | 13 Nov 23:50 | 14 Nov 17:50 |
| **At the time** (added) | At the celebration time | 14 Nov 00:00 | 14 Nov 18:00 |

Rules:
- Each switch is independent. Turning one off leaves the others.
- A reminder whose time has already passed when the birthday is saved is **skipped**, not fired late.
- After a restart or a dead battery, at most **one** catch-up notification fires, for the most recent missed reminder from the last 6 hours. Older ones are dropped.
- Two reminders that land in the same minute collapse into one notification.
- 29 Feb in a non-leap year behaves as 28 Feb. The row says so.
- The next year is built after the "At the time" reminder fires, and a daily check rebuilds it if the app was dead.
- Times are local wall-clock times. A timezone change rebuilds them (the boot receiver already listens for it).

**Notification.** Channel "Birthdays". Title `{Name}'s birthday`. Body is the distance: `in 1 month`, `in 2 weeks`, `in 1 week`, `in 3 days`, `tomorrow`, `in 5 hours`, `in 30 minutes`, `in 10 minutes`, `now`. If a birth year is set add `· turns {age}`. Tap opens the birthday. Actions: **Snooze 1 h** on clock-level reminders only.

**List.** Next occurrence first, with a month divider. A row: name, date, age if known, the next reminder still on. Search by name. Month filter chips.

**Import.** An optional **Import from contacts** button reads the contacts birthday field (the `READ_CONTACTS` permission is already declared). It lists matches with checkboxes. Nothing is added until the user confirms. A contact without a year is imported without one.

**Deleting.** Deleting a person does not delete the birthday: the birthday asks "Keep as a name only?" and, if yes, keeps a person row with just the name.

**Never.** No calendar row. No task. No dot on the month grid.

### 9.2 Achievements

**What it is.** A log of things the user finished. Only that.

**Where.** Library → Records → Achievements.

**Record** (`achievements`):

| Field | Required | Rule |
|---|---|---|
| Title | Yes | 1 to 120 characters |
| Date | Yes | Defaults to today. Not in the future. |
| Note | No | 1 000 characters |
| Photos | No | Up to 3, stored as `media_assets` rows linked to the achievement |
| Source task | No | Set only by "Save as achievement" on a done task |

**List.** Newest first with a year heading. A row: title, date, thumbnail if any. Search matches title and note. Tap opens a read view with Edit.

**Nothing else.** No category, points, level, streak, reminder or goal progress. It does not appear on the agenda, on Home or in any widget.

**Speech.** Only a past-tense completion ("I finished my thesis", "I passed the exam") becomes an achievement suggestion. Anything in the future becomes a task or a goal.

### 9.3 Trips

**What it is.** A named range of dates and a place.

**Where.** Library → Dates → Trips.

**Record** (`trips`):

| Field | Required | Rule |
|---|---|---|
| Name | Yes | 1 to 80 characters |
| Place | Yes | Free text |
| Start date, end date | Yes | End is on or after start. Same-day trips allowed. |
| Notes | No | 2 000 characters |
| Reminders | Yes | Two switches, default on: **1 week before** and **1 day before**, at the reminder hour on the date |
| Visibility | Yes | Private by default. Sharing with the household is opt-in and arrives with Household sharing. |

**Status is derived, never stored.** Planned if today is before the start, Ongoing if today is inside the range, Done if after. A timezone change cannot leave it stale.

**List.** Sections Ongoing, Planned (by start), Past. A row: name, place, dates, a countdown ("in 9 days") or "day 2 of 5". Detail view: dates, notes, and a **During this trip** list: appointments and calendar blocks whose time falls inside the range, as read-only links.

**Rules.**
- A trip creates no calendar rows and no budget.
- No daily reminder during the trip.
- Optional, later (phase 4 in section 19): a nullable `trip_id` on `transactions` and a **Spent on this trip** total. It is not built with the first release of Trips.

### 9.4 Appointments

**What it is.** A commitment to be somewhere at a time, with someone or at some place.

**Where.** Plan → Appointments (list) and Plan → Agenda (rows).

**Decision.** An appointment is **a row in `calendar_events` with kind `APPOINTMENT`**, plus three nullable columns. It is not a new table and is never copied into the calendar. This gives it recurrence, the reminder rows, leave-by, the free-time finder and sync for free, and removes the "block this hour" duplicate.

| Field | Required | Rule |
|---|---|---|
| Title | Yes | 1 to 120 characters |
| Start | Yes | Local date and time |
| End | No | After start when set |
| Place | No | Free text (existing `location` column). Tap opens the phone's map app. |
| Person | No | `person_id`, a `people` row. Typing a new name creates one. |
| Job | No | `job_id`, set by "Add interview" |
| Notes | No | 2 000 characters |
| Status | Yes | `UPCOMING`, `DONE`, `CANCELLED`. Default `UPCOMING`. |
| Reminders | Yes | Default two rows in `event_reminders`: 1 day and 1 hour before (from `DefaultReminders`). Each can be removed. |

Behaviour: it blocks time (it is not `TASK` or `ALARM`). Cancelling keeps the row and cancels its reminders. Marking done cancels what is left. Leave-by works as for any event.

The difference from a plain event, in one line: an appointment has a counterpart (person or place) and a status; an event is a block of the user's own time.

### 9.5 Wishes (future purchases)

**What it is.** Something the user might buy later.

**Where.** Money → Plan → Wishes.

**Record** (`wishes`):

| Field | Required | Rule |
|---|---|---|
| Name | Yes | 1 to 120 characters |
| Estimated price | No | Amount, or empty. **Never filled by the model.** Empty shows "Price unknown". |
| Priority | Yes | 1 (low) to 3 (high), default 2 |
| Wanted by | No | Month, for "by December" |
| Link | No | Text. Opens in the phone's browser on tap. The app does not fetch it. |
| Note | No | 1 000 characters |
| Photo | No | One image (`media_assets`) |
| Status | Yes | `WANT`, `SAVING`, `BOUGHT`, `DROPPED` |
| Goal | No | `goal_id`, set by **Start saving** |
| Transaction | No | `transaction_id`, set when bought |
| Visibility | Yes | Private by default |

List: **Want and Saving** by priority then date added, then Bought and Dropped under a collapsed header. A row shows name, price, and "wanted for 23 days" (a gentle cooling-off cue, no nagging).

Actions, each its own tap:
- **Start saving** creates a goal prefilled from the wish (name, target = price, target date = wanted-by) and links it. Disabled if the price is unknown. The wish shows the goal's progress by reading it.
- **Buy soon** adds the name to the shopping list. The wish stays `WANT`. For small things; big things go through Bought.
- **Bought** opens a prefilled Spend draft. Saving the draft sets `transaction_id` and status `BOUGHT`. Closing the draft changes nothing.
- **Dropped** hides it from the main list.

It is none of: a shopping item, a goal, a subscription.

---

## 10. New features: Passwords and Vault

### 10.1 Vault (important information)

**What it is.** Folders that hold written notes and files from the phone. It replaces Notes and the Notebook board. It is where "most important information" lives.

**Where.** Library → Records → Vault.

**Structure.**

- **Folder** (`folders`): name (1 to 40 characters), parent (root or one level down, no deeper), pinned, sort order, **locked**, **this device only**, a system key.
- **Note** is the existing `notes` row with a new `folder_id`. Title, body, tags and the AI actions (summarise, tag, ask) are unchanged.
- **File** is a `media_assets` row with `kind = 'file'` and `linkedType = 'folder'`, plus mime, size, display name and a locked flag. The photo pipeline already stores, queues and uploads these, so there is no second file store.
- **System folders**, created once and impossible to delete: **Notes** (every existing note lands here) and **Inbox** (unfiled clips). The Notes folder can be relabelled, not removed.

**Actions inside a folder.**

| Action | What happens |
|---|---|
| **Write** | Opens an empty note in this folder. |
| **Upload** | Opens the phone's document picker (multiple allowed). Each file is copied into app storage. This is the only way a file enters. The app never scans the phone's storage. |
| **Scan** | Camera, then a file (photo). Reuses the capture camera. |
| **Move** | Moves notes and files between folders. |
| **Pin folder** | Up to four pinned folders sit above the list. This is how "most important" stays on top. There is no Important tab. |

**Locked folders.** A folder can be marked **Locked**.
- Its notes and files are encrypted with a Keystore key and need the phone's biometric or screen lock each time the folder is opened (session unlock lasts 2 minutes).
- Never uploaded, so locked implies this-device-only.
- Excluded from search, Ask, the digest, the daily note scan, widgets and the assistant context.
- The window is secure (no screenshots, no screen read).
- The lock is by folder, set when creating the folder, and removable only after unlocking.
- Same limit as Passwords: lost with the phone unless exported (section 10.2).

**Inbox.** Clips from the selection menu, share, tile and bubble land here, unfiled. A row has two actions: **File** (pick a folder, creates a note whose body is the clip text or the edited text) and **Delete**. The clip row keeps the original text as its source and points at the note. Existing AI filing (`clip.file_*` actions) keeps working: it files into tasks, events, bills and so on, and `clip.file_note` targets a folder (defaulting to the folder last used).

**Ask AI on a selection** still works from the panel (section 17.3). Save files into the last-used folder. Ask never creates a note until **Save answer**.

**Search** covers folder names, note titles and bodies, and file names. It never opens file bytes and never looks inside locked folders.

**Sizes.** Notes up to 20 000 characters. Files up to 25 MB each. Unsynced or locked files have no size limit other than storage.

**Sync.** Notes sync as today. Files upload through `media-sign` after the extension-and-mime change in section 16. Folders sync unless marked this device only.

### 10.2 Passwords

**What it is.** A small encrypted store for account details. Not a note. Not a Vault folder.

**Where.** Library → Records → Passwords.

**Record** (`passwords`, device only):

| Field | Stored | Notes |
|---|---|---|
| Title | Clear | e.g. "Gmail" |
| Site or app | Clear | Text. Not fetched. |
| Username | **Encrypted** | |
| Secret | **Encrypted** | |
| Note | **Encrypted** | e.g. recovery hints |
| Last changed | Clear | Date, defaults to created |
| Change every | Clear | Optional: 90, 180, 365 days |
| Created, updated | Clear | |

Encryption: AES-GCM with a Keystore key that requires user authentication (biometric or screen lock). Each value has its own random IV. Decryption happens in memory when the row is opened.

**Unlock.** Opening Passwords asks for the phone's biometric or screen lock. The unlocked session lasts 2 minutes after the screen is left. This needs the `androidx.biometric` dependency, which is not in the project today.

**Screens.**
- **List:** title and site only. Search by title.
- **Row:** reveal behind the unlock. **Copy** copies the secret as a sensitive clip (hidden from clipboard previews) and clears the clipboard after 30 seconds. **Open site** opens the link in the phone's browser.
- **Add and edit:** a form. A **Generate** button creates a random password locally (length 12 to 32, symbol options). No strength lookups, no network.
- **Health strip** (offline, on the list): "3 older than their change date", "2 reused". Reuse is found by comparing salted hashes in memory while unlocked, never stored.
- **Change reminder:** if "Change every" is set, one notification when the date passes (family "Passwords", content generic: "A password is due for a change", no title in the lock screen text).

**Export and import (D7).** Settings → Privacy → **Export passwords** writes one file to a location the user picks (document picker). The file is encrypted with a passphrase the user types twice (key derived with a slow key-derivation function, then AES-GCM). **Import passwords** reads that file after the passphrase and merges by title and site, asking before overwriting. Until export exists the Passwords screen shows a persistent line: "Stored only on this phone."

**Security rules.**
- The screen and the sheets set the secure-window flag. The Notebook screen reader and `ScreenGrabService` refuse to read it.
- Passwords never appear in: search, Ask, the assistant context, a Vault note, a chat, a notification, widgets, logs, the sync payload, `voice_history`, `assistant_actions` or `event_log`.
- No autofill service in the first build.
- Deleting a row deletes the ciphertext. There is no trash.
- The registry has **no** action whose type contains "password". A unit test fails the build if one is added.

**Speech.** If the words contain "password", "passcode", "PIN" or a password-looking token, the suggestion sheet shows one row, **Open Passwords**, and does not keep the words anywhere (not in `voice_history`).

---

## 11. Phone: App time

**What it answers.** "How long did I spend in each app today, this week, this month?"

**Where.** Library → Phone → App time.

**Permission.** `PACKAGE_USAGE_STATS`, granted by the user on the system Usage Access page. Until then the screen is one sentence and one **Allow** button that opens that page. No sample numbers. Access is checked with the app-ops check, not by trying and failing.

**Label lookup.** The manifest gets a `<queries>` entry for launcher activities (the MAIN and LAUNCHER intent) so Android 11+ can return app names. The broad all-packages permission is not used because Play restricts it.

**Collection.** An hourly WorkManager job (`AppUsageWorker`), only while access is granted:
1. Read usage events since the last saved cursor.
2. Add up foreground time per app, per **local calendar day**. A session that crosses midnight is split at midnight.
3. Upsert `app_usage_days` rows (day, package, label, foreground milliseconds, launch count).
4. Save the cursor.

Android keeps fine-grained events for only a few days, so the stored daily rows are the history. If the job is off for more than that window the gap is real and shows as a gap, never as an estimate.

**Excluded by default.** The launcher, system UI and the keyboard. This app is included like any other.

**Screens.**
- **Today** (default): total at the top, then apps longest first (icon, name, time). Toggle **Today · 7 days · 30 days**.
- **App detail:** that app's last 14 days as bars (existing `Charts.kt`).
- No limits, no blocking, no goals in this version.

**Rules.**
- Device only. Never synced. Never sent to the model. Not a source for Ask.
- Uninstalled apps keep their last known label.
- Retention 400 days, pruned by the same worker.
- It does not read what is on screen and records no content.
- It is separate from Focus. Focus minutes are never added into app minutes.

---

## 12. Vault, Passwords and App time in one view

| | Vault | Passwords | App time |
|---|---|---|---|
| Synced | Yes, except locked or device-only folders | **No** | **No** |
| In search | Yes, except locked | **No** | No |
| Seen by Ask / assistant | Only a note the user attaches | **No** | **No** |
| Needs unlock | Locked folders only | Always | No |
| Lost with the phone | Locked or device-only items | Everything unless exported | Everything |

---

## 13. Settings and widgets

### 13.1 Settings

One list. The old "Shortcuts" section (Life log, Insights) is gone.

| Section | Rows |
|---|---|
| Account | Sign in, sync on or off, last synced |
| Appearance | Light, dark, system |
| Voice and AI | Language (English, Bangla), speech engine and Offline voice (D2), cloud fallback, history |
| Money rules | Currency, safe-to-spend rules, category list |
| **Reminders** | Master switch. A switch per family: birthdays, trips, appointments and events, tasks, bills and owed, pantry expiry, passwords. **Reminder hour** (default 09:00). **Birthday defaults** (nine switches and the celebration time). Exact-alarm status with a button to the system page when missing. |
| **Plan** | Show dates on the agenda (D5), first day of week, default reminders |
| **Privacy and lock** | Export and import passwords, lock timeout (2 minutes default), block screen reading on locked screens (always on, shown for clarity) |
| Capture | Ledger AI panel setup, floating bubble, tile, retention for clips |
| Phone | App time access status and shortcut |
| Widgets | Private mode |
| About | Version, privacy policy |

### 13.2 Widgets

Three become two. The user's note stays: no widget is a second app.

| Widget | Shows | Taps |
|---|---|---|
| **Today** | Date, **Next** (the same rule as Home), safe-to-spend, up to 3 tasks with a check box | Next opens its record; money opens Overview; a task box toggles it |
| **Mic** | A mic button | Opens the phone speech screen, then the suggestion sheet |

Removed from widgets: quote line (D6), log-gaps count, jobs count, habit dots, streak, the focus widget (Focus starts from Plan → Routines or the notification). Private mode keeps hiding amounts.

---

## 14. One speech and capture path

Everything spoken or typed in the Ask bar, the bottom mic, the widget mic, the assistant gesture and the quick-capture sheet goes through this path.

1. **Input.** `SpeechInput` returns a string. The default implementation is the phone's own speech screen with the user's language (D2). The offline engines sit behind the same interface while they exist.
2. **Route.** One call decides **capture** (a statement to save) or **question** (to answer). Rules first, then the fast model through `ai-proxy`. This replaces the separate Voice Log modes.
3. **Suggest.** Capture produces one or more typed suggestions in a sheet. Each row: title, one detail line, **Save**. Nothing is written before Save. The tap is the confirmation.
4. **Write.** Save runs the typed action through the executor. A row is marked saved only after the executor returns the new id.
5. **Fallback.** If nothing can be categorised, the sheet offers one action, **Save in Vault → Notes**, with the exact words. It is a note, not a guessed expense.
6. **Question.** The Ask screen opens with the thread. Answers use only the signed-in user's rows. A price the model invents is dropped (existing rule).

### 14.1 What a spoken sentence can become

| Heard as | Suggestion | Home |
|---|---|---|
| Money spent or received | Expense or income | Money → Spend |
| A bill, or a repeating charge | Bill or subscription | Money → Pay |
| Money owed to or from a person | Owed row (linked to People when the name matches) | Money → Pay |
| A savings target with an amount | Goal | Money → Plan |
| Something to buy "someday", no shopping trip | **Wish** | Money → Plan |
| Groceries, "add to the list" | Shopping item | Library → Home |
| A thing to do | Task | Plan → Tasks |
| A meeting with a time and a person or place | **Appointment** | Plan → Appointments |
| A class, exam, or "put it on my calendar" | Calendar block | Plan → Agenda |
| A wake time | Alarm | Plan → Routines |
| "Maya's birthday is 14 November" | **Birthday** | Library → Dates |
| A trip with dates | **Trip** | Library → Dates |
| "I finished…", "I passed…" | **Achievement** | Library → Records |
| A name plus a fact | Memory on a person | Library → People |
| Anything else | Note in Vault → Notes | Library → Records |

### 14.2 What speech must never do

- Never create or echo a password (section 10.2).
- Never create a calendar row for a birthday or a trip.
- Never turn an appointment into a second record.
- Never answer a meal, outfit or shopping question from the widget. The widget mic only suggests rows to save.
- Never invent a date, price, year of birth or amount. A missing value stays empty and the sheet asks.
- Never save a suggestion the user did not tap.

---

## 15. Reminder engine

One engine for every scheduled notification. This fixes the 500-alarm limit and the six separate schedulers.

### 15.1 Design

- A local table `reminders` holds every pending instant: `source_type`, `source_id`, `rung` (which switch), `fire_at` (UTC milliseconds), `state` (`PENDING`, `DELIVERED`, `SKIPPED`), `title`, `body`. It is derived, device only and rebuilt from the source rows, so it is never synced.
- **One** exact alarm is set, for the earliest `PENDING` row. When it fires the receiver delivers every row due within 30 seconds, marks them, and arms the next. Android then never holds more than a handful of this app's alarms.
- A 24-hour WorkManager job rebuilds the table from the sources and re-arms. The boot, time-set and timezone receivers (already registered) do the same.
- If exact alarms are not permitted (`canScheduleExactAlarms()` is false) the engine falls back to inexact alarms and Settings → Reminders shows a warning with a button to the system page.
- Tapping a notification opens the source record. Deleting or completing the source marks its rows `SKIPPED`.

### 15.2 Sources

| Source | Instants | Sound |
|---|---|---|
| Birthday | Up to nine, section 9.1 | Notification |
| Trip | 1 week and 1 day before, reminder hour | Notification |
| Appointment | Its `event_reminders` rows (default 1 day, 1 hour) | Notification |
| Calendar event | Its `event_reminders` rows (up to 10) | Notification |
| Task | The one offset set | Notification |
| Bill and owed | Reminder hour on the due date | Notification |
| Pantry | Reminder hour, 3 days before expiry | Notification |
| Password change | Once when due, generic text | Notification |
| Alarm | The clock time | **Rings** (existing ringing service) |

### 15.3 Migration of the old schedulers

The engine arrives in two steps so nothing is rewired in one risky change.

1. New sources (birthdays, trips) use the engine. Old schedulers (`EventReminderScheduler`, debt, leave-by, plan block, habit nudge) keep their own alarms. A debug counter logs total pending alarms and warns above 400.
2. Later, event, debt, plan-block and habit reminders are moved onto the engine and the old schedulers are deleted. Alarms (the ringing kind) stay as direct alarms because they must ring at a precise time with a full-screen intent.

The play-store policy on `USE_EXACT_ALARM` (reserved for alarm and calendar apps) is reviewed in step 2. `SCHEDULE_EXACT_ALARM` alone is the safer declaration if the app does not qualify.

### 15.4 Channels

One notification channel per family (Birthdays, Trips, Appointments and events, Tasks, Money, Pantry, Passwords, Alarms) so the user can change sound and importance in Android settings. The master and per-family switches in Settings stop scheduling, not just hide.

---

## 16. Data changes

Room goes from v21 upward in **one version per phase** so each phase can ship alone. Each version has a `MigrationNNToNN+1` and a test (same pattern as `Migration20To21Test`). New tables follow the existing convention: `id`, `user_id`, `updated_at`, `deleted_at` soft delete, and an owner-only RLS policy when synced.

### 16.1 New tables

| Table | Columns (besides id, user_id, updated_at, deleted_at) | Sync |
|---|---|---|
| `folders` | name, parent_id?, pinned, sort, locked, device_only, system_key? (`notes`, `inbox`) | Yes, unless `device_only` |
| `birthdays` | person_id (required), month, day, birth_year?, celebrate_minute (default 0), note?, nine booleans `r_1mo, r_2w, r_1w, r_3d, r_1d, r_5h, r_30m, r_10m, r_at`, visibility (private) | Yes |
| `trips` | name, place, start_date, end_date, notes?, r_1w, r_1d, visibility | Yes |
| `achievements` | title, happened_on, note?, source_task_id? | Yes |
| `wishes` | name, amount?, priority, wanted_by?, link?, note?, status, goal_id?, transaction_id?, visibility | Yes |
| `passwords` | title, site?, username_enc, secret_enc, note_enc, iv per field, last_changed, change_every_days?, created_at | **No** |
| `app_usage_days` | day, package_name, label, foreground_ms, launches | **No** |
| `reminders` | source_type, source_id, rung, fire_at, state, title, body | **No** |

### 16.2 Changed tables

| Table | Change | Why |
|---|---|---|
| `calendar_events` | New kind `APPOINTMENT`. New nullable `person_id`, `job_id`, and `status` (default `UPCOMING`). | Appointments (9.4). The server check constraint on `kind` (migration 004) must allow it. |
| `notes` | New nullable `folder_id` (null reads as the Notes folder). | Vault (10.1). Existing `clips.noteId` and note AI keep working. |
| `media_assets` | New nullable `mime`, `size_bytes`, `locked`. New kinds `file`. Link types `folder`, `achievement`, `wish`. | Vault files and photos reuse the existing queue. |
| `transactions` | New nullable `trip_id`. | **Phase 4 only**, optional (9.3). |

### 16.3 Edge Function change

`media-sign` currently signs `{user.id}/{id}.jpg`. It needs an allowlist of extensions and mime types (pdf, png, jpg, heic, txt, docx, xlsx, zip) and a 25 MB cap. Locked and device-only files never request a URL.

### 16.4 Server migrations

New SQL goes in `supabase/planned/migrations/` as 022 and up, and is promoted in order **after** 012 to 021 are promoted (those are still unapplied; see 1.4). Each needs an RLS test: an owner reads and writes, another user sees nothing. The calendar `kind` check change and the appointment columns are 022. `passwords`, `app_usage_days` and `reminders` have no server table.

### 16.5 Visibility

Per `CLAUDE.md` rule 6, every user table is private by default. Only `trips` and `wishes` carry a `visibility` column that Household sharing may later widen. `birthdays` is private only because its gift note can reveal a surprise. `achievements`, `passwords` and `app_usage_days` are never shared.

---

## 17. Assistant integration

### 17.1 New action types

All writes go through `ActionRegistry` and `Executor`. Risk tiers follow `CLAUDE.md` rule 3.

| Action | Risk | Notes |
|---|---|---|
| `birthday.add` | MEDIUM | One tap on the suggestion sheet. Creates the person if needed. |
| `trip.add` | MEDIUM | |
| `appointment.add` | MEDIUM | An event row of kind `APPOINTMENT`. |
| `achievement.add` | MEDIUM | Past tense only. |
| `wish.add` | LOW | Reversible, private, no money moves. |
| `wish.start_saving` | HIGH | Creates a goal. Explicit confirm. |
| `wish.mark_bought` | HIGH | Opens a spend draft. Money. |
| `vault.note_add` | LOW | Also the speech fallback. |
| `birthday.delete`, `trip.delete`, `appointment.cancel` | MEDIUM | |

No password action exists. No app-usage action exists. A registry test enforces both.

### 17.2 What the model may read

| Data | Read in answers | Propose new rows |
|---|---|---|
| Money tables, wishes | Yes | Yes, as suggestions |
| Calendar, appointments, tasks, trips | Titles and times | Yes |
| Birthdays | Names and dates (not the note) | Yes |
| Achievements | Titles | Yes |
| Vault note | Only one the user attaches | Only after Save |
| Locked folders, Passwords, App time | **Never** | **Never** |

`ContextSelector` and `ContextFactsMapper` gain the new facts (birthdays within 30 days, trips within 60 days, upcoming appointments, wishes by name). Rule 4 holds: unknown stays unknown, extracted values are marked `confirmed` or `inferred`.

### 17.3 Ledger AI panel

Unchanged in behaviour. Screen text is read only when the user calls it. It refuses when the foreground screen is Passwords or a locked folder. Save files a clip into the last-used Vault folder. Ask stores a thread, which now appears under Ask → History.

---

## 18. Remove everything related to web

Nothing here is done by this document. It is the work list for step 1 of section 19. The app is Android only.

### 18.1 What exists

| Item | State | Action |
|---|---|---|
| `web/` | 26 untracked files: 24 HTML screens, `shell.js`, `tokens.css`. A clickable prototype of the earlier design. No file in `app/` references it. | **Delete the folder.** |
| `.design/DIRECTION.md` | Untracked. Describes the web prototype and a paper-and-brass type direction. | **Delete the file and folder.** |
| `design-system/ledgerai/` | Untracked. `MASTER.md` is CSS variables and web font imports, `pages/app.md` the web screen rules. | **Delete the folder.** The Android theme in `presentation/theme/` is the design system. |
| `.agents/skills/` (frontend-design, ui-ux-pro-max), `skills-lock.json`, `.cursor/` | Untracked tooling installed to produce the web prototype. | **Ask first.** Nothing in the app depends on them. Default: delete the two skills and `skills-lock.json`; leave `.cursor/` unless it holds only the same. |
| `docs/life-os/architecture.md` line 69 | Lists "iOS, web dashboard, browser extension" as out of scope | Edit the line to "iOS" only. |
| `docs/life-os/roadmap.md` line 54 | "browser extension only if validated" | Remove the clause. |
| `docs/life-os/roadmap.md` line 77 | Risk row "iOS/web demand" | Reword to "iOS demand". |
| `docs/life-os/adr/0001-evolve-existing-android.md` lines 20 and 24 | Deferred web dashboard and extension | Remove them. Replace line 24 with "iOS is out of scope." |
| `docs/screens/*.png` | Tracked screenshots of the Android app | Keep. They are not web. |

### 18.2 What stays, and why

| Item | Why it is not "web" |
|---|---|
| `SUPABASE_GOOGLE_WEB_CLIENT_ID` in `secrets.properties` | Google's required OAuth client type for Android sign-in through Credential Manager. Not a web app. |
| `ai-proxy` and `media-sign` Edge Functions, Supabase, R2 | The backend of the Android app. |
| Weather, place search | Network data sources the app calls. |
| `privacy-security.md` line 45 (prompt injection from web pages) | A security note about pasted content, not a web product. |
| Opening a link in the phone's browser | A tap on a stored link. The app does not browse, scrape or track prices. |

### 18.3 After deletion

Run a search for `web`, `html`, `browser`, `extension` and `dashboard` across `docs/`, `PLAN.md`, `README.md` and `CLAUDE.md` and review each hit. `README.md` and `PLAN.md` currently have none.

---

## 19. Build order

Each step ends with the previous app still working, ships on its own, and has a check. Per `CLAUDE.md`, nothing is built on a gap: the first step closes the backend gap.

| Step | Work | Check |
|---|---|---|
| **0. Prerequisites** | Promote `supabase/planned` migrations 012 to 021 in order (each with Room entity, sync handler and RLS test), per [roadmap.md](roadmap.md) Stage 0 to 3 gates. Commit or stash the uncommitted widget-mic changes. | `supabase db push` succeeds on a scratch project. Two-user RLS test passes. Existing tests pass. |
| **1. Web removal and docs** | Section 18. Update this plan's status, `CHANGELOG.md`, `roadmap.md`. | Search for web terms returns only the "stays" rows. Git status shows only intended deletions. |
| **2. Reminder engine** | `reminders` table, one rolling alarm, daily rebuild, channels, Settings → Reminders. Unit tests for the ladder math. | Table-driven tests: 14 Nov at 00:00 and 18:00, month-end clamp, 29 Feb, past rungs skipped, one catch-up. 200 test birthdays hold at most 1 pending alarm. |
| **3. Birthdays and Trips** | Tables (Room vN), repositories, sync, Library → Dates, Home "Coming up", notifications, contacts import. | A birthday saved with all nine switches produces the table in 9.1. A birthday adds no `calendar_events` row. Airplane mode: reminders still fire. |
| **4. Appointments** | Kind `APPOINTMENT`, columns, server `kind` constraint, Plan → Appointments, "Add interview" in Jobs, person links. | One row in `calendar_events` per appointment. Reminders reuse `event_reminders`. Free-time finder treats it as busy. Old clients ignore the new kind safely (kind parse falls back to `EVENT`). |
| **5. Vault** | `folders`, `notes.folder_id`, file entries in `media_assets`, Upload, Write, pin, Inbox, Locked folders, `media-sign` extension allowlist. Point Plan → Notes and the Notebook screen at Vault. Stop adding notes outside folders. | Every existing note shows in Notes. A PDF uploaded on one device appears on another. A locked folder has no network call, no search hit, no Ask hit. |
| **6. Passwords** | `androidx.biometric`, Keystore, table, screens, secure window, screen-reader refusal, clipboard clear, generator, export and import. | Round trip: add, lock, unlock, copy, clipboard cleared in 30 s. Export on one phone, import on another. Registry test shows no password action. Screen read on the Passwords screen is refused. Speech with "password" saves nothing. |
| **7. Achievements and Wishes** | Tables, lists, "Save as achievement" on tasks, wish actions, speech routes. | Wish saved by speech is a wish, not a shopping item or a goal. Achievement has no streak, reminder or agenda row. |
| **8. App time** | Permission screen, `<queries>`, worker, daily table, screens. | Empty and no numbers until access is granted. After granting, today's top app matches the system's Digital Wellbeing within a minute. |
| **9. New shell** | New bottom bar and segments (section 4). Merge Today, Voice Log, Insights, Forecast, Safe-to-spend, Subscriptions and Notebook routes. Library index. Retire Life log and quote (D3, D6). | No route in the old list remains reachable. Every deep link, shortcut, tile and notification opens its record. Back navigation tested on each tab. |
| **10. Speech path** | `SpeechInput`, one router, one suggestion sheet, the routing table in 14.1, golden-set tests for the new intents. | Golden set: each row of 14.1 plus the never-lists in 14.2. Widget mic opens the phone speech screen and ends in the sheet. |
| **11. Widgets** | Two widgets (section 13.2). | Next on the widget equals Next on Home for the same data. |
| **12. Clean-up** | Move old reminder schedulers onto the engine. Delete dead screens and workers. Review dead schema. | Pending alarms stay under 20 with 100 events. |

### 19.1 Tests added

Unit: ladder math, month and leap-year clamps, status derivation for trips, appointment status prompts, password encryption round trip, export file round trip, usage-day splitting at midnight, routing for every row of 14.1, the no-password-action registry test. Migration tests for each Room version. RLS tests for `folders`, `birthdays`, `trips`, `achievements`, `wishes`, `calendar_events` (new columns). Section 22 is the behaviour list. A row marked Untested or Not built is still open.

### 19.2 Docs updated in the same change

`CHANGELOG.md` (entry per step), `roadmap.md` (a "Rebuild" section with these steps), `data-model.md` (new tables), `privacy-security.md` (passwords, locked folders, app time), `ui-design.md` (replace the tab table with section 4). `PLAN.md` and `README.md` get the new bottom bar.

---

## 20. Risks

| Risk | Mitigation |
|---|---|
| Step 0 (promoting ten migrations) is large and gates everything that syncs | Birthdays, trips, achievements and wishes can ship local-only first; the Room tables are identical, sync follows. Passwords and app time are never synced anyway. |
| Reminder engine misses notifications on aggressive battery managers | Daily rebuild plus boot receivers; Settings shows exact-alarm status; a "Test reminder" button. Document vendor battery settings in the Reminders screen. |
| Users lose passwords or locked files on a new phone | Persistent "Stored only on this phone" line; export prompt after the 5th password is added; export reminder every 90 days. |
| `APPOINTMENT` kind breaks older installs or the server check | Kind parse already falls back to `EVENT`. Server migration ships before the client pushes the new kind, gated by a flag. |
| Merging routes breaks widget, tile and notification deep links | `LaunchRequest` is rewritten once, with a test per entry in 1.2. |
| Scope: eight new features plus a shell rewrite | Step order keeps the old shell until step 9. Any step can stop and the app still works. |
| Removing offline speech engines (D2) hurts Bangla or no-network use | Not removed in this plan. Measure first; decide after step 10. |

---

## 21. Acceptance checklist

- Creating a birthday adds no row to `calendar_events` and no dot on the month grid.
- The nine birthday reminders match the table in 9.1 for both example times, and a switched-off one never fires.
- 200 birthdays hold at most one pending alarm from the reminder engine.
- An appointment is one `calendar_events` row. It appears in Plan → Appointments and Agenda, and nowhere is there a second copy.
- A trip creates no calendar rows and no budget.
- A wish from speech is a wish, not a shopping item, goal or transaction.
- An achievement has no streak, no reminder and no agenda row.
- A password never appears in search, Ask, a note, a notification, a log, a widget or a sync payload.
- A locked folder has no search hit, no upload, no Ask hit and no screen read.
- App time shows nothing until Usage Access is granted, and it never reaches the model.
- Home shows at most one Next card, one money number and one Coming up line.
- Every old deep link (widget, tile, shortcut, notification, selection menu) opens the right record.
- `web/`, `.design/` and `design-system/` are gone and the web terms search returns only the allowed items in 18.2.

---

## 22. Edge cases

Checked on 2026-10-22 against `app/src/test`. The question was: if a person does this, does a unit test already assert the outcome?

| Word | Meaning |
|---|---|
| **Tested** | A unit test asserts this behaviour. The test is named in the last column. |
| **Partial** | A nearby test covers a piece of it. The missing piece is named. |
| **Untested** | The code exists. No test asserts this. |
| **Not built** | This plan's feature is not in the app yet. |

Where today's code and this plan disagree, the **Expected** column follows this plan and the row says so.

The picture in one pass: conflict detection, recurrence expansion, alarm next-fire, insights, receipt honesty, subscription month-ends, people and household membership, pantry quantities, job paste, wardrobe wear, speech routing and the executor's risk rules are tested. Home's Next card, the safe-to-spend calculator, paying a bill twice, paying a debt, habit and focus behaviour, and every new feature in sections 9 to 11 and 15 are open.

### 22.1 Home (section 5)

| Behaviour | Expected | Coverage | Test |
|---|---|---|---|
| Open Home with nothing due in the next 24 hours | Date, "Nothing due", the ask bar, and safe-to-spend only if money exists | Not built | |
| Several things fall inside 24 hours | Next is the earliest one | Not built | |
| An appointment, a bill and a task share the same minute | Tie order is appointment, then bill, then task | Not built | |
| A task is due today with no clock time | It can still be Next, with Done as the action | Not built | |
| A task has no due date | It stays off Next | Partial | Reminders skip undated tasks (`EventReminderRulesTest.nextFire_skipsCompletedDisabledUndatedAndAlarms`). Home does not. |
| A reminder is about to fire and a calendar block starts later | The reminder wins Next | Not built | |
| A trip starts tomorrow and nothing else is due | It can be Next, and it appears on Coming up | Not built | |
| A birthday is 5 days away | One Coming up line. No calendar dot | Partial | A yearly birthday reminder is found (`EventReminderRulesTest.nextFire_yearlyBirthdayWithWeekBeforeReminderIsFoundBeyondShortWindow`). The Home line is not built. |
| The user has no transactions | Safe-to-spend is hidden | Untested | Spend-of-zero is tested (`HomeSnapshotTest.emptyList_spendIsZeroAndRecentIsEmpty`). The hide rule is not. |
| Tap the date line | Opens Plan → Agenda on today | Untested | |
| Tap Done on the Next task | The task completes and Next refreshes | Untested | |
| The title is Bangla or very long | The title is shown as stored, ellipsised on the card | Untested | |
| Type a question in the ask bar | Ask opens. A statement opens the suggestion sheet | Untested | Routing itself is tested in section 22.23. |
| Today and this month spend, with income and a refund on the same day | Income is excluded. A negative expense reduces the sum. Soft-deleted rows are skipped. Month edges include the first and last day | Tested | `HomeSnapshotTest` |

### 22.2 Agenda (section 6.1)

| Behaviour | Expected | Coverage | Test |
|---|---|---|---|
| The day is empty | An empty list. Free-time search treats 08:00–22:00 as one gap | Partial | `ScheduleRulesTest.emptyDayIsOneBigGap`. The empty screen is untested. |
| A timed class and an all-day event share a day | Timed rows, then all-day rows | Untested | Today's code sorts all-day first. This plan says timed first. |
| Check a task on the agenda | The task completes. A completed task leaves the day | Untested | |
| A task has no date | It stays off the agenda | Untested | |
| A habit, a study block or a weekday alarm falls on that day | Each gets its own row and opens its own sheet | Untested | |
| The alarm is switched off | It does not ring. Disabled events are ignored in conflicts | Partial | `ScheduleRulesTest.disabledEventsAreIgnored`. The agenda row is untested. |
| Week strip and month grid | The grid is Monday-first today. Dots mean a block, appointment or task. Birthdays and trips add no dots unless Show dates here is on | Untested | The Sunday-first setting in section 13 is not built. |
| Two events overlap | A clash, with a fix suggestion. Touching end-to-start is not a clash. Tasks and alarms never clash. Two rows of the same series do not clash | Tested | `ScheduleRulesTest` |
| Find a free hour after a lecture, or late in the evening | A slot after the busy block. No slot once the window has closed | Tested | `FreeBlockFinderTest.findsGapAfterBusyBlock`, `ScheduleRulesTest.lateNowLeavesNoGap` |
| Import a timetable | A headed CSV, AM/PM and midnight parse. Gibberish imports as nothing | Tested | `ScheduleCsvParserTest`, `ScheduleTimeParserTest`, `ScheduleRulesTest.gibberishTimetableIsEmpty` |
| One CSV line is missing its time or day | That line is skipped. The rest import | Partial | Happy paths only. |
| Delete one occurrence of a weekly event | That date is excluded. Later ones remain | Partial | Expansion skips excluded dates (`RecurrenceExpanderTest.weekly_skipsExcludedDates`). The delete action is untested. |
| Delete this and future, or delete the whole series | `until` moves, or the series is soft-deleted with its reminders and leave-by | Untested | |
| A weekly class skips next Monday, or its `until` is already past | The next reminder jumps ahead, or never fires | Tested | `EventReminderRulesTest` |
| Turn on leave-by: 15 minutes travel, 5 minutes buffer, start 09:00 | Leave at 08:40 | Tested | `LeaveByTimeTest.computeLeaveAt_subtractsTravelAndBuffer` |
| Leave-by time is already past, or the event is completed | The leave alarm is not scheduled, and a completed event drops its leave rule | Untested | |
| The end time arrives before the start | End is clamped to start | Tested | `SyncMappersTest.eventDto_endBeforeStartIsClamped` |
| A trip covers the day | Classes and appointments show "during trip". Nothing is blocked | Not built | |
| Add an eleventh reminder to one event | The cap stays at 10. Sync rejects an eleventh active reminder | Tested | `EventReminderRulesTest.cap_keepsOnlyTenReminders` |

### 22.3 Tasks (section 6.2)

| Behaviour | Expected | Coverage | Test |
|---|---|---|---|
| Open list | Dated tasks first, then Later. Done shows the last 30 days | Untested | Today's screen groups Late / Today / Soon and does not cap Done at 30 days. |
| Due date with no time, or due at 00:00 | Date-only label. After midnight an open 00:00 task is overdue | Untested | |
| No due date | Later. No reminder | Partial | Undated tasks produce no next fire. The list group is untested. |
| Check Done, then reopen | `isCompleted` and `completedAt` set, then cleared | Untested | |
| Check the same task from the widget and the list together | One final state. A second toggle does not corrupt the row | Untested | |
| Complete one day of a repeating task | That date is excluded. The series stays open | Untested | |
| Reminder offset of none, 1 day or 1 hour | Only that offset is scheduled | Untested | Event defaults today are 10 minutes, 1 hour and 3 hours. |
| A one-off reminder's time has passed | It does not fire again | Tested | `EventReminderRulesTest.nextFire_absoluteReminderFiresOnce`, `nextFire_atTimeFiresAtStartAndPastIsNull` |
| Save a done task as an achievement | An achievement is created with `source_task_id`. The task stays a task | Not built | |
| Say "add a task" | A task suggestion, even when the sentence also contains bill words | Tested | `QuickParseIntentTest.parseVoiceIntent_task`, `parseVoiceIntent_taskStillBeatsBillWords` |
| Search, empty list, Bangla title, very long title | Case-insensitive match, an empty state, and the full title stored | Untested | |

### 22.4 Appointments (section 6.3 and 9.4)

The kind `APPOINTMENT` is not in the code. Every row is **Not built**. Conflict and leave-by math for ordinary events is tested and can be reused.

| Behaviour | Expected |
|---|---|
| Create one with a person, a place and a start | One `calendar_events` row, kind `APPOINTMENT`, default reminders 1 day and 1 hour. It blocks time. |
| No end time | Allowed. |
| End before start | Rejected or clamped the same way events are. |
| Overlaps a class | Shown as a clash. Still saved. |
| Cancel | The row stays, status `CANCELLED`, reminders cancelled. |
| Mark done | Status `DONE`. Remaining reminders cancelled. The app never marks done on its own. |
| The start has passed and the status is still Upcoming | A prompt: Mark done, Reschedule, Cancel. |
| Filter Upcoming, Past, Cancelled | Each chip shows only that set. |
| The linked person is deleted | The appointment stays. The person field is empty. |
| Jobs → Add interview | An appointment with `job_id` set. Today, "interview" speech stays on the jobs follow-up (`QuickParseIntentTest.interview_landsOnTheJobsFollowUpNotTheCalendar`). |
| Weekly checkup | Uses recurrence and the same delete scopes as events. |
| Timezone change while travelling | Reminders rebuild to the new local time. |
| Leave-by | Same subtraction as any event. |
| Blank title, or a title over 120 characters | Rejected. |
| The same meeting saved from speech and from the form | One row. Speech must not write a second copy. |

### 22.5 Routines (section 6.4)

| Behaviour | Expected | Coverage | Test |
|---|---|---|---|
| One-shot alarm still ahead today | Rings today | Tested | `AlarmTriggerCalcTest.oneShot_sameDayWhenStillAhead` |
| One-shot time already past | Rolls to tomorrow | Tested | `AlarmTriggerCalcTest.oneShot_rollsToTomorrowWhenPast` |
| Weekday alarm checked on Saturday | Next Monday. Same weekday still ahead rings today. Same weekday already past waits a week | Tested | `AlarmTriggerCalcTest.repeatDays_*` |
| Alarm at 02:30 on a spring-forward morning, when that local time does not exist | A defined result. The app does not crash | Untested | Tests use UTC only. |
| The same local time happens twice on a fall-back morning | One chosen instant | Untested | |
| Switch the alarm off | The pending ring is cancelled | Untested | |
| Say an alarm in Bangla, or "in ten minutes", or a PM time that has passed | The next real clock time | Tested | `QuickParseIntentTest` |
| Check a habit once today | One done log and the habit streak moves | Untested | |
| Check the same habit twice in a day | Still one done day. A second log must not inflate the streak | Untested | `habit_logs` has no unique key on habit and date, so a second insert is possible. |
| Open a weekday-only habit on Sunday | No row for that day | Untested | |
| A habit nudge falls in quiet hours, or the habit is deleted | No nudge. A deleted habit cancels its nudges | Untested | |
| Move or skip a study block | A skipped block leaves the agenda | Untested | |
| An exam is coming, or a class day has free time | A study suggestion in a free slot, at most two, and none for a past exam or one already planned | Tested | `ScheduleRulesTest` exam and class-day cases |
| A streak appears on Tasks or Home | Streaks exist only on habits, inside Routines | Untested | The tasks screen still shows a done-day streak. |
| Start a 25, 50 or custom focus timer | A session row and a foreground notification | Untested | |
| The system kills the focus service | The session ends cleanly. The user can start again | Untested | The service is `START_NOT_STICKY`. |
| Focus minutes and App time | Kept apart. Focus is never added into app minutes | Not built | App time itself is not built. |

### 22.6 Money overview (section 7.1)

| Behaviour | Expected | Coverage | Test |
|---|---|---|---|
| Ask "how much can I spend today" after spending past the limit | The answer says the limit is past | Tested | `RuleAnswersTest.safeToday` |
| No budgets and no income | The answer asks the user to set a budget or log income | Untested | |
| First day of the month, last day, or a timezone change at the month boundary | The guide uses the local month and at least one day left. It reserves upcoming bills and debts the user owes | Untested | Home spend month edges are tested. The guide calculator has no tests. |
| Yesterday's unused guide | Half of it carries into today | Untested | |
| Bills and debts eat the whole remaining pool | The guide shows zero, not a negative | Untested | |
| A huge spend today | The status is over. The number stays displayable | Untested | |
| One daily insight, with over-budget and an overdue bill both true | Over-budget wins. Overspending against income is reported. An empty history asks the user to track | Tested | `InsightRulesTest` |
| Open Forecast | Three months, bills included, inactive bills ignored, risk from income or from budgets when there is no income, zeros when there is no data | Tested | `InsightRulesTest` |
| A soft-deleted budget | Ignored by insights | Tested | `InsightRulesTest.deletedBudgetsAreIgnored` |
| Weekends are heavier, or many small tickets | The behaviour insight fires on those patterns | Tested | `InsightEngineTest` |

### 22.7 Spend and receipts (section 7.2)

| Behaviour | Expected | Coverage | Test |
|---|---|---|---|
| Filter this month, net, and search merchant, note or category | Month filter, income minus expense, search ignores location | Tested | `SpendQueryTest` |
| Search for `$`, `%` or `.*` | A literal match. No crash | Untested | |
| Duplicate a past expense for today | A new draft: same fields, new id, today's date | Tested | `SpendQueryTest.duplicateToday_copiesFields_resetsIdAndDate` |
| Log zero, or a very large amount | Stored as entered. Zero is a real amount | Untested | |
| Say "refund of $12 from Amazon" | An income entry. A negative expense reduces spend | Tested | Rules table refund case. `HomeSnapshotTest` for the negative expense. |
| Two currencies in one month | Each row keeps its currency. The month total does not invent an exchange rate | Partial | Currency survives sync (`SyncMappersTest`). Totals are untested. |
| Add a transaction offline, then the same row syncs with a newer remote edit | The newer `updated_at` wins | Partial | Remote-wins is tested for calendar rows, not transactions. |
| Scan a receipt with no total | The total stays empty. Line prices stay empty until the user types them | Tested | `ReceiptCaptureTest.missingTotalIsNotFilledIn`, `confirmDoesNotInventLinePrices` |
| Confirm a receipt | Present merchant, date, total and line prices become confirmed. Inferred flags stay until then | Tested | `ReceiptCaptureTest` |
| Confirm a receipt that is already confirmed | The second tap does nothing | Untested | |
| Confirm a receipt and look for a new transaction | Section 7.2: no transaction exists before the review is confirmed. Confirm today marks the receipt and still inserts no transaction | Partial | Confirm marks are tested. The step from a confirmed receipt to a spend row is untested. |
| Add food lines to the pantry, then delete the receipt | Pantry stock is its own confirm. Deleting the receipt leaves that stock decision as the user made it | Untested | |
| A known shop name, including a symbol-heavy alias | The longer alias wins. A user correction beats the lexicon | Tested | `MerchantLookupTest` |
| Ask what was spent on food, yesterday, or at a shop, including an empty history | The sum, or a clear empty answer | Tested | `RuleAnswersTest` |

### 22.8 Pay (section 7.3)

| Behaviour | Expected | Coverage | Test |
|---|---|---|---|
| Ask what is due, who owes the user, or what the user owes, including nothing due | Combined answer with overdue labels | Tested | `RuleAnswersTest.dueAll`, `whoOwesMe`, `whatIOwe` |
| A bill due today, tomorrow, in 3 days, overdue, or far away | Today and overdue alert. Tomorrow and 3 days watch. Far bills stay quiet. Several overdue bills are counted | Tested | `InsightRulesTest` |
| Mark the same bill paid twice | The second tap does nothing. One expense, one rolled due date | Untested | |
| Mark an inactive bill paid | Nothing changes | Untested | |
| Say "electric bill 15 monthly", weekly, yearly, or due tomorrow | A bill with that period and due | Tested | `QuickParseIntentTest` |
| A subscription renews on the 31st | The 31st is kept when the month has it, and clamped to the last day when it does not | Tested | `SubscriptionChargesTest.nextChargeDate_clampsWhenTheMonthHasNoAnchorDay` |
| The amount is unknown, or a known zero | Unknown is not shown as $0. Zero stays zero. Blank stays unknown. A negative parse is rejected | Tested | `SubscriptionChargesTest`, `SubscriptionRepositoryTest`, `SubscriptionAmountTextTest` |
| Cancel a subscription | Status becomes cancel-requested. The merchant is not contacted. Another user's row and a soft-deleted row stay untouched | Tested | `SubscriptionRepositoryTest` |
| Say "I owe Alex 40" or "Jordan owes me 25" | Direction and amount match the sentence | Tested | `QuickParseIntentTest` |
| Pay more than the remaining debt, pay zero, or pay a settled debt | Overpay settles it and the remaining stays at zero. Zero and a settled debt do nothing | Untested | |
| A payment against what is owed | A transaction linked to that debt. The debt is not copied into a second debt | Untested | Today's payment only changes the debt amount. |
| File a clip that is a bill or a debt | Grounded amount and currency from the clip text | Tested | `ClipJsonTest` |

### 22.9 Budgets and goals (section 7.4)

| Behaviour | Expected | Coverage | Test |
|---|---|---|---|
| Ask what is left in food, or name the overspent category | Left-of, or over-by. The over-budget name is the category | Tested | `RuleAnswersTest` |
| Set a budget of 0 | Treated as no limit | Partial | `InsightRulesTest.adviceNoLimit`. Saving a zero budget is untested. |
| Over budget, near the limit, or on pace | Alert, watch, or no pace warning. Very early in the month skips pace and reallocation. Reallocation never takes from the category itself | Tested | `InsightRulesTest` |
| Say "set a food budget of 300" | A budget suggestion | Tested | `QuickParseIntentTest.parseVoiceIntent_budget` |
| Ask about a goal by name, or with no goals | Percent and remaining, or a clear empty answer | Tested | `RuleAnswersTest.goalsOverview`, `goalByName` |
| Say "save 200 for a bike" | A goal suggestion | Tested | `QuickParseIntentTest.parseVoiceIntent_goalSave` |
| The goal is already reached and the user adds more, or adds a negative amount | Saved can pass the target. Progress stays at 100%. Remaining stays at zero. A negative add does not reduce the saved total | Untested | |
| A target of 0 | Progress stays at 0% | Untested | |
| Delete a budget that still has transactions | The budget is soft-deleted. The transactions stay | Partial | Insights ignore a deleted budget. The delete itself is untested. |
| Ask income versus expense, savings rate, runway or balance | The monthly arithmetic matches the rows | Tested | `RuleAnswersTest` |

### 22.10 Wishes (section 9.5)

Not built. No table, screen or speech route.

| Behaviour | Expected |
|---|---|
| Save a name and no price | The row shows "Price unknown". Status stays Want. |
| Start saving with no price | The action stays disabled. No goal is created. |
| Start saving with a price and a wanted-by month | A linked goal uses that name, price and month. From speech this is a high-risk confirm. |
| The linked goal moves | The wish shows that progress by reading the goal. |
| Buy soon | The name is added to the shopping list. The wish stays Want. A second tap does not add a duplicate line. |
| Bought, then close the spend draft | The wish is unchanged. |
| Bought, then save the draft | `transaction_id` is set and the status becomes Bought. |
| Drop it | It leaves the main list and sits under the collapsed header. |
| The model fills in a price | The price stays empty. |
| Say "I might buy a camera someday" | A wish. It is not a shopping item, a goal or a transaction. |
| Tap the link | The phone's browser opens. The app does not fetch the page. |
| Priority outside 1–3, or a blank name | Rejected. Default priority is 2. |
| Household sharing | Private until the user opts in. |

### 22.11 People (section 8)

| Behaviour | Expected | Coverage | Test |
|---|---|---|---|
| Delete a person | They leave the list. The row is soft-deleted. Another user's person is left alone | Tested | `PeopleRepositoryTest` |
| Delete a person who has memories | New memories are rejected. Existing rows stay stored and hidden | Partial | `MemoryRepositoryTest.memoryForDeletedPersonIsRejectedAndExistingRowsStayHidden`. Promises are not covered. |
| Delete a person who has promises | Those promises leave the person's list | Untested | |
| Two people with the same name | Both are kept. Nothing merges them | Untested | |
| Blank name | Rejected. Nothing stored | Tested | `PeopleRepositoryTest.blankNameIsRejectedAndNothingIsStored` |
| A new person | Private | Tested | `PeopleRepositoryTest.addedPersonStaysPrivate` |
| A memory with no person, blank text, or a person the user does not own | Rejected | Tested | `MemoryRulesTest`, `MemoryRepositoryTest` |
| A promise with no person, or with no due date in the words | Allowed. The due date stays empty | Tested | `CommitmentRepositoryTest.commitmentMayOmitPersonId`, `nothingInventsADueDate` |
| Open Birthday, Appointments with this person, or Owed with this person | Read-only links on the person | Not built | |
| Copy a memory to Vault | A one-off snapshot. Editing one leaves the other as it was | Not built | |

### 22.12 Jobs (section 8)

| Behaviour | Expected | Coverage | Test |
|---|---|---|---|
| Paste a post with a company and a role, in several layouts | Both are found. The original text is kept | Tested | `JobPasteParserTest.companyAndRoleAreFound`, `notesKeepTheOriginalText` |
| Paste with no company, empty text, or gibberish | Needs help. No invented company | Tested | `JobPasteParserTest.missingCompanyNeedsHelp`, `gibberishNeedsHelp` |
| Share a newsletter that mentions hiring | Needs help. It does not become a clean application on its own | Untested | |
| The paste includes a URL | One job. The URL is the source and is kept out of the company and the role | Tested | `JobPasteParserTest` |
| Several jobs in one paste, or one labelled block | One job per line. A labelled block stays one job | Tested | `JobPasteParserTest` |
| Interview, deadline or applied dates, or status words such as offer and rejected | Dates and status are read. No dates means no extras | Tested | `JobPasteParserTest` |
| A job listing that mentions a salary | Classified as other, not as a receipt | Tested | `DocumentClassifierTest.classify_jobListingWithSalary_isOther` |
| Add interview | An appointment with `job_id`. See 22.4 | Not built | |

### 22.13 Pantry and shopping (section 8)

| Behaviour | Expected | Coverage | Test |
|---|---|---|---|
| Check off a shopping line | Stock updates only when the quantity is known. No expense is created. A receipt is what creates an expense | Partial | `InventoryQuantityTest` covers stock. No test asserts the absence of a transaction. |
| The line quantity is unknown | The line is checked. No quantity is invented | Tested | `StockQuantityTest.checkOffDoesNotInventAQuantity` |
| Adjust an unknown quantity, or a known one | Unknown stays unknown. A known quantity takes the delta | Tested | `StockQuantityTest`, `InventoryQuantityTest` |
| Quantity is zero | Low and out. An unknown quantity is neither | Tested | `StockQuantityTest.unknownQuantityIsNotLowOrOut` |
| The adjustment would go below zero | This plan does not say. Today's adjust can store a negative quantity | Untested | |
| A fractional quantity, such as 0.5 kg | Stored and adjusted as entered | Untested | |
| Expires today, or already expired | "Today" or "N days ago" | Untested | |
| Add low items to the shopping list | Known low items only, with a blank line quantity. Unknown, plenty and missing are skipped | Tested | `InventoryQuantityTest` |
| Ask for a meal when a pantry amount is unknown | The prompt says the amount is unknown | Tested | `AskPromptsTest.mealPromptNeverStatesAnUnknownAmount` |
| Soft-deleted stock, or a blank name | Hidden. A missing name does not insert a row | Tested | `InventoryQuantityTest` |

### 22.14 Wardrobe (section 8)

| Behaviour | Expected | Coverage | Test |
|---|---|---|---|
| Blank colour, type, laundry or photo | Stored as unknown, or the photo path left empty | Tested | `WardrobeRulesTest`, `WardrobeRepositoryTest` |
| A named colour, seasons and a photo | Kept as entered | Tested | `WardrobeRulesTest.namedColorIsKeptAndNotReplaced` |
| Log the same clothes on the same day | Rejected as a duplicate. A deleted wear can be logged again. A different day or different clothes is allowed | Tested | `WardrobeRulesTest`, `WardrobeRepositoryTest` |
| Log a garment the user does not have, or a blank name | Rejected | Tested | `WardrobeRepositoryTest` |
| Ask for an outfit when the weather is missing | Weather is left out of the prompt. Invented garment ids are dropped | Partial | `AskPromptsTest.outfitDropsInventedIdsAndEmptyOutfits`. The missing-weather sentence is untested. |
| Outfit ideas on Home or the widget | They stay on the wardrobe screen | Untested | |
| Log what was worn during a trip | The wear is stored by date. The trip does not block it | Not built | |

### 22.15 Household (section 8)

| Behaviour | Expected | Coverage | Test |
|---|---|---|---|
| Create a household with a blank or over-long name | Rejected. A valid name makes the creator the owner. A non-member sees nothing | Tested | `HouseholdRepositoryTest`, `HouseholdMembershipTest` |
| The only owner tries to leave | Refused. A member can leave. An owner can leave when another owner remains | Tested | `HouseholdRepositoryTest.leave_soleOwnerCannotLeaveAndAMemberCan` |
| A member tries to add or remove someone, or an owner tries to remove another owner | Refused. A removed member can rejoin. An unknown role is rejected. A soft-deleted owner cannot add anyone | Tested | `HouseholdMembershipTest` |
| A member opens the shared shopping list | They see the household list | Untested | |
| A member looks for a birthday, a password, a locked folder, an achievement or app time | Those stay with the owner. Household sharing does not include them | Not built | |

### 22.16 Birthdays (section 9.1)

Not built, apart from the existing yearly-event reminder search named in 22.1. The ladder in section 9.1 is the spec these tests have to pin.

| Behaviour | Expected |
|---|---|
| Add a birthday by typing a new name | A person is created. The birthday points at that person. The name is edited in one place. |
| 29 February in a leap year, and in the following year | Stored as 29 February. In a non-leap year it behaves as 28 February and the row says so. |
| No birth year, including a contact imported that way | Stored with no year. Age is omitted. The year is never guessed. |
| Celebration at 00:00 and at 18:00 on 14 November | The nine instants match the table in section 9.1. |
| Switch one rung off | That instant is absent. The others stay. |
| Save a birthday after some rungs have already passed | Those rungs are skipped. They do not fire late. |
| The phone was dead, then boots | At most one catch-up, for the latest miss inside 6 hours. |
| Two rungs land in the same minute | One notification. |
| The reminder hour is 09:00 and the user changes timezone | Every pending instant is rebuilt in the new local time. |
| 1 March has no 31st to count a month back from | The 1-month rung uses the last day of the previous month. |
| Import from contacts | A checklist. Nothing is written until confirm. A person who already has that date is shown as a duplicate, not silently added again. |
| Delete the person | The app asks "Keep as a name only?". Yes keeps a person row with just the name and keeps the birthday. |
| The gift note | Private. It is left out of Ask, search context and the model. |
| Put it on the calendar, as a task, or as a month dot | It stays off all three. |
| Snooze | 1 hour, and only on the clock-level rungs (5 hours, 30 minutes, 10 minutes, at the time). |
| A household member | Cannot see the birthday or the note. |

### 22.17 Achievements (section 9.2)

Not built.

| Behaviour | Expected |
|---|---|
| Blank title, or a title over 120 characters | Rejected. |
| The date is left blank | Defaults to today. |
| The date is in the future | Rejected. |
| Say "I will finish the thesis" or "I finished the thesis" | The future sentence becomes a task or a goal. The past sentence becomes an achievement suggestion. |
| Note over 1 000 characters, or a fourth photo | Rejected. |
| Set a source task by hand | Refused. Only "Save as achievement" on a done task sets `source_task_id`. |
| Look for points, a streak, a reminder, a category or a place on the agenda, Home or a widget | None of those exist. |
| Share with the household | Stays private. |

### 22.18 Trips (section 9.3)

Not built.

| Behaviour | Expected |
|---|---|
| End date before the start | Rejected. |
| Start and end on the same day | Allowed. |
| Open the list before, during and after the range | Status is Planned, Ongoing or Done from today's date. It is not stored, so a timezone change cannot leave a stale status. |
| The trip crosses a daylight-saving change | The date range still covers the right civil days. Reminders stay at the reminder hour. |
| Both reminder switches on | One notification a week before and one the day before, at the reminder hour. No daily ping during the trip. |
| Look for a calendar block or a budget created by the trip | None. The during-this-trip list is read-only links to appointments and blocks that already fall in the range. |
| Spend during the trip, in the first release | Unlinked. `trip_id` on transactions waits for phase 4. |
| Share with the household | Off until the user opts in. |

### 22.19 Vault (section 10.1)

Not built as folders. Notes, clips and a 20 000 character chat cap exist beside it.

| Behaviour | Expected | Coverage | Test |
|---|---|---|---|
| Folder name empty or over 40 characters, or a third nesting level | Rejected. Depth stops at one level under root | Not built | |
| Delete the Notes or Inbox folder | Refused. Notes can be renamed | Not built | |
| Pin a fifth folder | Refused. Four pinned folders sit above the list | Not built | |
| Open a locked folder | Biometric or screen lock. The unlock lasts 2 minutes. Removing the lock requires an unlock first | Not built | |
| Search, Ask, the digest, a widget or a sync of a locked folder | No hit, no context, no upload. Locked means this device only | Not built | |
| Screenshot or screen-read a locked folder | The window is secure and the reader refuses | Not built | |
| Upload a file, or share a 40 MB file | Document picker only. Synced files stop at 25 MB. Locked files are limited by the phone's storage | Not built | |
| Write a note over 20 000 characters | The body is capped | Partial | Chat text is cut at 20 000. A note-body test does not exist. |
| File an inbox clip | A note in the chosen folder. The clip keeps the original text and points at the note. Filing twice does not create a second note | Partial | `clip.file_note` exists. The folder target and the double-file are untested. |
| Ask about a selection, then leave without Save answer | No note is written | Tested | `NotebookJsonTest.storedActionsAreInferredAndNotAppliedUntilATap` |
| Search | Folder names, titles, bodies and file names. File bytes stay closed. Locked folders stay out | Not built | |

### 22.20 Passwords (section 10.2)

The store is not built. A few gates around secrets already exist and are tested.

| Behaviour | Expected | Coverage | Test |
|---|---|---|---|
| Open Passwords, or cancel the biometric prompt | Locked until the phone unlock succeeds. Cancel leaves every secret encrypted | Not built | |
| Read the list | Titles and sites only | Not built | |
| Copy a secret, then switch apps | A sensitive clipboard clip, cleared after 30 seconds | Not built | |
| Generate a password | Local, length 12 to 32. No network | Not built | |
| Two rows share a secret, or a change-every date has passed | The health strip says so, from hashes held in memory. The change notification says "A password is due for a change" and names nothing on the lock screen | Not built | |
| Export, then import on another phone | A passphrase file. Import merges on title and site and asks before overwrite | Not built | |
| No export yet | The screen keeps the line "Stored only on this phone." | Not built | |
| Screen-read or screenshot the Passwords screen | Refused | Not built | |
| A password field on some other app's screen | Dropped from the blocks the panel reads | Tested | `ScreenBlocksTest.dropsPasswordAndShortNodesAndSortsReadingOrder` |
| A clip flagged as a password manager, or text that is a card, an OTP or a private key | The password-manager clip is not saved. The others need a confirm or are blocked | Tested | `ClipRulesTest` |
| Say "the wifi password is on the fridge", or dictate a PIN | One suggestion, Open Passwords. The words are kept nowhere, including voice history | Untested | Today's rules table files that sentence as a note. |
| Search for a password's title | No password row appears in search, Ask, notes, widgets, logs or sync | Not built | |
| Delete a row | The ciphertext is gone. There is no trash | Not built | |
| An assistant action whose type contains "password" | The registry test fails the build | Not built | |

### 22.21 App time (section 11)

Not built.

| Behaviour | Expected |
|---|---|
| Open the screen before Usage Access is granted | One sentence and an Allow button. No sample numbers. |
| Grant access, then use an app across midnight | The session splits at local midnight into two days. |
| Revoke access halfway through the day, or leave the job off longer than Android keeps events | Collection stops. The missing days show as a gap. They are not estimated. |
| Launcher, system UI and the keyboard | Excluded. This app is included. |
| An app is uninstalled | The old rows keep the last known label. |
| Rows older than 400 days | Pruned by the same worker. |
| Ask a question about app time, or sync the table | Refused. The rows stay on the phone. |
| A focus session is running | Its minutes stay in Focus. They are not added here. |

### 22.22 Settings and widgets (section 13)

| Behaviour | Expected | Coverage | Test |
|---|---|---|---|
| Turn the reminders master off, or one family off | That family is not scheduled. Hiding the notification channel is a separate Android setting | Not built | |
| Exact alarms are not allowed | Settings shows a warning and a button to the system page. The engine uses inexact alarms | Untested | |
| Widget private mode | Amounts render as a mask | Untested | The flag exists in the widget state builder. |
| The Today widget and Home, same data | The same Next card and the same safe-to-spend figure | Not built | The Next rule itself is not built. |
| Check a task on the widget while that task is open in the app | Both show the completed task after refresh | Untested | |
| The widget still shows a quote, habit dots, a jobs count or a focus timer | Those are gone in this plan | Untested | |
| Tap the widget mic and cancel, or say nothing | Dropped. No sheet | Tested | `WidgetMicTest.blankSpeechIsDropped` |
| Say a meal, outfit or shopping question into the widget mic | A suggestion to save, or the notes fallback. The widget does not answer the question | Partial | The widget path only categorises. No test feeds it a meal question. |
| Speech with nothing to categorise | A note containing the exact words | Partial | Unmatched becomes a note in the suggestion builder. No dedicated test. |

### 22.23 Speech (section 14)

| Behaviour | Expected | Coverage | Test |
|---|---|---|---|
| Empty or cancelled speech | Dropped | Tested | `WidgetMicTest.blankSpeechIsDropped` |
| Bangla alarm or reminder | The next clock time, including "tomorrow morning" | Tested | `QuickParseIntentTest` |
| A sentence that matches nothing | One unmatched card. It is not filed as an expense | Tested | `QuickParseIntentTest.parseVoiceIntent_unmatchedIsNotATransaction` |
| The rules are confident | The cloud model is not called. If the cloud fails, or cloud fallback is off, the rules answer stands | Tested | `VoiceRoutingTest` |
| The cloud returns an unknown intent or a bad amount | That item is dropped | Tested | `AiResponseValidatorTest.voiceItems_dropsUnknownIntentsAndBadAmounts` |
| An answer invents a price, a currency amount or a date | The answer is rejected | Tested | `NotebookJsonTest` golden cases |
| A receipt or a meal is missing a quantity or a price | The value stays null. The meal prompt says the amount is unknown. Invented outfit ids are dropped | Tested | `CaptureJsonTest`, `AskPromptsTest` |
| Dismiss the suggestion sheet | Nothing is written | Untested | |
| Tap Save twice | One write | Tested | `ExecutorTest.rerunDoesNotApplyTwice` |
| Each row of the routing table in 14.1, including wish, birthday, trip, appointment and achievement | The suggestion named in that row, and a missing date, price, year or amount stays empty | Not built | Money, task, bill, debt, goal and alarm routes are tested today. The new rows are not. |
| A birthday or a trip sentence | A birthday or a trip. No calendar row | Not built | |
| Words that contain password, passcode, PIN, or a password-shaped token | Open Passwords. Nothing stored. See 22.20 | Untested | |
| The screen has no readable text | On-phone OCR, then "Can't read this screen" if that is empty too | Untested | |
| The user is offline and asks a question | On-device rules answer when they can. Otherwise a failure, not a made-up plan | Partial | `VoiceRoutingTest`, `OrchestratorTest.garbageIsAFailureNotAPlan`. The Ask screen's offline state is untested. |

### 22.24 Reminders (section 15)

Event reminder math and alarm next-fire are tested. The single rolling alarm is not built.

| Behaviour | Expected | Coverage | Test |
|---|---|---|---|
| 200 birthdays with every rung on | At most one pending exact alarm for the engine | Not built | This is the acceptance check in section 21. |
| Exact alarms denied | Inexact fallback, and the Settings warning in 22.22 | Untested | |
| Boot, clock change, or timezone change | The table is rebuilt and the next alarm is armed | Untested | The receivers are registered. |
| Two rows due in the same minute | Both delivered in one wake, inside the 30-second batch | Not built | |
| Do Not Disturb | The channel still schedules. Android decides the sound | Untested | |
| Tap a notification after the source was deleted or completed | No crash. The row is skipped | Untested | |
| More than 10 reminders on one event | Capped. See 22.2 | Tested | `EventReminderRulesTest` |
| A completed, disabled, undated or alarm-kind event | No notification reminder. Alarms ring through their own service | Tested | `EventReminderRulesTest.nextFire_skipsCompletedDisabledUndatedAndAlarms` |
| A weekly class with a skipped date, or an until-date in the past | The next fire follows the series, or never fires | Tested | `EventReminderRulesTest` |
| Birthday, trip, password-change and pantry rows | They join the engine on the schedule in section 15.2. Debt, leave-by, plan-block and habit nudges stay on their old schedulers until step 12 | Not built | Those old schedulers have no unit tests. |

### 22.25 Assistant (section 17)

| Behaviour | Expected | Coverage | Test |
|---|---|---|---|
| A low-risk action | Applied, and logged only after the executor returns | Tested | `AssistantSpineTest.lowRiskIsAppliedAndLoggedOnlyAfterExecutor` |
| A medium action, even if the model calls it low | Stays proposed until the user approves. Logged once | Tested | `ExecutorTest.modelClaimedRiskIsIgnored` |
| An unknown type, or bad arguments | Rejected | Tested | `ExecutorTest.unknownTypeAndBadArgsAreRejected` |
| The executor fails | Recorded as failed. The app does not say it was applied | Tested | `ExecutorTest.failureIsRecordedNotReportedAsApplied` |
| Offline garbage instead of a plan | A failure | Tested | `OrchestratorTest.garbageIsAFailureNotAPlan` |
| "Coffee at Lidl" with Starbucks also in the history, or a debt for "Sa" when the person is Sarah | Lidl is kept and Starbucks dropped. Sa does not match Sarah | Tested | `ContextSelectorTest` |
| The new actions in section 17.1 | Risk tiers as in that table. `wish.start_saving` and `wish.mark_bought` wait for an explicit confirm | Not built | |
| Context includes a locked folder, a password, app time, or a birthday's gift note | Left out. Birthdays within 30 days, trips within 60 days, upcoming appointments and wish names can be included, without the gift note | Not built | |
| The panel is called on Passwords or a locked folder | The read is refused | Not built | |
| Save from the panel | The clip files into the last-used Vault folder. Ask stores a thread under Ask → History and writes a note only after Save answer | Partial | Save-until-tap is tested (`NotebookJsonTest`). The Vault folder target is not built. |
