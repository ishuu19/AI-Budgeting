# LedgerAI UI design audit

**Scope:** every Compose screen, sheet and shared component in `app/src/main/java/com/ledgerai/app/presentation/` (Home chat, Today, Plan, Money, Life, Profile, Search, Ask, Voice log, Login, the floating capture sheet), plus `theme/`.
**Date:** 2026-10-11. **Method:** code read of every file against the checklist below. Not checked on a running device, so visual claims are from the code, not screenshots.
**How to read:** each row says what the UI does **now** and what to **change it to**. Severity: **H** = visible inconsistency, a broken state, or a contrast bug. **M** = clear improvement. **L** = polish. `file:line` paths are relative to `presentation/`.

---

## 1. The target, in eight rules

Reference: v0 by Vercel and shadcn/ui. These are the rules every row below is measured against.

1. **One primary action per surface.** Everything else is ghost, icon or text weight.
2. **Headline → insight → action.** The top block answers "how am I doing", the next answers "why", and then comes what to do. Lists come last.
3. **Every number has context:** a delta vs last period, a progress bar, a sparkline, or a status colour. No bare numbers.
4. **Labels, not sentences.** Empty states are one line. No helper paragraphs, no engine names ("Rules", "AI", "manual"), no `(optional)`.
5. **One component per idea:** one row, one search box, one icon button, one sheet, one mic, one header.
6. **Tokens only:** colour, radius, spacing (4 · 8 · 12 · 16 · 24 · 32) and type come from `L` / `MaterialTheme`. No literals, no ad-hoc alpha, no `FontWeight` overrides.
7. **Flat and calm:** 1dp borders instead of shadows, neutral surfaces, accent colour only for the one thing that matters.
8. **AI-first:** voice and photo are the first way in, and manual forms sit behind them. Forms open short (photo or voice + name) and the rest hides under "More".

---

## 2. Fix the system first

Each of these changes one shared file and removes dozens of the per-screen findings in section 3. Do them before touching screens.

| # | Sev | Now | Change to |
|---|---|---|---|
| S1 | H | `L.Radius = 16.dp` and `L.RadiusSm = 16.dp` (components/Ledger.kt:66-67), so "small" means nothing. Literals 4, 8, 16 and 28dp are scattered across screens. | `Radius = 16`, `RadiusSm = 10`, `RadiusPill = 50%`. Ban `RoundedCornerShape(<number>)` in screens. |
| S2 | H | Spacing literals 1, 1.5, 2, 3, 5, 6, 10, 14, 18, 20 and 26dp in many files. | Add `L.Space` = 4 / 8 / 12 / 16 / 24 / 32 and use only those. |
| S3 | H | Two different "primary" fills: `L.Gold` (#2F5FE3) in `LButton`/`LFab`, `L.Primary` (#17306F) in chat `Pill`, user bubbles and `ProfileButton`. | One primary fill, `L.Accent` (rename `Gold`), with one `L.OnAccent`. |
| S4 | H | Text on accent uses `L.BoxDeep` in about 10 places (Ask bubbles, send, mic, KindPill, switch thumbs, calendar selected day, Today hero button) but `L.OnPrimary` in others. BoxDeep is light grey in light mode, so contrast is low. | Use `L.OnAccent` everywhere. Delete the BoxDeep-on-accent pattern. |
| S5 | H | Amber has no "on" colour. The hard-coded navy `Color(0xFF0B1B45)` (navigation/AppNavigation.kt:455, chat/ChatScreen.kt:448) and `L.Ink` on amber (capture/CaptureComposer.kt:167) fail contrast in dark mode. | Add `L.OnHighlight` (navy in both themes). |
| S6 | H | Errors use `MaterialTheme.colorScheme.error` (Household, Settings, Login) in some places and `L.Danger` in others. PlanAddSheet has a literal `ErrorInk = Color(0xFFB3261E)`. | `L.Danger` only. Ban `colorScheme.*` and `Color(0x…)` in screens. |
| S7 | H | Destructive actions look safe. `LConfirmDelete` "Delete" is `L.Primary` (LayoutKit.kt:285). RecurrenceDelete shows three filled accent buttons. Many deletes have no confirm (pantry item, shopping line, wardrobe remove, member remove, Leave, Focus "Abandon"). | Destructive text is `L.Danger`, and every delete goes through `LConfirm`. |
| S8 | H | Four or more icon-button styles: `LToolButton`, `LIconButton`, raw Material `IconButton` (Tasks, Calendar, Jobs, PlaceField, Inventory, back button), chat `HeaderIcon` and `SmallIcon`, capture `RoundAction`. | `LToolButton` only (48dp target, 24dp icon). Delete the rest. |
| S9 | H | Four row implementations for "item in a list": `AgendaRow`, `TaskRow`, `NoteRow`, `LGroupRow`, plus stacked bordered `LRow` cards and per-item `LCard`. Paddings differ (4/16, 20/14, 16/16, 12/16). | `LGroup` + `LGroupRow(leading, title, sub, trailing, end)` for every text list, and a photo grid for objects. Keep `LRow` only for a single standalone item. |
| S10 | H | Section headers come in five styles: `LSection`, raw `titleSmall` muted text, inline `titleSmall` Ink, private `SectionLabel`, `MutedLine`. | `LSection(title, sub?, action?)` for list headers and `LFieldLabel` for form groups. |
| S11 | H | `LButton` always fills the width, so `Pill`, `HeroButton`, `PhotoButton`, `LIconTextChip` and text links were written from scratch. | `LButton(fill = false)` plus one `LTextLink` (min 48dp). |
| S12 | H | `LChip` is `selectable(Role.Tab)` but is used as a button ("Ask AI", "Copy last month", "More details", starters, "Photo"/"CSV", "Move to Interview" with `selected = true`). Exclusive choices (Active/Done, period, direction) use chips instead of segments. | Split into `LFilterChip` (filters and multi-select) and `LActionChip` (Role.Button). Use `LSegments` for 2–4 exclusive options. |
| S13 | H | `LSheet` always requires a filled primary, so "Close" or "Cancel" becomes the main button (Suggestions, ManualAdd, RecurrenceDelete). | Make `primary` nullable. Swipe or scrim closes the sheet. |
| S14 | H | Mic/send buttons come in 48, 52 and 56dp, in Primary or Highlight. `LAiBar` exists but is never called, and LTabPage's `aiBar` slot is unused. On Home the tab-bar mic and the composer mic show together. | One `LMicButton` (52dp, Highlight, OnHighlight). Wire `LAiBar` into Plan, Money and Life, and hide the composer mic on Home. |
| S15 | H | `LHero` and `LSmallBlock` have only label/value/sub, and no money hero shows a comparison. | Add `delta: Delta?` (↑12% vs Sep, coloured) and `trend: List<Float>?` (sparkline) to `LHero` and `LSmallBlock`. |
| S16 | H | Charts are hand-made: one bar chart, one donut, and `LProgress`. The donut takes `L.Gold`/`L.OnBox` once at start-up, so it keeps the wrong colours after a theme switch (screens/analytics/AnalyticsScreen.kt:116). `CategoryColors` is unused and does not adapt to dark mode. | Shared `LBarChart`, `LDonut`, `LSparkline`, `LGauge` and `LProgress(marker)`. Use a token categorical palette with light and dark variants and one fixed colour per category. |
| S17 | H | Income and expense coding is undefined. `IncomeGreen`/`ExpenseRed` exist but are unused. Signs vary: `+/-` on Spend, none on bills, net in day headers. | One rule: income `+` and Income colour, expenses neutral with no sign, overdue/over the Danger amount only. |
| S18 | M | Status colours drift. Near-limit is amber in Budget but accent in SpendToday. Overdue colours the amount, pill, heading and the whole hero at once. | One scale: Accent = on track, Highlight = near or inferred, Danger = over or overdue (on the amount only). |
| S19 | M | Page headers differ: LScreen `headlineMedium`, LTabPage `titleLarge`, the Chat greeting `headlineMedium` with its own icons, Today with a logo and settings icon, plus custom headers in Receipt, Ask and Focus. | One `LHeader(title, back?, actions)` with ProfileButton, used by LScreen, LTabPage, Chat and Today. |
| S20 | M | Feedback has five renderings: inline muted text, inline Ink text, Danger at the top of the list, a custom `SavedBar`, and `Toast` (Notes, AlarmTone, QuickCapture). | Promote `SavedBar` to `LSnackbar(message, action)` and route every notice through it. |
| S21 | M | Searches differ. `LSearchBox` (16dp radius although documented as a pill) is used on the tabs. Notes, Settings currency and Search use `LField`. Ask uses `OutlinedTextField`. | `LSearchBox` (pill, 52dp) everywhere. The in-search mic gets a distinct icon (keyboard voice) so it isn't mistaken for "talk to AI". |
| S22 | M | Three switch styles (custom, default M3, default inside custom rows). | `LSwitch` and `LSwitchRow` (Accent track, OnAccent thumb). |
| S23 | M | Type scale: `labelMedium` equals `labelSmall`, `displayLarge` is unused, no tabular figures, two `FontWeight.SemiBold` overrides. | Five roles only (display/hero, title, body, label, numeric with `tnum`). Optional brand face such as Inter or Geist. |
| S24 | M | Three date formats ("MMM d, yyyy", "d MMM yyyy", "MMM d") and two time formats (HH:mm and h:mm a). | One `relativeDate()` ("Today", "Fri", "3d ago", "Oct 12") and a locale-aware time. |
| S25 | M | Thumbnails at 64, 72, 84 and 96dp. Remove badges at 24, 28 and 32dp. | Two thumbnail sizes (64 / 96) and one `LRemoveBadge` (24 visible / 48 target). |
| S26 | L | Shadows on the tab bar (4dp) and mic (6dp); nothing else in the app has one. | Remove them; the 1dp border is enough. |

---

## 3. Screen by screen

### 3.1 Home (chat) and capture

| Sev | Where | Now | Change to |
|---|---|---|---|
| H | chat/ChatScreen.kt:290-307 | Briefing is four prose lines ("Next: X at 14:00", "2 overdue tasks", "Safe to spend today: $40"). | Compact block: next item as time + title, "Safe to spend" value with progress, and count badges for tasks and bills. |
| M | chat/ChatScreen.kt:259-275 | Custom header with the greeting "Good morning" in headlineMedium plus copied icon buttons. | `LHeader` with the date as the title ("Sat 11 Oct"), Search and Profile. The briefing is the real headline. |
| M | ChatScreen.kt:287-309 vs today/TodayScreen.kt:122-134 | The same day summary is drawn twice with different words ("Nothing else planned today" / "Nothing else planned"). | One shared `DaySummary` component. |
| M | ChatScreen.kt:179 | "Needs your OK" as a raw `titleSmall` muted header. | `LSection("Needs you")`. |
| M | ChatScreen.kt:200 | Bubble "Here is what I understood. Check it and save." | Remove it; the confirm cards speak for themselves. |
| L | ChatScreen.kt:189,197,236 | "Looking at your photo…", "Reading that…", "Thinking…" as separate bubbles. | One animated typing-dots bubble. |
| M | ChatScreen.kt:349-362 | Custom `Pill` "Save all" / "Discard". | Inline `LButton` + `LGhostButton`. |
| M | ChatScreen.kt:221-233 | Saved notice: a hand-built row with "Open"/"Undo" text links under 48dp. | `LSnackbar` with an Undo action. |
| L | ChatScreen.kt:325,341 | Bubble max width 300dp (user) vs 320dp (bot). | One value (85% width). |
| L | ChatScreen.kt:85 vs capture/CaptureComposer.kt:63 | Two different starter-prompt lists. | One list with short labels ("Cook", "Outfit", "Log lunch", "Remind me"). |
| M | ChatScreen.kt:452-457 vs CaptureComposer.kt:178-183 | "Add manually" in two styles (labelMedium muted vs labelLarge Primary). | `LTextLink("Add manually")`. |
| H | CaptureComposer.kt:257 | "You need: …" in amber on a white card (about 2:1 contrast). | InkMuted text with an amber dot. |
| M | CaptureComposer.kt:240-261 | Meal idea as sentences "From your pantry: a, b, c" / "You need: x". | Ingredient chips (filled = have, outlined = missing) and a "5 of 6 ingredients" bar. |
| L | CaptureComposer.kt:240 | "You have everything". | A check icon next to the time. |
| M | CaptureComposer.kt:292-294 | The outfit "why" paragraph. | One line, or reveal on tap. |
| L | CaptureComposer.kt:300-309 | "Worth buying" plus "name · reason" lines and "Add all to shopping list". | `LGroupRow(title = name, sub = reason)` and "Add all". |
| M | CaptureComposer.kt:114-136 | `OutlinedTextField` composer, while Chat uses a pill `BasicTextField`. | One shared composer component. |
| M | capture/CaptureScreen.kt:106-113,134 | Failed photos show the error twice ("Error: …" plus description). | Once, in one muted line. |
| M | CaptureScreen.kt:150 | "Offline. I will continue when you are back online." | "Offline · queued". |
| M | CaptureScreen.kt:167-179 | Suggestions as sentences ("Log $X at shop as spending.", "Looks like a subscription: … Renews about …"). | Structured row: title merchant, trailing amount, sub "Monthly · renews 12 Nov". |
| M | CaptureScreen.kt:197-200 | Two full-width buttons per suggestion ("Add 3 to pantry" / "Not now"). | A compact inline action plus a dismiss ✕. |
| M | CaptureScreen.kt:186 | "Who is this? I'll file it under their name on the date it was taken." | Field label "Who is this?" only. |
| M | CaptureScreen.kt:133-137 | Failed card: description, 4 chips, field, "File it", "Try the AI again". | "Retry" tool button plus a "Name it" action that opens the picker. |
| L | CaptureScreen.kt:119,127 | Raw enum kind label ("Clothing") and an "Open" text inside a clickable card. | Mapped labels ("Clothes") and a trailing chevron. |
| H | CaptureScreen.kt:48-82 | `CaptureScreen` is not routed anywhere (only `InboxCard` is used). | Delete it, and move `InboxCard` to its own file. |
| M | capture/ManualAddSheet.kt:29-38 | 7 bordered `LRow` cards plus a filled "Close". | One `LGroup` of rows, with no primary button. |

### 3.2 Today (full-day view)

| Sev | Where | Now | Change to |
|---|---|---|---|
| H | today/TodayScreen.kt:139-174 | Custom header (logo, greeting, Search, Settings icons, no back) on a pushed screen. | `LHeader("Today", back)` with Profile. |
| H | TodayScreen.kt:362-380 | "Attention" shows a bare total "5" with no breakdown. | "2 tasks · 1 bill · 2h log" or a small stacked bar. |
| M | TodayScreen.kt:213-228 | "Now / Free", and the current block shown as "14:00–15:00 · 25m". | "Free until 15:00", or a progress bar of the block with "25 min left". |
| M | TodayScreen.kt:254-272 | Custom `HeroButton` (BoxDeep text, the "outline" variant has no border). | Inline `LButton` / `LGhostButton`. |
| M | TodayScreen.kt:289-323 | Hand-built rows with a "Tomorrow" tag on each. | `LGroup` with a "Tomorrow" sub-header, as a time-rail timeline. |
| M | TodayScreen.kt:404 | "Insight · Rules" / "Insight · <model>". | "Insight". |
| M | TodayScreen.kt:414,418 | "3 h open" / "Log clear" / "3/5 habits" as text. | "3h unlogged" / "All logged" / a progress bar "3 of 5 habits". |
| M | TodayScreen.kt:97 | `spacedBy(24.dp)` while every other list uses 16. | 16. |
| L | TodayScreen.kt:245 | "Start" focus shows on every event. | Only on Study/Habit blocks. |
| L | TodayScreen.kt:344 | "-" when there is no guide. | Hide the block, or show a "Set budget" action. |

### 3.3 Plan

**Calendar** (screens/calendar/CalendarScreen.kt)

| Sev | Where | Now | Change to |
|---|---|---|---|
| H | :107-111 | Opens straight on the date strip with no headline; `LHeroCard` is imported but unused. | Headline above the strip: "Next: Dentist in 40 min", or "3 things today, free after 4 PM". |
| H | :506 | Day cell `heightIn(min = 40.dp)`. | 48dp touch target. |
| H | :499 | Selected day number in `L.BoxDeep` on accent. | `L.OnAccent` (S4). |
| M | :385-390 | Kind dots use `GoldSoft` (invisible in dark) and `OnBox` vs `OnBoxMuted` for Events vs Classes. | Categorical palette, one colour per kind. |
| M | :530-535 | One dot whether a day has 1 item or 9. | Up to 3 kind dots, or a "busy" bar. |
| M | :276-283 | Day agenda is a flat list. | Vertical time axis: blocks sized by duration, a "now" line, free gaps labelled "2h free". |
| M | :265-271, :245-249 | Day header and "3 matches" in two raw text styles, next to `LSection` "Next 7 days". | `LSection` for all three. |
| M | :394-485 | Custom `AgendaRow` (padding 4/16, 80dp time column, trailing check). | `LGroupRow` with a leading time slot and a leading check (S9). |
| M | :473-480 | Custom Switch colours. | `LSwitch` (S22). |
| M | :125,160-161 | Month/agenda toggle is a toolbar icon that flips meaning. | A small `LSegments` "Week · Month" above the grid. |
| L | :441 | Subtitle "Event · Place". | Place only; the kind comes from the dot. |
| L | :450-455 | 16dp bell icon on every row with a reminder. | Remove it (reminders are the default). |
| L | :344 | "More (4)" row inside the card. | `LSection(label, action = "All 9")`. |
| L | :372 | "Today" `LChip` between the chevrons. | A "Today" text action at the right of the header. |
| L | :167 | `topPadding = 4.dp` while sibling segments use 12–16. | The default, so content doesn't jump when swiping. |
| L | :274 | Empty "Free day". | "Free day" plus an "Ask AI to plan it" action. |

**Tasks** (screens/tasks/TasksScreen.kt)

| Sev | Where | Now | Change to |
|---|---|---|---|
| M | :137 | Hero sub "Today 5:00 PM · 4 open, 1 late". | Progress bar "3 of 7 done today", with no open/late text. |
| M | :118-129 | Hero actions as chips "Done", "Tomorrow", "Pick day". | `LButton("Done")` plus a ghost "Tomorrow". |
| M | :180-227 | Custom `TaskRow` (padding 4, spacing 4) with raw IconButtons. | `LGroupRow` with a leading check and `LToolButton`. |
| M | :223 | Clock icon means "Move to today". | A "Today" text action. |
| L | :131 vs :57 | "Overdue" in the hero vs "Late" as the group name. | "Late" everywhere. |
| L | :126 | "Pick day" vs "Pick date" elsewhere. | "Pick date". |
| L | :152 | "Late · 3" count format. | The count as a trailing muted number in `LSection`. |
| L | :145-174 | The Done group listed in full. | One line, "12 done this week ▸", with a 7-day streak. |
| L | :109 | Its own "Add" FAB on a swipe page. | One tab-level add (AI bar). |

**Jobs** (screens/jobs/JobsScreen.kt)

| Sev | Where | Now | Change to |
|---|---|---|---|
| H | :391-405 | Stat blocks "Applied this week" / "Replied %". | Funnel Applied → Screening → Interview → Offer with counts and conversion %. |
| H | :417-419 | 7 status filter chips and a list grouped by stage. | Funnel segments act as the filter; remove the chips. |
| H | :165-264 | A second hand-built month calendar (`JobsMonth`) with different sizes. | Show follow-ups in the main calendar as a "Jobs" kind, or reuse one `LMonthGrid`. |
| H | :255 | Follow-up dot `L.Box` on `L.Page`, invisible in both themes. | `L.Accent`. |
| H | :490-494 | Helper "Easiest: say it, like "Applied to Google, follow up Friday". Or paste a posting or link here." | Remove it. Mic button plus a field hinted "Link or posting". |
| H | :541-611 | Job sheet: 6 stage chips, 5 follow-up chips, 11 fields. | Read-only summary (stage stepper, follow-up), with fields behind Edit. |
| M | :564-569 | Stage picker as 6 equal chips. | Horizontal stepper Applied ● Screening ● Interview ○ Offer. Rejected/Withdrawn as a separate action. |
| M | :359-363 | `LChip("Move to Interview", selected = true)` used as a CTA. | `LButton`. |
| M | :438-444 | A trailing chevron that means "advance stage". | A "→ Interview" text action. |
| M | :450-455 | Row sub "Role · Location · Source · applied Oct 3". | "Role · Location". |
| M | :347 | Empty "No jobs yet. Say: applied to Google, follow up Friday". | "No jobs yet" plus the mic. |
| M | :595-597 | "More details" disclosure built from a chip. | A "More" row with a chevron. |
| M | :186,196,330,438 | Raw IconButtons, one in a stray `LScreen` action row. | `LToolButton` in the Plan toolbar. |
| L | :366, :379, :383 | "Snooze 1 week", "Follow up now", lowercase "due Oct 3". | "+1 week", "Follow up", "Due Oct 3". |

**Notes** (screens/notes/NotesScreen.kt)

| Sev | Where | Now | Change to |
|---|---|---|---|
| H | :302-304 | Its own `LField` "Search notes" inside the list. | The Plan toolbar search (S21). |
| H | :464-511 | AI hidden in a ghost "Assist" button with a dropdown ("Summarize, Tag, Ask, Tasks, Nudge"). | Tool icons above the body: Summarize, Ask, To tasks. Hide unavailable ones. |
| M | :324-326 | "3 notes · newest first". | Remove it. Group by Today / This week / Earlier. |
| M | :121-182 | "Summary ready · Rules", "Suggested 2 tag(s) · AI", "Added 3 task(s) · Rules". | "Summary added", "2 tags added", "3 tasks added". |
| M | :269-273,349 | `Toast` for saves and AI messages. | `LSnackbar`. |
| M | :444 | Sheet title "New" / "Note". | "New note", or the note's title. |
| M | :454-461 | 6 fixed tag chips always shown. | Applied tags plus a "+ tag" chip. |
| M | :515-519 | A second filled "Ask" button in a sheet whose primary is Save. | A trailing send icon in the question field. |
| M | :320 | Empty "Say: note, idea for the project". | "No notes yet" plus the mic. |
| L | :107,142,202 | "Add note text before summarizing." | Disable the action instead. |
| L | :381 | Row padding 20/14. | 16/16 (`LGroupRow`). |
| L | :534 | "Rules" / "AI" source label on the summary. | An "inferred" icon on AI output only. |

**Life log, Focus, and the Plan sheets**

| Sev | Where | Now | Change to |
|---|---|---|---|
| H | lifelog/LifeLogScreen.kt:158-185 | Check-in windows as a list "9:00 AM – 11:00 AM". | A 24h day ribbon coloured answered / pending / gap, with a compact list below. |
| H | LifeLogScreen.kt:205-212 | Voice as a secondary ghost "Voice" button. | Mic as the field's leading action, auto-started. |
| M | LifeLogScreen.kt:152-156 | Hero "3 of 8" / "2 gaps". | Segmented progress plus a 7-day streak. |
| M | LifeLogScreen.kt:168-174 | "Gap" as both subtitle and trailing text. | Status dot only. |
| M | LifeLogScreen.kt:107-141 | Its own voice broadcast plumbing. | `rememberVoiceInput`. |
| H | focus/FocusScreen.kt:144 | Timer "25:00" inside an `LHero` card. | A large progress ring with the time inside, plus "25 of 50 min". |
| H | FocusScreen.kt:148-162 | Two filled buttons, "Pause" and "Finish". | Pause primary, Finish ghost. |
| M | FocusScreen.kt:134-139 | "Focus" title plus "Focus" topic. | The topic as the title. |
| M | FocusScreen.kt:164-167 | "Abandon" with no confirm. | "End without saving" behind a confirm. |
| H | components/PlanAddSheet.kt:397-546 | Event form shows ~15 controls at once (title, date, all day, from, to, place, links, notes, leave by, 6 repeat chips, every, weekdays, until, 6+ reminder chips). | Title + when visible. Place, Notes, Repeat and Reminders as summary rows ("Repeat · Once ›"). |
| H | PlanAddSheet.kt:331-343 | Opens on 8 kind chips and a "Title" field. | One natural-language field with mic and camera ("Dentist Fri 3pm, remind me 1h before") that pre-fills the form. |
| M | PlanAddSheet.kt:52-54 | 8 kinds (Event, Task, Exam, Class, Alarm, Routine, Study, Habit). | 4 (Event, Task, Alarm, Habit). Exam, Class and Routine become event types. |
| L | PlanAddSheet.kt:425,431,536 | "From 9:00 AM" / "To 10:00 AM" chips and the counter "2/5". | One range chip "9:00–10:00 AM"; drop the counter. |
| L | PlanAddSheet.kt:464 | "Leave by". | "Leave-by alert". |
| H | components/CalendarEventDetailSheet.kt:143-153 | Up to 7 stacked full-width ghost buttons (Open map, 3× Open link, Change date, Edit, Delete). | One row of tool icons (Map, Link, Reschedule, Edit), with Delete as Danger text. |
| M | CalendarEventDetailSheet.kt:252-274 | Habit sheet: Start, Complete, Edit, Delete, Done. | Start primary, Complete as a header check, Edit/Delete as icons. |
| M | CalendarEventDetailSheet.kt:266-270 | Habit shows "Every day · Nudges". | Streak plus a 14-day dot heatmap. |
| L | CalendarEventDetailSheet.kt:88-98 | Plain date line. | Relative time: "In 2 days · 3–4 PM". |
| M | plan/StudyTimesSheet.kt:59-79 | Proposed sessions as a flat list with an AI reason per row. | A mini week strip with blocks next to existing events, no reasons. "Add 3 sessions" as the button. |
| M | plan/AgendaItem.kt:13-15 | 7 filter chips, and "Tasks" duplicates the Tasks segment. | All, Events, Classes, Habits, or none. |

### 3.4 Money

**Overview** (screens/money/MoneyOverviewScreen.kt)

| Sev | Where | Now | Change to |
|---|---|---|---|
| H | :198-206 | Hero "Safe to spend today" with sub "Spent $X this month", and no pace. | Add the delta vs the same day last month and the month-progress bar (`monthProgress` is computed at :153 but never shown). |
| H | :152 | "Next payment" shows the largest payment, not the next one. | Sort by due date, or label it "Largest upcoming". |
| H | :168,227-233 | "Saved" is all-time income minus spending, shown next to monthly figures. | This month's saved amount plus the savings rate vs last month. |
| H | :86,154 | `healthScore` is computed (an AI call) and never shown. | Show it as a small ring, or remove the call. |
| M | whole screen | No "where the money went" view. | A top-3 categories stacked bar that links to Insights. |
| M | :244-245 | Order: hero → link rows → chart → insight. | Hero → week chart → insight with action → link rows. |
| M | :274-281 | The insight card shows only the headline; `action` is hidden. | Headline plus a one-tap action chip with a sparkle icon. |
| M | :298 | "Last 7 days" chart with no total. | The 7-day total with "vs prior 7 days" in the label. |
| M | :229, :236-240 | "Income minus spending" sub, and a "Subscriptions / Tracked from receipts" row with no value. | Remove the sub; show the monthly subscription total, or move the row to Owed only (it's also on Owed). |
| L | :316,332 | Ad-hoc alphas 0.25 / 0.8 on chart parts, and an unlabelled guide line. | Tokens, and a dashed guide line labelled "guide". |
| L | :339 | Today's day letter replaced by "•". | Keep the letter, bold or accent. |

**Spend and its add form** (screens/transactions/)

| Sev | Where | Now | Change to |
|---|---|---|---|
| H | TransactionsScreen.kt:156-169 | One scrolling chip row holds From, To, All/In/Out and every category. | `LSegments` "All · Income · Spending", one date-range chip, and categories in a filter sheet. |
| H | TransactionsScreen.kt:148-152 | Hero "Spent" with sub "N entries · Oct 1 to Oct 11". | The delta vs the previous equal range plus a mini daily bar; drop the count. |
| M | TransactionsScreen.kt:62 | "In" / "Out" here, "Income" / "Expense" elsewhere. | "Income" / "Spending" everywhere. |
| M | TransactionsScreen.kt:211-212 | Income and expense amounts in the same colour. | Rule S17. |
| M | TransactionsScreen.kt:220-224 | A copy icon on every row, plus "Duplicate" in the sheet. | Keep it only in the sheet. |
| M | TransactionsScreen.kt:195-199 | Day header shows net "+$12 / −$40". | The day's spend only. |
| L | TransactionsScreen.kt:67 | `onBack` defaults to `{}`, which draws a dead back arrow. | Default `null`. |
| H | AddTransactionSheet.kt:102-163 | Amount, 3 chip rows, a suggestion line, Name, "Add place or note", Duplicate, Delete. | Amount, Name, suggested category, and one date/type row; the rest under "More". |
| M | AddTransactionSheet.kt:110 | "Repeats" toggle chip inside the Expense/Income choice. | A switch row under "More". |
| M | AddTransactionSheet.kt:125,135 | "Suggested · {reason}". | Just the suggested chip with a sparkle. |
| M | AddTransactionSheet.kt:168,178 | Titles "Edit" / "New". | "New transaction" / "Edit transaction" (glossary §4). |
| L | AddTransactionSheet.kt:104 | "Enter an amount above 0" in muted text, while Save is disabled. | Remove it. |

**Budget, goals and safe to spend**

| Sev | Where | Now | Change to |
|---|---|---|---|
| M | money/MoneyPlanScreen.kt:89-95 | "Budget left" hero with no pace. | Progress spent / total with a marker at today's % and "on pace" or "$X ahead". |
| M | MoneyPlanScreen.kt:96-117 | Budgets, Goals and Forecast all stacked in full. | Top 3 each with "See all". |
| H | budget/BudgetScreen.kt:108-111 | Bars without an "expected by today" marker. | A tick at the day-of-month %. |
| M | BudgetScreen.kt:103,112-116 | "$X left", the bar, then "$S of $L". | "$X left" plus the bar. |
| M | BudgetScreen.kt:59 | Muted sentence "Daily $X · Unbudgeted $Y". | `LSmallPair`. |
| M | BudgetScreen.kt:61-66,163 | "Ask AI" / "Copy last month" chips and an "Advice" button. | AI bar or sparkle row; "Copy last month" only in an empty or new-month state. |
| M | goals/GoalsScreen.kt:207-221 | % in the title, the bar, then "$X of $Y · $Z/mo". | Name, "$X of $Y" trailing, the bar, and "$Z/mo to hit Jun 1" only when there is a date. |
| M | GoalsScreen.kt:137 | Required pace only. | An "On track" / "Behind" tag (last 30 days vs required). |
| M | GoalsScreen.kt:161,164 | "Saved $X of $Y" muted text; Active/Done as chips. | One aggregate bar; `LSegments`. |
| L | GoalsScreen.kt:196,303 | The same flag icon on every row; "Finish it". | No icon; "Complete ($X)". |
| H | money/SpendTodayScreen.kt:127 | Near-limit in accent (amber elsewhere). | `L.Highlight` (S18). |
| M | SpendTodayScreen.kt:138-155 | Safe, buffer and hard limit as three numbers. | One horizontal gauge: spent today, a safe line and a limit line. |
| M | SpendTodayScreen.kt:150,157 | "Do not go past this today" and "Why this number". | Remove the helper; "Why". |
| M | SpendTodayScreen.kt:132 vs Overview :199 vs nav :104 | "Safe today" / "Safe to spend today". | "Safe to spend". |
| M | SpendTodayScreen.kt:134 | Raw `TextButton("Plan spend")` in the header. | A tool button. |

**Owed, bills, debts and subscriptions**

| Sev | Where | Now | Change to |
|---|---|---|---|
| H | money/MoneyOwedScreen.kt:88-95 | A bordered "Subscriptions" row above the hero. | Below the hero, as a group row with the monthly total. |
| H | MoneyOwedScreen.kt:100 | "0 overdue · $X per month" when nothing is overdue. | Show "2 overdue · $340" only when the count is above 0. |
| M | MoneyOwedScreen.kt:101 | The whole hero turns red when anything is overdue. | A red badge only. |
| M | MoneyOwedScreen.kt:96-103 | No paid-vs-remaining view. | Progress paid / due this month; fold "net owed" into the sub. |
| M | bills/BillsScreen.kt:199-218 | A bill list. | A 30-day timeline strip with dots on due dates. |
| M | BillsScreen.kt:325-335 | Edit sheet with Paid, Skip, Pause/Resume, Delete and Save. | Save and Delete; the rest in an overflow. |
| M | BillsScreen.kt:259-279 / debts/DebtsScreen.kt:137-154 | The "Paid" / "Pay" pill hand-built twice (14dp padding, 50% alpha border). | One `LPillAction`. |
| M | Bills:282-286 / Debts:163-165 / Overview:221 | "Overdue 3d", "Overdue", "Today", "Due today", "Oct 12", "Due Oct 12". | One `dueLabel()`: "3 days overdue", "Due today", "Due Oct 12". |
| L | BillsScreen.kt:233,247,261,303 | Red heading, red amount and red pill; "Monthly" on every row; SUBSCRIPTIONS as the default category. | Red on the amount only; hide the default frequency; infer the category. |
| H | DebtsScreen.kt:78,126,245 / DebtsViewModel.kt:17 | "Owed to you", "Owes you", "They owe me", "I owe", "They owe". | "Owes you" / "You owe". |
| M | DebtsScreen.kt:77-80 | Two totals with no net. | "Net +$40" plus a two-segment bar. |
| M | DebtsScreen.kt:227-264 | The edit sheet has its own Pay/Settle flow next to `RepaySheet`. | Open `RepaySheet`. Verbs "Paid back" and "Settle". |
| L | DebtsScreen.kt:91,103,244 | "Nothing here"; Settled with a Show/Hide toggle; direction as chips. | "No debts match"; one archive pattern; `LSegments`. |
| H | subscriptions/SubscriptionsScreen.kt:49-50,148-150 | Permanent paragraph "Cancelling here only changes this list. LedgerAI does not contact the merchant." | Show it once, in the cancel confirm. |
| H | SubscriptionsScreen.kt:168-184 | Each subscription is a heavy card (headlineMedium SemiBold amount plus a full-width button). | `LGroupRow` (name, next charge, amount); cancel in the item sheet. |
| H | SubscriptionsScreen.kt:134-140 | Hero "Next charge" (one item). | Monthly total with delta; the next charge as the sub. |
| M | SubscriptionsScreen.kt:141-147,216 | "N due in the next 30 days" line; "Leave amount blank if you don't know it. Blank is not zero." | Fold into the hero sub; placeholder "Unknown". |
| M | SubscriptionsScreen.kt:92-94,129-130 | `TextButton("Add")` in the header; empty state plus a full button. | One add pattern; a one-line empty state. |

**Insights, forecast and receipt**

| Sev | Where | Now | Change to |
|---|---|---|---|
| H | analytics/AnalyticsScreen.kt:116-123 | The donut palette is set once at start-up (wrong after a theme switch) from 2 hues plus alphas. | Categorical tokens (S16). |
| M | AnalyticsScreen.kt:157-173 | Flat list: Income, Spent, Savings rate, Daily avg, Top merchant. | Hero Spent with delta, an `LSmallPair` (savings rate, daily avg), then the donut. |
| M | AnalyticsScreen.kt:161,205,221 | "Spent" shown twice; % shown in the legend and the rows. | Merge the legend and the category list into one list. |
| M | AnalyticsScreen.kt:40-44,149 | 3M and Year have no trend chart; period as chips "Month / 3M / Year". | Monthly bars; `LSegments` "1M · 3M · 1Y". |
| L | AnalyticsScreen.kt:147 | Title "Insights" collides with the Overview "Insight" card. | "Trends". |
| H | forecast/ForecastScreen.kt:178-194 | Month-end balance as a bare number. | A line to date with a dashed projection to month end. |
| M | ForecastScreen.kt:176-178,199-213 | Double header; re-lists bills and overdue items shown on Owed. | One header; link to Owed. |
| M | ForecastScreen.kt:220-231 | AI paragraph per month, a stray "Rules" label, and a full-width primary "Predict months". | One-line insight; remove "Rules"; sparkle action. |
| H | receipts/ReceiptReviewScreen.kt:97-138 | Custom Scaffold and header, different from every other screen. | `LScreen` with a bottom action. |
| H | ReceiptReviewScreen.kt:133 | "Saves the receipt on this phone. Spending and pantry stay drafts." | Remove it. |
| M | ReceiptReviewScreen.kt:333-337 | "12.50 USD". | `money()` → "$12.50". |
| M | ReceiptReviewScreen.kt:215-287 | Hand-made TotalCard, big Merchant/Date fields, loose line items with an "Edit" button each. | `LHero` Total; one `LGroup` (Merchant, Date, Paid by); line rows that open edit on tap. |
| M | ReceiptReviewScreen.kt:281,404 | "Qty 2 · Price 3.5 · Line total 7 · Inferred"; "Pantry draft · Milk · quantity 2 · receipt 123 line 456". | "2 × $3.50" with the total trailing; "Milk +2 to pantry". |
| M | ReceiptReviewScreen.kt:229,244 | Confidence marked in three different colours. | One "Inferred" chip (amber). |
| L | ReceiptReviewScreen.kt:102,322 | Disabled primary "Confirmed on this phone"; "Not recorded as spent." | A "Saved" status chip; remove the line. |

### 3.5 Life

**Pantry** (screens/inventory/InventoryScreen.kt)

| Sev | Where | Now | Change to |
|---|---|---|---|
| H | :201,262-281 | A private "Stock / To buy" switch under the Life segments (two stacked switchers). | `LSegments`, or make Shopping its own Life segment. |
| H | :298-303 | "$low running low" as a bare amber line. | `LSmallPair` "Low" and "Expiring this week" that filter the list. |
| H | :304-327,459 | Unsorted list; expiry hidden in the sub as "Expires Oct 3, 2026". | Sort by expiry, an "Expiring soon" section, and a relative badge "3d". |
| H | :187-198,213 | Five ways in (search, mic, camera, gallery, FAB). | Camera and mic first, with manual add inside them. |
| M | :208,267,377 | "Pantry/Shopping", "Stock/To buy", "Add item/Add line". | "Pantry" / "To buy" / "Add to list". |
| M | :203-205 | "Do I have…" answer as plain text. | A result card: item, quantity chip, "Add to list". |
| M | :317-319,351-357,411-418 | "Add to list" TextButton on each row; delete icons with no confirm. | Tool icon or swipe; deletes with a confirm. |
| M | :348 | Bought lines stay in place with trailing "Bought". | Strike-through, collapsed into a "Bought" group. |
| M | :179,202,291 | Two notice styles that never dismiss; "Got it. The AI is adding what it sees." | `LSnackbar("Adding from photo…")`. |
| L | :448, :197 | "Unknown" trailing on every row; `Photo` icon (Wardrobe uses `PhotoLibrary`). | "—"; `PhotoLibrary`. |

**Wardrobe** (screens/wardrobe/WardrobeScreen.kt)

| Sev | Where | Now | Change to |
|---|---|---|---|
| H | :215,233-250 | The garment sheet has no photo, only text. | A large photo, plus "Worn 4 times · last 3d ago". |
| H | :202-207 | "Worn" as rows of comma-joined names. | A strip of outfit thumbnails by date, plus a wear-count badge on each card. |
| H | :279-287 | Add form: photo, name, 7 type, 5 season and 3 laundry chips, colour. | Photo + Name; the rest under "More" (the AI fills it). |
| M | :190-201 | No type filter. | Type chips (All, Top, Bottom, Shoes…). |
| M | :228-229 | Card shows name plus "Top · blue · Clean". | Photo + name; a laundry corner badge only when not clean. |
| M | :241-247 | "Remove from wardrobe" with no confirm; picking a date logs immediately. | A confirm; the date chip only sets the date, and "Log wear" commits. |
| M | :156 | "Got $n photos. The AI is naming and adding them." | `LSnackbar("Adding $n…")`. |
| L | :221,246,283 | Card padding 10dp, a 14dp alignment hack, "Color (optional)". | 12dp, centre alignment, "Color". |

**People and person** (screens/people/, screens/memory/)

| Sev | Where | Now | Change to |
|---|---|---|---|
| H | people/PeopleScreen.kt:178-187 | Bordered rows with a generic person icon. | Photo avatar (or initials) plus "Last seen 3w ago", in one `LGroup` or an avatar grid. |
| M | PeopleScreen.kt:220-224 | 5 fields with "(optional)" labels. | Photo, Name and one "Note" (dictatable); the AI extracts org and role. |
| M | PeopleScreen.kt:158-164 | No camera tool (Pantry and Wardrobe have one). | Add the camera tool. |
| M | PeopleScreen.kt:173 | Error shown at the top of the list, away from the form. | Inside the sheet. |
| H | memory/PersonMemoryScreen.kt:318-319 | Title = name, then `LHero` "Person" / name again in a number style. | One header: large round photo, name, org · role. |
| H | PersonMemoryScreen.kt:333-369 | Three silos (Moments, Memories, Promises) with three row styles. | One reverse-chronological timeline with type icons, open promises pinned at the top. |
| H | PersonMemoryScreen.kt:325-329 | Three buttons: "Add photo or moment", "Memory", "Promise". | One AI bar ("Tell me about Sam…") that picks the type; manual types in a menu. |
| H | PersonMemoryScreen.kt:486-491 | "fact · confirmed · manual". | Hide everything except an "inferred" badge. |
| M | PersonMemoryScreen.kt:335,364 | Two-sentence empty states; a map-pin icon for Promises. | "No moments yet" / "No promises"; a handshake icon. |
| M | PersonMemoryScreen.kt:269,449,469 | Blank screen while loading; enum chip labels; Open/Done chips on a new promise. | `LLoading`; mapped labels; no status on new promises. |
| L | PersonMemoryScreen.kt:416-441 | "Add photo or moment", "Add a memory", "Add a promise". | "New moment", "New memory", "New promise". |

**Household** (screens/household/HouseholdScreen.kt)

| Sev | Where | Now | Change to |
|---|---|---|---|
| H | :271-286,405 | "Copy my invite" / "Paste an invite" put a raw "Name::uuid" string on the clipboard. | One "Invite" button that opens the share sheet with a link or QR code. |
| H | :198-206 | `TextButton("Add")` in the header (FAB elsewhere). | The same add pattern as the other Life pages. |
| M | :196 vs Segments.kt:7 | Segment "Home", title "Households"; the chat tab is also "Home". | "Household" everywhere. |
| M | :208-214,230 | "Homes you belong to"; "Selected" / "Tap to see members". | Remove the first; a check plus "3 members". |
| M | :238 | With one household, the list and section repeat its name. | Show members directly. |
| M | :289-303 | Inline paste field and button in the list. | A sheet. |
| M | :309,390 | Leave and remove member (✕ icon) with no confirm. | A confirm; a "Remove" label. |
| M | :383-401 | Member rows with no avatars, and "You" twice. | Initials avatars; an "Owner" chip. |

### 3.6 Profile, search, ask, voice log, login, quick capture

| Sev | Where | Now | Change to |
|---|---|---|---|
| M | settings/SettingsScreen.kt:184-236 | One 6-row group with no headers. | Account / Preferences / About groups with `LSection`. |
| M | SettingsScreen.kt:238-244 | Life log (Info icon, same as About) and Insights (currency icon, same as Money rules) mixed into settings. | Move them to their tabs, or a "Shortcuts" section with distinct icons. |
| H | SettingsScreen.kt:346-410 | `VoiceSheet` ignores selection state; `EngineRow` is dead; "On-phone parser" row is clickable with no action. | Show a check on the selected engine and remove the dead rows. |
| H | SettingsScreen.kt:636-653 | A long privacy-policy text wall. | One "Privacy policy" row that opens a page. |
| M | SettingsScreen.kt:428-445,552-599 | Mixed commit models: some controls are instant, others need "Save". | Everything instant, with "Done". |
| M | SettingsScreen.kt:365,356 | "The phone's built-in listener. No extra voice model."; "Ask AI only when the rules are unsure". | "Built in"; "When rules are unsure". |
| L | SettingsScreen.kt:184,316,502 | Title "Profile" while the first row also opens "Profile"; "Google sign-in is not set up"; a double space in "$code  $symbol". | "Settings"; hide the row; the symbol trailing. |
| H | search/SearchScreen.kt:151 | `LField` "Search" with no mic. | `LSearchBox` with mic, auto-focused. |
| M | SearchScreen.kt:149-162,56-65 | Blank page on an empty query; pantry, wardrobe and people not searched. | Recent searches and kinds; include the Life items. |
| M | SearchScreen.kt:112-130,159 | Singular headers ("Spend", "Note"); money in the sub-line. | Plural headers; amount trailing with a kind icon. |
| H | ai/AiAssistantScreen.kt:318-348 | `OutlinedTextField` "Ask" with no mic. | The shared composer (pill, mic, send). |
| M | AiAssistantScreen.kt:195-212,275,345 | Hand-made header; BoxDeep on accent. | `LHeader`; OnAccent. |
| L | AiAssistantScreen.kt:216,321 | "Ask" as the header, the empty state and the placeholder. | Three suggestion chips; placeholder "Ask or say…". |
| H | voice/VoiceRecorderScreen.kt:249-260 | Every confirm card has a filled "Save", plus "Save all" and "Discard all". | Per-card tonal Save and one sticky primary "Save all". |
| M | VoiceRecorderScreen.kt:179,195-200,313 | Engine-name pill in the header; language chips above the mic; "Speak" under the mic. | Remove the pill; chips below the mic; no idle text. |
| M | VoiceRecorderScreen.kt:264-274 | A third composer ("Type" field plus a ghost "Send"). | The shared composer. |
| M | voice/VoiceCards.kt:226 | Confirm card border in `L.Primary` (every other card uses `L.Line`). | `L.Line`. |
| H | VoiceCards.kt:559-604 | Event draft sheet with 5–7 unlabelled chip rows. | Labelled rows; Notes and Reminders under "More". |
| M | VoiceCards.kt:471-757 | Every draft sheet titled "Edit". | "Edit expense", "Edit event", … |
| M | VoiceCards.kt:486,652,763,238 | Every category dumped as chips; a parse-source pill ("rules"/"cloud") on each card. | One category chip that opens a picker; hide the source. |
| H | voice/VoiceHistoryUi.kt:115-128 | Five stacked buttons (Save, Open, Voice, Redo, Delete). | Save, with the rest in an overflow. "Re-record" / "Read again". |
| M | VoiceHistoryUi.kt:60,108,149 | Gold `KindPill` on every row; title is just the time; "Words". | Muted pill; "Capture · 3:42 PM"; "Transcript". |
| H | auth/AuthViewModel.kt:74,81 | "Supabase is not configured. Set SUPABASE_URL…" shown to users. | Log it; show "Sign-in unavailable". |
| M | auth/LoginScreen.kt:65,90 | "Money. Simply."; "Sign-in needs Google and Supabase." | A Lifeline tagline ("Your life, remembered."); "Sign-in unavailable" plus "Continue on this device". |
| M | service/QuickCaptureActivity.kt:91-127 | Custom sheet (28dp radius, 20dp padding); a close icon under 48dp; title "Add or ask". | `LSheet` look; `LToolButton` close; the same title as Home. |
| M | QuickCaptureActivity.kt:74-78 | Toast "Saved. I'll sort it out in the background." | "Sent — sorting it now" (don't claim it's done). |
| M | screens/home/HomeScreen.kt:63-71 | Unreachable screen with "Household sharing isn't connected yet." placeholders. | Delete the route and the screen. |

### 3.7 Shell and components

| Sev | Where | Now | Change to |
|---|---|---|---|
| H | navigation/AppNavigation.kt:95 + Segments.kt:7 | "Home" is both the chat tab and the household segment. | "Household" for the segment. |
| M | Segments.kt:8 | Money segment "Plan" collides with the Plan tab. | "Budget". |
| M | AppNavigation.kt:95,105,97-98 | The sparkle icon for both Home and Ask; the mic icon for both the Speak button and Voice log. | Home icon for Home; a waveform or history icon for Voice log. |
| M | AppNavigation.kt:427-459 | Tab-bar mic and composer mic both on Home. | One mic on Home (S14). |
| M | AppNavigation.kt:435,454 | Mic announced twice (semantics and Icon). | Icon `contentDescription = null`. |
| H | components/LayoutKit.kt:252 | Unselected `LSegments` text in the same colour as selected. | `L.InkMuted` for unselected. |
| M | LayoutKit.kt:78 vs Ledger.kt:169 | Two hero sizes (headlineMedium vs displaySmall). | One display style for heroes. |
| M | LayoutKit.kt:155 | Divider `OnBox.copy(alpha = 0.12f)`. | `L.Line`. |
| M | LayoutKit.kt:337 | LError: a bare 32dp Primary icon (LEmpty uses a 64dp circle). | Match LEmpty. |
| H | components/EventDatePickerDialog.kt:39-40 | Selected day `L.Box` (white) on `L.Page`, near-invisible in light mode. | Accent with OnAccent. |
| M | components/PickChips.kt:56-59 | Time-picker selector white on a light dial. | Accent with OnAccent. |
| M | components/ToolKit.kt:212-228 | 48dp filled navy ProfileButton, the strongest object in every header. | 36dp avatar on `L.BoxDeep` with Ink text. |
| M | ToolKit.kt:183-185 | Raw `TextButton` "Check" in the search toolbar. | Check tool icon, or an inline `LButton`. |
| H | components/RecurrenceDeleteDialog.kt:22-32 | Three filled accent buttons (two destructive) plus a filled "Cancel". | Danger text rows "This one / This and later / All" and a ghost Cancel. |
| M | components/SuggestionsSheet.kt:20-37 | Title "Suggest"; "Close" primary; raw empty text; `LRow` cards with "Add" text; raw ISO timestamp as the sub. | "Suggestions"; no primary; `LEmpty`; group rows with an add icon; "Fri 15:00". |
| M | components/ScheduleImportSheet.kt:64-75 | "Photo" / "CSV" as chips; "Add/Replace" chip whose label flips; errors in Primary. | Tool icons; a "Replace existing" switch row; Danger for errors. |
| M | components/PlaceField.kt:91-148 | Raw IconButton; a 12dp-radius surface with an alpha border; a small "Open saved Maps link" text. | `LToolButton`; `LGroup` look; a "Maps" icon chip. |
| M | components/AlarmTone.kt:131-152 | A third chip border style. | One outlined chip spec. |
| M | components/LeaveByToggle.kt:20 | Never called. | Delete it, or use it as an `LSwitchRow`. |
| M | theme/Theme.kt:16-62 | Both schemes repeat the hex values; `onTertiary` is unset (white on amber). | Build the schemes from the Color.kt vals; set `onTertiary`. |
| M | theme/Color.kt:59,62-73 | `IncomeGreen` and `CategoryColors` are single-theme. | Light/dark pairs via `Palette.pick`. |

---

## 4. Copy glossary (one word per concept)

| Concept | Use | Stop using |
|---|---|---|
| Money you can spend today | **Safe to spend** | Safe today, Safe to spend today |
| Money in / out | **Income / Spending** | In / Out, Income / Expense |
| Debt direction | **Owes you / You owe** | Owed to you, They owe me, I owe, They owe |
| Paying a debt | **Paid back / Settle** | Pay, Record, Received |
| Late items | **Late**, "3 days overdue" | Overdue (in labels), Overdue 3d |
| Due date | **Due today**, **Due Oct 12** | Today, Oct 12, due Oct 3 |
| Creating | **New X** (New note, New bill) | Add X, New, Add |
| Editing | **Edit X** | Edit |
| Shopping list | **To buy**, **Add to list** | Shopping, Stock, Add line |
| Shared home | **Household** | Home, Households |
| Money budgets segment | **Budget** | Plan |
| AI-made values | **Inferred** (small badge) | Rules, AI, cloud, manual, confirmed |
| Empty states | One short line ("No notes yet") plus the mic | Say: …, Try: …, two sentences |
| Optional fields | Unlabelled | (optional) |
| Photo actions | **Camera / Gallery** | Take photo, Choose, Snap clothes, Scan food or receipt, Upload photo |

---

## 5. Suggested order of work

1. **Tokens and theme** (S1–S6, S18, S23–S25): radius, spacing, `Accent`/`OnAccent`/`OnHighlight`, Danger, categorical palette, type roles, date formats. Most contrast bugs disappear here.
2. **Core components** (S7–S15, S19–S22): `LGroupRow` with slots, `LToolButton` only, `LButton(fill)`, `LTextLink`, filter vs action chips, nullable sheet primary, `LMicButton`, `LHeader`, `LSnackbar`, `LSearchBox`, `LSwitch`, `LConfirm`. Then delete the hand-made copies.
3. **Charts** (S15–S17): `delta` and `trend` on heroes, then `LBarChart`, `LDonut`, `LSparkline`, `LGauge`, `LProgress(marker)`.
4. **Screens, highest-traffic first:** Home → Money Overview and Spend → Calendar and Tasks → Pantry and Wardrobe → People and person → the rest. Apply the rows in section 3 and the glossary in section 4.
5. **Forms last:** collapse every add/edit sheet to voice or photo + name, with "More" for the rest.
6. **Clean-up:** remove dead UI (CaptureScreen, HomeScreen, EngineRow, LeaveByToggle, LIconButton, LStat, unused `aiBar` and `compact`) once nothing calls it.
