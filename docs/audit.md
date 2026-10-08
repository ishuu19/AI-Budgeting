# LedgerAI — Audit

Status: Findings from reading the source. Nothing was built or run.
Severity: **H** breaks a feature or risks data, **M** wrong or inconsistent behaviour, **L** polish.
Part C (redundancy and the calendar merge) is below. What was built for it is in section F.

---

## A. Not built / missing

| # | Item |
|---|---|
| A1 | **The new Dashboard is not built.** Home is still the old totals layout from [dashboard-plan.md](dashboard-plan.md): no insight stack, health score, spend strip, bills, budget progress or next alarm. |
| A2 | Voice has no calendar events, no budgets, no edits or deletes by voice, no queries, one item per utterance. |
| A3 | No voice log list. Audio is deleted after transcription and transcripts are never saved, so nothing can be edited. The "Log" tab is an unrelated read-only check-in list. |
| A4 | No sign-in after "Skip", so a local user can never enable sync. |
| A5 | No notification permission request anywhere. |
| A6 | No way to view or edit Routines, Alarms or Notes. They have no entry point. |

---

## B. Findings

### B1. Navigation and global

| Sev | Where | Problem |
|---|---|---|
| H | AppNavigation:197, LifeContainerScreen:25 | Alarms has no entry point. Notes and Tasks callbacks are accepted and never used. |
| H | RoutinesScreen.kt | 497 lines with no route. Voice saves routines that can never be seen. |
| H | Manifest:14 | No `POST_NOTIFICATIONS` request. Alarms and reminders are silent on Android 13+. |
| H | all screens | No `rememberSaveable`. Sheets, drafts, filters, tab and Focus timer reset on rotation. |
| M | AppNavigation:137 | Tab highlight fails on sub-screens, so Money is not selected inside Spend, Budget, Bills. |
| M | AppNavigation:106-131, MainActivity | Widget extras read once, no `onNewIntent`. A widget tap while the app is open is ignored. |
| M | AppNavigation:107-113 | Life sub-tab from the widget works only when `openCalendar` is also set. |
| M | AppNavigation:206 | Focus topic and `blockId` come from the activity intent, not the route. Plan always opens "Focus". |
| M | Ledger.kt:263-286 | `LSheet` does not scroll and ignores the keyboard. Long forms clip. |
| M | Ledger.kt:246 | `LChip` is about 34dp tall, below the 48dp target, with no selected semantics. |
| L | 4 files | Delete-confirm dialog copied four times although `ConfirmDelete` exists. |
| L | Spend, Budget, Debts | Snackbars hard-code `bottom = 88.dp`. Tasks and Notes use Toasts instead. |
| L | SharedComponents:101,155 | Hard-coded `0xFFFFC9C2` instead of `L.Danger`. |
| L | Ledger.kt LScreen | Title is not a heading for TalkBack. |

### B2. Home (Dashboard)

| Sev | Problem |
|---|---|
| M | Every box is the same size, colour and radius. Hero, then two equal stats, then equal cards. No hierarchy. |
| M | Three large numbers (hero, Spent, Health) against the "one large number" rule. |
| M | Missing: bills due, budget remaining, next alarm, today's agenda, goals, debts. |
| M | "Soon" rows have no `onClick`. Recent rows open the list, not the item. |
| M | Insight cards are paragraphs with an uncapped headline and two different text sizes. |
| M | `WeekBars` has no labels, values or semantics, and vanishes when all days are zero. |
| L | Search, Mic and Settings icons duplicate the tab bar. The logo and greeting use a full row. |
| L | Off-scale spacing (14, 18, 28, 36dp). Hand-built boxes instead of `LStat` and `LCard`. |
| L | No error state, no quick-add on Home, daily quote sits on You instead. |

### B3. You (Settings)

Seven chunks plus sign out.

| Sev | Problem |
|---|---|
| M | Five voice-engine rows (about 72dp each) should be one row opening a sheet. |
| M | Merge Profile and Sign out into one card. Move the quote to Home or drop it. |
| M | App section holds six rows. Group Money (currency, tracking, on hand) and Alerts (switch plus threshold). |
| M | Sync, Privacy and Version belong in one About row. Sync row does nothing. Version is hard-coded. |
| M | Currency picker is a raw `Dialog`, not `LSheet`. Changing currency does not change `money()`. |
| M | Track and Threshold sheets lose state on rotation. |
| L | "Ask AI" row here duplicates the Money hub tile. Privacy text is hard-coded. |

### B4. Voice

| Sev | Where | Problem |
|---|---|---|
| H | VoiceRecorderViewModel:401-417, WidgetVoiceCaptureActivity:86-103 | Reminder ignores the spoken date and label, so "tomorrow 9am" saves for today. The widget turns every utterance into a task titled with raw text. |
| H | VoiceRecordingService:176-181 | No matches means no broadcast and no `stopSelf`. The service and the invisible activity hang. |
| H | VoiceRecorderViewModel:164,537 | Every `MediaRecorder` failure reads "Mic blocked". Permanent denial is not detected and there is no settings link. |
| M | VoiceRecorderViewModel:243-259 | Recordings under 1s throw and are still transcribed. Mic keeps running when the app is backgrounded. |
| M | VoiceRecorderScreen:152-166 | Error state has no Retry. Can be reached with an empty transcript. |
| M | VoiceRecorderScreen:540,691,737 | Date fields are free text (`yyyy-MM-dd HH:mm`) with no picker and no error text. Alarm hour and minute are separate text fields. |
| M | VoiceRecorderScreen:169-176, 412-445 | Edit sheets save immediately. Custom alarm repeat shows no chip selected. |
| M | QuickParse:256, AiRepository:113 | Anything unmatched becomes a transaction ("No amount"). Cloud path does not know Bill, Debt or Goal. |
| L | VoiceRecorderScreen | "Saved" shows 1.4s with no link or undo. Engine chip is not tappable. Hard-coded 280dp. No 60s countdown. |
| L | VoiceRecordingService:222 | Notification says "Speak your transaction" for every intent. |

### B5. Other screens

| Screen | Sev | Problem |
|---|---|---|
| Money hub | L | Nine equal tiles with no numbers or badges. |
| Spend | M | Prev and Next chips with no month shown, Next unbounded. Four stacked controls before the list. |
| Today | M | Raw `OutlinedTextField`, no number keyboard, silent failure on bad input. Loads forever if the guide throws. Speculation is fixed to an expense one week out with no list, edit or delete. Reasons shown as helper rows. |
| Budget | M | "Ask" button forced to 40dp high. "Copy" is ambiguous. No duplicate-category check. |
| Bills | M | Paid button is 32dp. Double tap creates two expenses, not atomic. Frequency chips overflow. Monthly total not reactive. |
| Debts | M | Due date is free text. Amount shows "1000.0". Direction by colour only. Phone and email dropped on add. |
| Goals | M | Hard-coded emoji, which breaks the no-emoji rule. Deadline free text. "+" is 36dp with the same description on every card. Missing `key(goal.id)`. |
| Forecast | M | Projected balance ignores cash on hand and disagrees with Home. Error "Unavailable" has no retry. Overdue bills excluded. `now` goes stale. |
| Insights | M | Donut has no description or legend. Six repeating slice colours. Category rows do not open Spend. |
| Life | M | Tab state resets on switch. Each tab nests its own `LScreen`. |
| Calendar | M | Today cell is dark text on dark green. Adjacent-month days show "Nothing" while events exist. Edit on task-derived events does nothing silently. Import offered twice. |
| Tasks | H | Delete is immediate with no confirm or undo. |
| Tasks | M | Time picker is always on and defaults to 9:00, so a task can never have no time. Sheet is a very tall form. "Late" duplicates "Today". Own search duplicates global Search. |
| Notes | M | Delete without confirm. Closing the editor discards text. Four stacked AI buttons with no offline state. |
| Alarms | M | Clock stale after time passes. AM/PM hidden in the list but forced in the picker. Rows hide repeat days. Delete immediate. Custom-tone chip does nothing. |
| Plan | H | Study sheet cannot be dismissed (`onDismiss` does not clear `showStudy`). A plan is saved before confirm, leaving orphans. |
| Plan | M | Blocks cannot be edited, completed or deleted. "Add" sheet abuses its buttons as a menu. Habit save fails silently. Hand-built FAB. |
| Jobs | M | Row `onClick = {}`: no edit, delete, status change or link open. Parsing splits on "-", breaking hyphenated names. Cryptic labels (Week, Rate, Talks). |
| Focus | M | Fixed 50 minutes, ignores the block, resets on rotation, not tied to the service. "End" cannot tell finish from abandon. |
| Ask | M | Failures become chat history and are sent back. No retry. `$` hard-coded. History lost on leaving. |
| Search | H | Results cannot be opened. Only four sources searched. No debounce. |
| Login | M | Non-`L` button, error truncated to one line, "Skip" unclear. |

### B6. Security and backend

| Sev | Where | Problem |
|---|---|---|
| H | Manifest:99, FocusForegroundService:35 | No `foregroundServiceType`. Crashes on target 34. `START_STICKY` restart skips `startForeground`. |
| H | ai-proxy/index.ts:345 | Rejects anon keys over 200 characters. Legacy keys are about 208, so the function fails. |
| H | DatabaseModule:52, LedgerDatabase:40 | `fallbackToDestructiveMigration` and no exported schema. A missed migration wipes finance data. |
| H | SyncRepository:88 | Pull has no pagination or order. Rows beyond 1000 are skipped while the cursor advances. |
| H | SpendGuideRepository:40, WidgetStateBuilder:55 | Read `BudgetEntity.spent`, which nothing maintains. "Safe today" is inflated and widget month % is always 0. |
| H | AiRepository:453 | `completeRaw` sends type CHAT. Forecast, timetable, schedule and nudge JSON is forced into `{reply}`. |
| H | NoteScanWorker:25 | Notes tagged `#nudge` go to the cloud daily with no consent and no dedupe. |
| M | AuthRepository signOut | Room is not cleared. The next user sees the last user's data and their sync batch is rejected. |
| M | UserSession:23 | Tokens stored in plaintext DataStore. |
| M | index.ts:377 | Clients may supply their own system prompt, making the proxy a free general LLM. Rate limit is per isolate and resets. |
| M | 002 migration | Reminders update policy does not check task ownership. The max-10 trigger is not concurrency safe. Server never sets `updated_at`. |
| M | 001_init:61 | Unique budget key conflicts with upsert on id, so a batch fails on every retry. |
| M | SyncTime, Converters | Local times synced as UTC. No time zone or time-set handler re-arms alarms. |
| M | TaskRepository:134 | Every save soft-deletes and recreates reminders and leaves tombstones. Not in one transaction. |
| M | Entities | Money is `Double`. Currency ignored in every sum. |
| M | AiRepository:279-421 | Note text, merchants and history are concatenated into prompts without delimiters. Validator allows null or negative amounts, any category, uncapped strings. |
| M | AlarmScheduler | No exact-alarm or full-screen permission check. Repeating alarms are re-armed only on dismiss. |
| M | TaskReminderScheduler | Reminders use WorkManager delays, so Doze delays them. |
| M | BudgetCheckWorker, BillReminderWorker | Re-notify every 6 and 12 hours with no dedupe. |
| M | JobShareActivity | Exported, saves any shared text without confirm, unmanaged scope. |
| L | Manifest | `READ_CONTACTS` unused. Both exact-alarm permissions declared. Broad FileProvider roots. |
| L | proguard-rules.pro | No JNA keep rule (Vosk may break in release). Blanket okhttp and retrofit keeps. |
| L | Daos | `REPLACE` conflict strategy. No indices on sync columns. Seeder `clearAllTables()` can race. |
| L | Workers | No network constraint on insight and note workers. Boot receiver ignores `LOCKED_BOOT_COMPLETED`. |
| L | Credentials | Live keys sit in local `secrets.properties` and `.env` (not committed). PLAN.md says rotation is still pending. |

### B7. Widgets

| Sev | Problem |
|---|---|
| M | Focus actions pass `blockId = 0`, so the Focus widget never opens the real block. Private mode sends "•••" as the topic. |
| M | `runBlocking` inside `provideGlance`. The guide writes to the DB on every render. All errors swallowed into zeroed data. |
| M | Quick Actions is 1 cell high with a 40dp minimum, but its buttons need 64dp plus padding. The fifth button clips. |
| M | Config is global, not per widget. Config activity blocks the UI thread. Two widgets have no config. |
| L | Light theme still uses dark tiles. Icons are untinted framework drawables with no descriptions. |
| L | Preview layout is one static view with a fake "$42". |
| L | `widget_bg.xml`, `widget_mic*.xml` and `WidgetTaskToggleCallback` are unused. |
| L | No widget refresh on data change or at midnight. |

### B8. Build and repo

| Sev | Problem |
|---|---|
| M | compileSdk and targetSdk 34. Play requires 35 or higher now. |
| M | Old dependencies: AGP 8.3.2, Kotlin 1.9.24, Compose BOM 2024.06, supabase-kt 2.6.1, ktor 2.3.12. |
| M | Tests cover parsers and mappers only. No DAO, migration, repository, worker or widget tests. |
| M | `compile-verify.txt` (27 KB build log) is tracked. `compile-agent-a.log` also sits at the root. |
| L | Unused: `kotlin-compose`, `sherpa-onnx`, duplicate supabase aliases, Sherpa classes. |
| L | No `gradlew` for macOS or Linux, no CI. `versionCode` 1, no shrinkResources. `ksp.incremental=false` slows builds. |
| L | `.gitignore` misses `.kotlin/`, `.claude/`, `.env.*`. Root `logo.png` and `scripts/grok*` tracked. |

---

## C. Redundancy and calendar merge

### C1. Dead and duplicate code

| Sev | Where | Problem |
|---|---|---|
| H | RoutinesScreen, RoutineTimetable | Never composed. Voice and import still create routines nothing displays. |
| M | CourseRepository, CourseEntity, CourseDao | Injected nowhere. `courseId` is always null on tasks, slots and events. |
| M | FocusSession, LocationPoint, Visit DAOs | Provided, never used. Focus persists nothing. |
| M | AiSchemaTypes, AiResponseValidator, AiDtos | TASK and ALARM response types, `SpendGuideExplainDto` and `parseVoiceAudio` are unused. |
| M | SpeakQuoteActivity | Registered, never launched, since the `HomeWidget` rewrite. |
| M | `voiceOnlyWidget` setting | Toggle has no effect. No widget reads it. |
| M | SharedComponents.kt | Nine composables, all unused. Whole file is dead. |
| M | WidgetActions, WidgetComponents | `openVoice`, `openBills`, `openJobs`, `startFocus`, progress bar, chip unused. `quoteLine` computed, never shown. |
| M | Five speech engines | Android, Sherpa, Zipformer, Whisper, Vosk. Sherpa dependency is commented out and always fails. |
| L | Color.kt, strings.xml, colors.xml | Dozens of unused constants and about 44 unused resources. |
| L | Repositories and DAOs | About 30 unused methods (full list in git grep of `observeIncomplete`, `clearForSlot`, `markGaps`, `getActiveDebts`, and similar). |
| L | Repo root | `compile-verify.txt`, `compile-agent-a.log`, `scripts/grok*` leftovers. |

### C2. Bugs from overlapping task, event and routine paths

| Sev | Where | Problem |
|---|---|---|
| H | SyncRepository:147 | Pulling a task resets `eventKind` and `courseId`. Exams and events become plain tasks. |
| H | Calendar | One task is stored three ways: mirror event (`syncFromTask`), synthetic event (`CalendarTaskExpander`), and the task row. De-duplication relies on a fragile key. |
| M | WidgetStateBuilder:63-73 | Exam and event tasks appear twice in the widget. |
| M | TaskRepository:81 | Completing a task does not update its mirror event. |
| M | RoutineRepository:59 | Deleting a routine leaves its schedule slots, still shown in the calendar. |
| M | CalendarRepository:176 | A rescheduled instance loses its link to the series. |
| M | PlanRepository:79 | Study blocks ignore existing blocks, so they can double-book. Blocks are absent from the Calendar screen. |
| M | CalendarEventEditSheet:96 | Always saves as `PERSONAL`. Events have no reminders. |
| M | Dashboard, ContextBuilder | Neither includes calendar events or classes. Two separate AI context builders overlap. |
| M | SyncRepository | Covers 10 of 29 entities. Calendar, slots, plans, habits, jobs, log and guide are local only. Pulled alarms and reminders are never scheduled. |
| M | BootReceiver | Does not re-arm `InsightDailyWorker` or `QuoteDailyWorker`. |

### C3. Plan versus code

| Sev | Claim | Reality |
|---|---|---|
| H | PLAN: calendar removed | Full Calendar, timetable import, Plan, Habits, Jobs, Life Log, Leave-by and Spend Guide exist and are not in PLAN or README. |
| H | Routines, Alarms, Notes "Done" | Unreachable from the UI. |
| H | Voice-only widget "Done" | Toggle inert, launcher orphaned. |
| M | Sync "Done" | 10 of 29 entities. |
| M | Vosk default, Whisper removed | Five engines present. |
| M | Dashboard "Done" | Stub only. Real plan is [dashboard-plan.md](dashboard-plan.md). |
| L | "Unit test stubs" | 13 real test files. |

### C4. Calendar merge: what changes

Goal: Tasks, Routines, Reminders, Classes and Alarms all become calendar events.

**Model.** One `CalendarEvent` with `kind` (EVENT, TASK, EXAM, CLASS, ROUTINE, ALARM), `isCompleted`, `links`, optional end or all-day, alarm fields (`toneUri`, `repeatDays`, `isEnabled`), and recurrence with exceptions. One reminders table, capped at 10 per event.

| Area | Change |
|---|---|
| Entities | Remove Task, Routine, Course. Merge task and slot reminders into one table. Fold schedule slots into recurrence. Write a real Room migration. |
| Repositories | Merge Task, Routine, Schedule, Course, Calendar and Alarm repositories into one. Delete `syncFromTask` and `CalendarTaskExpander`. |
| Workers | One event-reminder scheduler replaces the task and routine-slot ones. Alarm ringing stays, driven by the event. Leave-by drops the TASK reference. |
| Screens | Tasks and Alarms become filtered views of Calendar. Remove Routines and the slot editor. |
| Voice and AI | Task, Reminder, Alarm and Routine intents become one Event intent. Merge the two context builders. Add calendar events to Dashboard and widgets. |
| Backend | Add `calendar_events` and reminders tables, DTOs and sync. Move the 10-reminder trigger. |

Fixes already applied (not built or tested): see section E.

---

## E. Fixes applied

| Done | Change |
|---|---|
| Yes | `FocusForegroundService` now declares `specialUse` with its permission and returns `START_NOT_STICKY`. |
| Yes | `MainActivity` requests notification permission on Android 13+. |
| Yes | Database no longer wipes on any missed migration, only from version 1. |

---

## D. UI direction (from review)

1. Break equal boxes: one large hero tile, one wide tile, small paired tiles, a horizontal scroller. Fewer words per tile.
2. Voice is the centre. Add budget, calendar event, task, edit voice logs, and query. Voice log becomes an editable list.
3. Home and You reduce to fewer, larger blocks.
4. All new tasks, routines and reminders go straight to the calendar as events.

---

## F. Calendar merge result

Status: implemented and verified at the level described under "Verification". Nothing was run on a device or against a live Supabase project.

### What changed

| Area | Change |
|---|---|
| Model | One `CalendarEvent` with kinds `EVENT, TASK, EXAM, CLASS, ROUTINE, ALARM` (legacy `PERSONAL` is kept and treated as `EVENT`). New fields: `notes`, `links`, `allDay`, `hasDate`, `isCompleted`, `completedAt`, `isEnabled`, `alarmToneUri`, `alarmRepeatDays`, `reminders`. Recurrence is the existing `EventRecurrence` with exceptions. |
| Room | Version 7 with `exportSchema = true` (`app/schemas/.../7.json`, `room.schemaLocation` set in `build.gradle.kts`). `calendar_events` is rebuilt, `event_reminders` is new, both indexed. A real SQL migration 6 to 7 (`Migration6To7.kt`) copies tasks, task reminders, routines, schedule slots with exceptions and reminders, alarms and leave-by rules, then drops the old tables. Destructive fallback stays limited to version 1. |
| Removed | `TaskEntity`, `TaskReminderEntity`, `RoutineEntity`, `AlarmEntity`, `CourseEntity`, `ScheduleSlot*`, `RoutineSlotReminderEntity` and their DAOs and models. `TaskRepository`, `RoutineRepository`, `ScheduleRepository`, `CourseRepository`, `AlarmRepository`. `CalendarTaskExpander`, `CalendarEventExpander`, `syncFromTask`, `RoutinesScreen`, `RoutineTimetable`, `ScheduleSlotEditSheet`, `ScheduleContextBuilder`, `TaskReminderScheduler/Worker`, `RoutineSlotReminderScheduler/Worker`, the Routines tile and `LifeTab.Routines`. |
| Repository | `CalendarRepository` is the only store: observe by range, tasks, alarms; `upsert` with reminders in one transaction; `setCompleted`, `setEnabled`, recurring delete, reschedule one occurrence, `importClasses`, `rearm`, `rescheduleAllAlarms/Reminders`. The cap of 10 reminders is enforced there. |
| Reminders | One `event_reminders` table. Reminders are offset based (minutes before each occurrence, 0 = "At time") or a single absolute time. `EventReminderScheduler` and `EventReminderWorker` replace the two old pairs and re-arm repeating events themselves. Default reminders for new events are the existing 10 min, 1 hour and 3 hours, pre-selected in the sheet. |
| Alarms | `AlarmScheduler` (still `setAlarmClock`), `AlarmFireReceiver`, `AlarmRingingService`, `AlarmRingingActivity` and `AlarmTriggerCalc` keep their behaviour and now read ALARM events. The ring service ignores an id that is not an enabled ALARM event (stale intents from before the upgrade). `BootReceiver` re-arms alarms and reminders on boot, package replace, time change and time zone change. |
| Screens | Calendar create and edit use one sheet (kind, date, time, all day, repeat, reminders, leave-by, place, notes) with date and time pickers. Tasks and Alarms are filtered views over events. Calendar today cell contrast fixed, adjacent-month days now show their events, task delete asks for confirmation. Tasks, Notes and Alarms are reachable from the Life hub. Timetable import (CSV, paste, image) writes weekly CLASS events. |
| Voice and AI | `ParsedIntent.Task/Reminder/Alarm/Routine` collapsed into `ParsedIntent.Event(title, startAt, kind, repeat, reminders)`. `QuickParse` now reads the spoken date (today, tomorrow, next week, in N days, weekday names, "october 12", ISO) and clock time ("7:30 pm", "at 9", noon) and strips them from the title, so "remind me to call mom tomorrow at 9 am" saves "Call mom" for tomorrow 09:00. The cloud mapper uses `start_at`, falls back to the transcript, and never drops the date. The confirm-card edit sheet is the shared calendar sheet; Bill and Debt due dates use `DatePickChip`. Dead TASK and ALARM validator types removed. |
| Widgets and readers | `WidgetStateBuilder`, `WidgetTaskToggleCallback`, `WidgetVoiceCaptureActivity`, Dashboard "Soon", Search, `ContextBuilder` (now includes the former `ScheduleContextBuilder` 14 day slice) and `PlanRepository.collectBusyIntervals` read events only. Exams and events no longer appear twice in the widget. |
| Sync | `calendar_events` and `event_reminders` replace tasks, reminders, routines and alarms. Pulls are ordered (`updated_at, id`) and paged in a loop, for all synced tables. Pulled reminders respect the cap of 10. After a pull every touched event is re-armed (reminders and alarms) and deleted ones are cancelled. `supabase/migrations/004_calendar_events.sql` adds both tables, RLS, a server `updated_at` trigger with stale-write protection, a concurrency-safe max-10 trigger and an ownership check on the reminder update policy. |
| Tests | Updated for the removed classes. Added migration, recurrence with exceptions, reminder cap and timing, alarm mask, event sync mapper and voice date and label tests. |

### Deviations from the brief

| Brief | What was done and why |
|---|---|
| `supabase/migrations/003_calendar_events.sql` | Named `004_calendar_events.sql`. `003_quotes_seen.sql` already exists and Supabase keys migrations by their numeric prefix. |
| `endAt` and `allDay` optional | `endAt` is stored non-null; point items (tasks, alarms) use `endAt == startAt`. `allDay` is a flag. This keeps every query and the expander simple. |
| Undated tasks | Added `hasDate`. An undated task keeps `startAt` at its creation day for ordering and is excluded from calendar range queries. |
| "Move the 10-reminder trigger" | Added to `event_reminders`. The old `reminders` trigger and the legacy server tables are left untouched. |
| Slot exceptions to recurrence exceptions | Done as `excludedDates`. Slots get a past anchor date (four weeks before the current week) so earlier weeks still show. Moved class occurrences already stored as separate CLASS rows are kept as they are. |
| Routines | A routine that owns timetable slots is dropped and its slots become CLASS events. A routine without slots becomes a ROUTINE event at 08:00 with a daily, weekly or weekdays repeat (a blank or custom rule becomes daily). Soft-deleted rows are not migrated. |
| Completing a repeating task | Skips that one day (adds an exception) instead of completing the whole series. |
| Reminder rows have no foreign key | Soft delete is used everywhere; the repository and the migration keep them consistent. |
| Calendar event times on the server | `timestamp` columns holding wall-clock time, not instants. This fixes the old "local time stored as UTC" problem for events only. |
| Widget voice capture | A parsed Event is saved as that event. Speech that is not a calendar item (for example a spend) is still saved as a task titled with the text, as before. |
| Plan blocks | They were already part of busy intervals; they are still not drawn on the Calendar screen. |

### Not done

| Item | Note |
|---|---|
| Moved occurrence link | A rescheduled instance still becomes a standalone event with no link to its series. Reminders are copied to it. |
| Server data | Existing cloud tasks, reminders, routines and alarms are not copied into `calendar_events`. Each device pushes its own migrated rows. Old server tables are not dropped. |
| Edge function | `supabase/functions/ai-proxy` still lists `task` and `alarm` response types; the app no longer sends them. |
| Reminder delivery | Still WorkManager delays, so Doze can delay a reminder (audit B6). Only alarms are exact. |
| Pre-upgrade alarms | Alarms armed before the upgrade are cleared by the package replace and re-armed from the new table by `BootReceiver`. This was not tested on a device. |
| UI redesign | Not started, as instructed. The new sheets use the `ui-brief.md` components only. |
| Other findings | Items in sections A, B and D that are not about the merge are untouched. |

### Verification

| Check | Result |
|---|---|
| `.\gradlew.bat :app:compileDebugKotlin` | Passes. |
| `.\gradlew.bat :app:assembleDebug` | Passes, so the Hilt graph and the Room schema are valid. |
| `.\gradlew.bat :app:testDebugUnitTest` | 113 tests, 0 failures. One older test (`SyncTimeTest.isoToMillis_acceptsOffsetDateTime`) had a wrong expected constant and was corrected. |
| Migration 6 to 7 | `Migration6To7Test` builds a hand-written version 6 database (copied from the entities and migrations 2 to 6, not from a real device file) on SQLite through sqlite-jdbc, seeds tasks, reminders over the cap, routines, slots with exceptions, alarms, a task mirror event and leave-by rules, runs the exact SQL used by `Migration6To7`, checks the mapped rows, and compares columns and indices with the Room-exported `7.json`. It is not a Room `MigrationTestHelper` run and nothing was upgraded on a device. |
| `004_calendar_events.sql` | Executed on a throwaway local PostgreSQL 17 with a stubbed `auth` schema. Checked: the server stamps `updated_at`, stale upserts are ignored and fresh ones apply, the 11th active reminder is rejected on insert and on revive, another user cannot see events or attach or re-point reminders, check constraints. It was not applied to a Supabase project; the real `auth` schema and PostgREST were not involved. |

---

## G. UI redesign result

Build: `assembleDebug` passes. Unit tests: 128 run, 0 failed, 0 skipped. Not run on a device or emulator.

| Area | Done |
|---|---|
| Tabs | Today, Plan, Voice (centre), Money, You |
| Today | Now, Next, Safe today and Attention pair, Insight, Wrap-up, time-of-day order. Old Home removed |
| Plan | Calendar (agenda, month, kind chips), Tasks, Log, Jobs. One Add sheet for all kinds. Alarms folded in. Study confirm-before-save. Focus uses block length |
| Voice | Speak with editable history (Room v8 `voice_history`), multi-item confirm cards with pickers, Undo and Open, budget intent, permission and recorder errors, Notes and Ask as segments |
| Money | Overview, Spend, Plan, Owed. Safe-today now computed from transactions |
| You | Five rows, single engine picker, Google sign-in for local users, currency sheet |

Not done:
- Voice queries, and edit or delete by voice.
- `ai-proxy` still lacks bill, debt, goal and budget types (app-side override covers it).
- Speculations cannot be edited or deleted. Habit sessions cannot be un-completed. Study blocks cannot be moved.
- Dated tasks with no time are stored all-day and may show at midnight in Today and widgets.
- Bill payment is guarded, not a single DB transaction.
- Widgets not redesigned to [plan-widget.md](plan-widget.md) in this pass.
- Insights category rows do not open Spend. Engine chip is not tappable.

---

## H. Offline then sync (checklist)

Local Room is the copy the user edits with no network. When the device is online again, every row the user entered is pushed to Supabase. This list is a section of the audit, not a Today-screen change.

Status values: open, confirmed, already done, dropped.

| # | Status | Issue |
|---|---|---|
| H1 | done | Coming online enqueues `supabase_sync_now`. The 15-minute job remains. |
| H2 | done | Study plans and plan blocks push and pull when online. A block keeps its parent plan id. |
| H3 | done | Habits and habit logs push and pull when online. A log waits until its habit has a remote id. |
| H4 | done | `job_applications` push and pull when online. |
| H5 | done | Life log entries and check-in windows push and pull when online. Visit ids stay on the phone. |
| H6 | done | `voice_history` pushes when online. The local linked item id stays on the phone. |
| H7 | done | Spend speculations, daily guides, and leave rules push and pull when online. |
| H8 | done | Focus sessions and nudge proposals push and pull when online. Location points and visits stay on the phone. |
| H9 | done | A failed sign-out upload keeps Room and `pendingUploadUserId`. The same account uploads next time. A different account still wipes first. |
| H10 | already done | Quotes sync in `QuoteRepository`, not inside `syncAll`. Left there so two writers do not race. |
