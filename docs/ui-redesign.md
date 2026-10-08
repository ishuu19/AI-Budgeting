# LedgerAI — UI Redesign

Status: Planning. No code yet. No feature is removed; every feature is re-homed (see section 7).
Applies the look in [ui-brief.md](ui-brief.md) and the calendar merge in [audit.md](audit.md) section C4.

---

## 1. What is wrong today

| Problem | Effect |
|---|---|
| Home, Money, Life and You each hold 6 to 9 equal tiles or rows | The eye has no entry point, so everything competes |
| Features are grouped by data type (Tasks, Alarms, Routines, Notes, Bills, Debts) | People think in moments (today, this week, what I owe), not tables |
| Four tabs overlap: Home, Life and Calendar all show "what is next" | The same question has three answers |
| Alarms, Notes and Routines have no way in | Features exist but cannot be found |
| Settings is seven chunks, five of them voice engines | Rarely used items take the most room |
| Voice, the main input, is one tab among five and its history cannot be edited | The core action is isolated |
| Screens do not follow a sequence | No beginning, middle or end |

---

## 2. Principles

| Principle | Rule applied |
|---|---|
| Hick's law | At most 5 tabs, at most 4 segments per tab, at most 5 rows per group before a "More" sheet |
| Chunking | One screen answers one question. Related items share one container, not many boxes |
| Storytelling | Order follows the day: **Now → Next → Money → Wrap-up**. Order shifts with time of day |
| Visual hierarchy | One hero per screen, one wide block, then small blocks. Tile sizes are never all equal |
| Progressive disclosure | Summary first. Detail in a sheet. Rare settings one level down |
| Thumb zone | Primary actions in the lower third. Voice is the centre button. Filters sit at the top, reachable by scroll, not by stretch |
| Recognition over recall | Chips and pickers instead of typed formats. Same icon means the same thing everywhere |
| Fitts' law | 48dp minimum touch target |
| Peak-end | Strong first block (Now) and a calm last block (Wrap-up) |
| Consistency | Same list row, same sheet, same empty state on every screen |

---

## 3. Information architecture

Five tabs, grouped by the question each answers.

| Tab | Question | Contains |
|---|---|---|
| **Today** | What matters right now? | Now, Next, money today, alerts, wrap-up, quote |
| **Plan** | What is coming and what must I do? | Calendar, Tasks, Log, Jobs. Alarms, classes, routines, study and habits appear inside as event kinds |
| **Voice** (centre) | Capture or ask anything | Speak, Notes, Ask, editable voice history |
| **Money** | Where do I stand? | Overview, Spend, Plan, Owed |
| **You** | How is the app set up? | Account, Voice and AI, Money rules, Alerts and widget, About |

Global: a search icon at the top of Today and Plan opens Search. Focus mode opens full-screen from any item or from Today.

### Navigation depth
| Action | Taps |
|---|---|
| Add a spend, task or note | 1 (Voice button) |
| See next item | 0 (Today) |
| Open any list | 2 |
| Rare setting | 3 |

---

## 4. Screens

Legend: **H** hero, **W** wide block, **S** small block, **L** list.

### 4.1 Today (spine of the app)

Sections run top to bottom in the order of the day. The hero and which section comes second change with time.

```
H   Now                     what is happening / next 90 minutes
    Study: Graphs  24:10    [Start] [Skip]

W   Next                    timeline, 3 to 4 items
    14:00 Algorithms   16:30 Study   18:00 Gym   20:00 Rent due

S S Safe today  $42        S  Attention   2 tasks · 1 bill · 1 gap
    ▓▓▓▓░░ $18                (red only when overdue)

W   Insight                 one ranked insight + one action

W   Wrap-up                 evening: unlogged hours, habits done, quote
```

| Time of day | Order |
|---|---|
| Morning | Now, Next, Safe today, Insight |
| Daytime | Now, Next, Attention, Safe today |
| Evening | Wrap-up first (log gaps, habits), then tomorrow's Next |

Rules: one big number (Safe today). Quote lives in Wrap-up, one line. Tap any block to open its home screen. No add buttons here; Voice is the add.

### 4.2 Plan

Segments: **Calendar · Tasks · Log · Jobs**

```
[ Calendar | Tasks | Log | Jobs ]            search
Chips: All  Events  Tasks  Classes  Alarms  Study  Habits

Agenda (default)     Today, Tomorrow, This week
Month toggle         top right
Row = time, title, kind dot, reminder icon
FAB: Add (sheet: choose kind)
```

| Segment | Content |
|---|---|
| Calendar | Agenda and month. Everything with a time: events, tasks, exams, classes, routines, alarms, study blocks, habit sessions. Kind chips filter it |
| Tasks | Items with no fixed time, plus overdue. Checkable. Grouped Late, Today, Soon, Done |
| Log | Day timeline of what you did, gaps in red, check-in answers |
| Jobs | Applications by status, paste to add |

One Add sheet creates any kind. Kind decides the fields:

| Kind | Extra fields |
|---|---|
| Event | End, place, leave-by |
| Task | Due, links |
| Exam, Class | Place, repeat |
| Alarm | Tone, repeat days |
| Routine | Repeat |
| Study | Topic, hours, find time |
| Habit | Days, nudges |

Reminders (up to 10) and repeat are shared fields in the same sheet. Edit, complete and delete live in the item sheet.

### 4.3 Voice (centre tab)

Voice is the main input, so it gets the centre and a full tab.

Segments: **Speak · Notes · Ask**

```
[ Speak | Notes | Ask ]

Speak
  Large mic (hero)
  Live transcript
  Confirm card: type, fields (pickers), [Save] [Edit]
  History (editable list)
     Transcript, result chip (Spend, Task, Budget, Event), time
     Tap: edit transcript, change result, delete, redo

Notes   list, search, tag, AI actions in the note menu
Ask     chat with calendar, money and notes context
```

Voice must reach everything: spend, budget, bill, debt, goal, calendar event of any kind, note, query ("how much did I spend"), and edit or delete by voice. Multiple items in one sentence create several confirm cards. Every saved result shows a link to the item and Undo.

### 4.4 Money

Segments: **Overview · Spend · Plan · Owed**

```
Overview
H   Safe today / month progress / health score
W   Spend vs guide, last 7 days
W   Insights stack (top 3)
S S Forecast end of month   |   Next big payment

Spend      search, month switcher, In/Out, category chips, list. FAB add
Plan       Budgets (progress rows), Goals (progress rows), Forecast
Owed       Bills (due soonest first), Debts (owe / owed), pay and settle actions
```

Insights detail opens from Overview in the tabs defined in [dashboard-plan.md](dashboard-plan.md).

### 4.5 You

Five rows only. Each opens a sheet or sub-screen.

```
Profile card      name, email, Sign in or Sign out, Sync status
Voice and AI      engine, model download, AI consent for notes and audio
Money rules       currency, tracking, on hand, alert threshold
Alerts and widget notifications, quiet hours, widget theme and private mode
About             privacy, version
```

Voice engines collapse into one row with a picker sheet. Sign in is available to local users.

---

## 5. Layout system

### 5.1 Block sizes (never all equal)

| Size | Width | Use |
|---|---|---|
| H hero | Full | One per screen, one large number or one active item |
| W wide | Full | Lists, timelines, charts, insights |
| S small | Half | Single counts or status, always in a pair |
| Row | Full | List item, 56 to 64dp |

Maximum per screen: 1 hero, 3 wide, 1 small pair before the scroll. Never two small pairs stacked.

### 5.2 Containers
- Group related rows in one container with dividers, not one box per row.
- Section labels: one word.
- Page background white, containers royal green, accent gold, danger red for overdue, over-budget and gaps only.

### 5.3 Patterns used everywhere
| Pattern | Use |
|---|---|
| Segmented control (max 4) | Switching views inside a tab |
| Kind chips | Filtering a list |
| Add sheet | One per tab, never a full screen |
| Item sheet | Edit, Complete, Delete as ghost button, with confirm |
| Row | Leading icon or dot, title, one line meta, trailing value |
| Empty state | Icon and 2 to 3 words |
| Pickers | Date, time, duration, category. No typed formats |

### 5.4 Copy
Titles one word. No helper paragraphs. Buttons are verbs. Numbers carry currency from settings.

---

## 6. Flows

### Morning
```
Open → Today → Now / Next → tap item → sheet (Start, Move, Done)
```

### Capture
```
Voice button → speak → confirm card(s) → Save → toast with Undo and Open
```

### Money check
```
Today → Safe today → Money Overview → Spend or Plan → item sheet
```

### Evening
```
Today → Wrap-up → Log gap → fill entry → habits tick → tomorrow preview
```

### Weekly review
```
Money → Overview → Insights → action sheet → apply (budget, transfer)
```

---

## 7. Feature placement (nothing removed)

| Feature | New home |
|---|---|
| Dashboard KPIs, insights, health score | Today (summary), Money → Overview |
| Safe-to-spend guide, speculations | Today, Money → Overview |
| Transactions / Spend | Money → Spend |
| Budgets | Money → Plan |
| Goals | Money → Plan |
| Forecast | Money → Plan, Money → Overview |
| Analytics / Insights | Money → Overview |
| Bills | Money → Owed |
| Debts | Money → Owed |
| Calendar, imported timetable, classes | Plan → Calendar |
| Tasks | Plan → Tasks, and in Calendar when timed |
| Reminders | Field on any event |
| Routines | Plan → Calendar, kind Routine |
| Alarms, ringing | Plan → Calendar, kind Alarm |
| Study planner, free-time finder | Plan → Add → Study |
| Habit sessions, nudges | Plan → Add → Habit |
| Focus mode, timer | Full screen from Today or any study item |
| Leave-by | Event field |
| Life log, check-ins, gaps | Plan → Log, Today → Wrap-up |
| Job tracker | Plan → Jobs |
| Voice capture and history | Voice → Speak |
| Notes, note nudges | Voice → Notes |
| AI assistant | Voice → Ask |
| Search | Icon on Today and Plan |
| Quote | Today → Wrap-up |
| Widgets | Configured in You → Alerts and widget |
| Settings | You |
| Sync, sign in and out | You → Profile |

---

## 8. Changes to the current app

| Area | Change |
|---|---|
| Tabs | Home → Today. Money stays. Life → Plan. Voice stays at centre but gains Notes and Ask. You is reduced to five rows |
| Money hub | Nine-tile grid replaced by four segments |
| Home | Replace equal boxes with hero, wide and small blocks in day order |
| Settings | 7 chunks become 5 rows. Voice engine list becomes one picker |
| Tasks, Alarms, Notes | Become views inside Plan and Voice, so they are reachable |
| Voice | History list added, editable. Confirm cards use pickers |
| Navigation | Segmented controls inside tabs. Back stack keeps the segment on rotation (`rememberSaveable`) |
| Search | Results open the item |

Dependencies: the calendar merge must land first so Plan can show one list. The Daily Spend Guide and the insight engine feed Today.

---

## 9. Build order

| Step | Deliverable |
|---|---|
| U1 | Shared layout kit: hero, wide, small pair, container, segmented control, kind chips, unified item sheet |
| U2 | Tab shell and renamed navigation, segment state saved |
| U3 | Plan: Calendar agenda and month, Tasks, Add sheet (after calendar merge) |
| U4 | Voice tab: Speak with editable history, Notes, Ask |
| U5 | Money segments: Overview, Spend, Plan, Owed |
| U6 | Today: Now, Next, Attention, Safe today, Insight, Wrap-up with time-of-day order |
| U7 | You: five rows, voice engine picker, sign in for local users |
| U8 | Log and Jobs moved under Plan; Search opens items; empty, loading and error states |
| U9 | Accessibility pass (48dp targets, headings, content descriptions, font scale 1.3), rotation QA |

---

## 10. Success checks

- Any frequent action reachable in 2 taps or fewer.
- Every screen has exactly one hero.
- No tab shows more than 4 segments, no group more than 5 rows.
- Every feature in section 7 is reachable from the UI.
- No screen contains more than one pair of small blocks.
- First scroll of Today answers: what now, what next, how much can I spend.
