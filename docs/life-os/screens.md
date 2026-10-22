# Screens: visual redesign

Status: **wireframes only. No code is changed by this document.**
Date: 2026-10-22.
Direction chosen: **calm and minimal**, with enough character that it does not look like a template.
Structure (tabs, segments, features, data) comes from [app-design.md](app-design.md). This file only says what each screen looks like. If the two disagree, app-design.md wins on structure and this file wins on appearance.

---

## 1. The idea

Today's screens are stacks of bordered cards in blue and amber: every block looks equally important, so none is. The redesign has one rule:

> **Each screen has one thing to read first, one thing to do, and everything else is quiet.**

What makes it recognisable instead of generic:

1. **Numbers and dates are set large in a serif.** The safe-to-spend amount, a countdown, a day number. They are the "headline" of their screen. Everything else is small and plain.
2. **Lists are not cards.** Rows sit directly on the page, separated by a hairline. No box around a list, no box inside a box.
3. **One accent color**, used only for the single action and for "now". Money direction uses text color, never fills.
4. **A thin vertical line** (the "thread") runs down the left of time-ordered screens (Agenda, Trip, Achievements). It is the app's signature: a day is a line with things hanging off it.
5. **Space is the divider.** Sections are separated by 32 dp of air and a small lowercase label, not by headers on tinted bars.

What is removed: card borders, shadowed tiles, tinted section bars, pill-everything, icon-in-circle rows, floating bordered tab bar, gradient or filled hero blocks.

---

## 2. Tokens

### 2.1 Color

Two themes, following the system. Values replace the current `Color.kt` set; names map to the existing `L.*` getters so screens change without a new theming system.

| Role | Light | Dark | Use |
|---|---|---|---|
| Page | `#FAF8F4` warm paper | `#121214` | Background of every screen |
| Ink | `#1C1B1A` | `#ECE9E2` | Main text, big numbers |
| Muted | `#77736B` | `#9A968E` | Secondary text, labels |
| Hairline | `#E8E4DB` | `#26252A` | Row dividers, the thread |
| Accent | `#2F3E8F` ink blue | `#AAB6F4` | The one action, "now", links, selected tab |
| Accent wash | `#E9ECF8` | `#1E2132` | Selected chip, pressed row (only fill used besides Page) |
| In | `#2E6B4B` | `#86C9A4` | Income amounts (text only) |
| Out | `#A33B32` | `#F09A92` | Overdue, expense warnings (text only) |

Rules: Accent never fills a large area. Only the primary button and the mic are filled with it. There is no amber. Contrast of Ink, Muted and Accent on Page is at least 4.5:1 in both themes (check when built).

### 2.2 Type

Two families. A serif for display numbers and screen titles, the system sans for everything else. The serif is bundled in `res/font` (a variable serif such as Newsreader or Fraunces; the final choice is made when building, anything with good numerals works).

| Style | Face | Size / line | Weight | Used for |
|---|---|---|---|---|
| Display | Serif | 56 / 60 | Regular | Safe-to-spend, big countdown, day number |
| Title | Serif | 28 / 34 | Regular | Screen titles |
| Heading | Sans | 18 / 24 | Medium | Row titles that matter, sheet titles |
| Body | Sans | 16 / 24 | Regular | Row text, notes |
| Small | Sans | 13 / 18 | Regular | Row detail, times |
| Label | Sans | 12 / 16, +4% tracking | Medium, lowercase | Section labels |

Numbers use tabular figures so columns of money align.

### 2.3 Space, shape, motion

- Grid: 4 dp. Page gutter 20 dp. Section gap 32 dp. Row height 56 dp (72 dp with a second line). Minimum touch target 48 dp.
- Shape: rows have no shape. Sheets and the keyboard-attached bar have a 20 dp top radius. Buttons are 12 dp. Chips are fully round. Photos are 12 dp.
- Elevation: none on the page. Sheets use a scrim, not a shadow.
- Motion: 180 ms ease-out for sheets and expands, 120 ms for taps. The thread draws downward once on first open of Agenda. Reduced-motion setting turns every animation into an instant change.
- Haptics: one light tick on checking a task and on saving from the suggestion sheet.

### 2.4 Components (the whole kit)

Small on purpose. Every screen below is built only from these.

| Component | Looks like | Notes |
|---|---|---|
| **Screen** | Title (serif) at top left, optional one-line subtitle in Muted, content scrolls under it | Replaces `LScreen`. Large title collapses to a small one on scroll. |
| **Label** | Lowercase 12 sp Muted text above a group | The only section header. |
| **Row** | Leading (optional), title, detail, trailing. Hairline under it, inset to the text | Replaces cards and `LGroupRow`. |
| **Thread row** | A Row with a 1 dp vertical line and a 7 dp dot at the left, time in Small to the left of the line | Time-ordered screens. Dot is filled Accent for "now". |
| **Number** | Display or Title serif number with a Muted caption beneath | |
| **Chip** | Round, text only; selected = Accent wash plus Accent text | Filters and the segment switch. |
| **Segment** | A row of 3 or 4 text labels with a 2 dp underline on the selected one | Replaces the boxed segmented control. Swipeable. |
| **Primary button** | Filled Accent, 48 dp, 12 dp radius, one per screen | |
| **Text button** | Accent text, no fill | Secondary actions. |
| **Sheet** | Bottom sheet, 20 dp top radius, drag handle, title, content, one Primary | All forms. Full-height when they have more than 5 fields. |
| **Field** | Label above, text on a hairline underline. No box | Error text replaces the label, in Out color. |
| **Check** | 22 dp circle, hairline when empty, Accent fill with tick when done | |
| **Empty** | One serif sentence, one Muted line, one Text button, centred at 40% height | No illustrations. |
| **Toast** | Single line at the bottom with Undo | After every save and delete. |
| **Bar** | Bottom navigation (section 3) | |

---

## 3. Shell

```
┌─────────────────────────────────────┐
│ Search                         (M)  │  top: a search line + avatar
│                                     │
│  Title                              │
│  ...content...                      │
│                                     │
├─────────────────────────────────────┤  hairline, no card, no shadow
│  Home   Plan    ( ◉ )   Money  Library│
└─────────────────────────────────────┘
```

- The bar sits flush at the bottom on the Page color with a hairline above. It is not a floating bordered card.
- Four text-and-icon items and the mic in the middle. Icons are 24 dp line icons. Only the selected item shows its label and is colored Accent. The others are Muted icons.
- The mic is the only filled round control in the app: 56 dp, Accent, raised 8 dp above the bar line. Tap = speech sheet. Hold = hold-to-talk.
- Top line: the word "Search" in Muted with a magnifier on the left, the avatar initial on the right. Tapping the line opens Search. It scrolls away with content and returns on scroll up.
- Segments (Plan, Money) sit under the title, as text with an underline. Swiping the page changes the segment.

---

## 4. Home

Job: what needs me, right now. Reads top to bottom in four beats.

```
┌─────────────────────────────────────┐
│ Search                         (M)  │
│                                     │
│ Thursday                            │  Title (serif)
│ 22 October                          │  Muted
│                                     │
│ next                                │  label
│ 14:30   Dentist, Dr. Rahman         │  Heading
│         in 2 h 10 min · Banani      │  Small, Muted  ("in 2 h" in Accent)
│                                     │
│ safe to spend today                 │  label
│ ৳ 1,240                             │  Display
│ ৳ 460 spent                         │  Muted
│                                     │
│ coming up                           │  label
│ Maya's birthday          12 Nov     │  row
│ Dhaka trip               20 Nov     │  row
│                                     │
│ ┌─────────────────────────────┐ (◉) │  ask bar (Field + mic)
│ │ Tell me anything…           │     │
│ └─────────────────────────────┘     │
├─────────────────────────────────────┤
```

- **Date**: tap opens Plan → Agenda.
- **Next**: one item, chosen by the earliest-time rule in app-design 5.1. A task shows its Check on the right instead of a time. Tapping the body opens the record. If none: the whole block is a single Muted line, "Nothing due."
- **Safe to spend**: hidden if there is no money data. Tap opens Money → Overview.
- **Coming up**: at most two rows, birthdays and trips in the next 14 days. Hidden if none.
- **Ask bar**: a hairline-underlined field pinned above the bottom bar, with the filled mic at its right. Typing and speaking both open the suggestion sheet (section 13) or Ask.
- Small screens: Coming up collapses to one row. The Display number never shrinks below 44 sp.

---

## 5. Plan

Title "Plan". Segments: **Agenda · Tasks · Appointments · Routines**.

### 5.1 Agenda

The thread screen.

```
┌─────────────────────────────────────┐
│ Plan                                │
│ Agenda  Tasks  Appointments  Routines│
│ ───────                             │
│  M   T   W  [T]  F   S   S          │  week strip, today circled in Accent
│ 19  20  21  22  23  24  25          │
│                          ▾ month    │
│                                     │
│ 09:00 ●─ Statistics class     Room 4│
│       │   until 10:30               │
│ 12:00 ●─ Lunch with Rafi            │
│       │                             │
│ 14:30 ◉─ Dentist, Dr. Rahman        │  ◉ = next, Accent
│       │   Banani · Appointment      │
│       │                             │
│ any   ○─ Send report         ( )    │  undated-time tasks, a Check
│       │                             │
│                                (+)  │  add
└─────────────────────────────────────┘
```

- Week strip: today is a filled Accent number, other days plain. Days that have items show a 3 dp dot under the number. Birthdays and trips never add a dot.
- **month** expands the strip into a month grid (dots the same way). Selecting a day collapses it back.
- The thread runs under the dots of the rows. Time is in Small to the left. A past row is Muted. The next row's dot is a ring in Accent.
- **Add (+)**: the only floating control on the screen, a 48 dp outlined circle (hairline border, Accent plus). Tapping opens a four-row sheet: Task, Appointment, Event, Alarm.
- Row types differ by one word in the detail line, not by icons or colors: "Class", "Exam", "Appointment", "Study", "Habit".
- Optional dates strip (Settings → Plan → Show dates here): one Muted line above the first row, "Maya's birthday · Dhaka trip, day 2 of 5".
- Swipe a task row right to complete, left to open delete. A swipe never deletes without the Toast Undo.
- Empty day: a serif line "Nothing planned." and a Text button "Add something".

### 5.2 Tasks

```
│ open                                │
│ ( ) Send report            today    │
│ ( ) Book flights           tomorrow │
│ ( ) Renew library card     Fri 24   │
│                                     │
│ later                               │
│ ( ) Organise photos                 │
│                                     │
│ done · 30 days                  ▾   │
```

One Row per task: Check, title, due date in Small (Out color if overdue). No cards, no priority colors. Tap opens the Sheet (title, notes, due date and time, reminder). **Save as achievement** is a Text button on a done task's sheet. Add is a field fixed above the bar: "Add a task…" (Enter saves; a date word like "tomorrow" is parsed).

### 5.3 Appointments

```
│ upcoming                            │
│ Thu 22 · 14:30                      │  date as Heading
│ Dentist, Dr. Rahman                 │
│ Banani · in 2 h                     │
│                                     │
│ Mon 26 · 10:00                      │
│ Visa interview                      │
│ US Embassy · Rafi                   │
│                                     │
│ past                       filter ▾ │
│ Mon 12 · Eye check     Done         │
```

Each entry is two lines under a Heading date. Detail line: place, then person, then countdown. A past entry still marked upcoming shows three Text buttons on its row: **Done · Reschedule · Cancel**. Filter chips: Upcoming, Past, Cancelled.

Sheet (create and edit): Title, Date and time, Place, Person (search People or add a name), Notes, Reminders as removable rows ("1 day before", "1 hour before"), and the Primary button.

### 5.4 Routines

One scroll, four labelled groups.

```
│ alarms                              │
│ 06:30   Wake up       Mon–Fri  (━●) │  switch
│ 08:00   Weekend       Sat Sun  (●━) │
│ + Add alarm                         │
│                                     │
│ habits                              │
│ ( ) Read 20 pages        12 days    │
│ ( ) Walk                  3 days    │
│                                     │
│ study                               │
│ Statistics · 3 blocks this week  ›  │
│                                     │
│ focus                               │
│ Start 25 min    Start 50 min   ·  ● │  two Text buttons and a custom ›
│ Today: 1 h 15 min                   │
```

Alarm times are Heading size and tabular. Streaks (the "12 days") exist only here. Focus is a full-screen timer: a Display countdown centred, topic below, Pause and Stop as Text buttons, on an otherwise empty Page.

---

## 6. Money

Title "Money". Segments: **Overview · Spend · Pay · Plan**.

### 6.1 Overview

```
│ safe to spend today                 │
│ ৳ 1,240                             │  Display
│ ৳ 460 spent · guide ৳ 1,700         │  Muted
│ ━━━━━━━━━━━━━━━░░░░░░░░░░           │  1 dp line with a 4 dp marker, no bar fill
│                                     │
│ this month                          │
│ Spent        ৳ 31,400               │
│ Budget left  ৳ 18,600               │
│ Bills due    ৳  6,200               │
│                                     │
│ where it went                       │
│ Food        ▬▬▬▬▬▬▬▬▬▬  ৳ 11,200    │  thin bars, one ink color
│ Transport   ▬▬▬▬▬        ৳  5,100   │
│ Rent        ▬▬▬▬         ৳  4,000   │
│ + 4 more                            │
│                                     │
│ one thing                           │
│ You spent 30% more on food than     │  the day's single insight, plain text
│ last month.                         │
│                                     │
│ forecast                         ▾  │  collapsed
```

The hero is the number and nothing else. Tapping it opens a breakdown sheet with the rules behind it (the old Safe-to-spend screen). Charts are single-color thin bars; no pies, no legends.

### 6.2 Spend

```
│ Search spending…              [receipt]│
│ This month ▾   All ▾                │  two filter chips
│                                     │
│ today                               │
│ Tapal Grocers            −৳ 860     │  Out only if over a budget; otherwise Ink
│ Food · 14:10                        │
│ Salary                +৳ 45,000     │  In color
│ Income · 09:00                      │
│                                (+)  │
```

Grouped by day with a label ("today", "yesterday", "Mon 20"). Row: merchant, category and time, amount right-aligned and tabular. Add sheet: a big amount Field first (Display size) with a custom numeric pad, then Category chips, Merchant, Date, Note, **Save**. The receipt icon at the top right opens Receipts (6.5).

### 6.3 Pay

```
│ Bills   Repeats   Owed              │  three chips
│                                     │
│ due soon                            │
│ Electricity           ৳ 2,400       │
│ due Fri 24                          │
│ Netflix               ৳ 800         │
│ renews Sun 26 · monthly             │
│                                     │
│ later                               │
│ Rent                  ৳ 20,000      │
│ due 1 Nov                           │
```

Same Row for the three chips. Owed rows read "Rafi owes you ৳ 1,500" or "You owe Mim ৳ 700", with the remaining amount on the right. Overdue date text is Out. Tapping opens a sheet with Mark paid, Edit and (for owed) Add payment.

### 6.4 Plan (money)

```
│ budget                              │
│ Food        ৳ 11,200 of 15,000      │
│ ───────────────────░░░              │
│ Transport   ৳ 5,100 of 6,000        │
│ ...                                 │
│                                     │
│ goals                               │
│ Laptop      ৳ 40,000 of 90,000      │
│ by March                            │
│                                     │
│ wishes                              │
│ Mechanical keyboard     ৳ 8,500  ★★ │
│ wanted for 23 days                  │
│ Trip to Cox's Bazar     price unknown│
```

The three sections are labelled groups on one page. Progress is one hairline with an Ink segment, no colored bars. Wishes show priority as 1 to 3 small dots, not stars, and "price unknown" when empty. Tapping a wish opens its sheet with **Start saving**, **Buy soon**, **Bought**, **Drop** as four stacked Text buttons.

### 6.5 Receipts

List with a thumbnail 56 dp on the left of each Row (merchant, date, total, "Draft" in Muted). The scan button is the Primary button at the bottom. Review screen: the photo on top at 40% height (pinch to zoom), below it Field rows for merchant, date, total, then each line as a Row with an editable name and amount. Low-confidence values have a Muted dotted underline and the words "check this". **Confirm** is the Primary button; **Add food to pantry** appears afterwards as a Text button.

---

## 7. Library

```
│ Library                             │
│                                     │
│ dates                               │
│ Birthdays            Maya, 12 Nov  ›│  title left, status right in Muted
│ Trips                Dhaka, 9 days ›│
│                                     │
│ records                             │
│ Vault                  4 folders   ›│
│ Passwords                 12       ›│
│ Achievements        Passed IELTS   ›│
│ People                    31       ›│
│                                     │
│ work                                │
│ Jobs                 2 interviews  ›│
│                                     │
│ home                                │
│ Pantry and shopping   5 expiring   ›│
│ Wardrobe                  42       ›│
│ Household             Not set up   ›│
│                                     │
│ phone                               │
│ App time          Today 3 h 10 min ›│
```

A plain list of names. No icons, no tiles, no counts in badges. The status is a Muted string read from the record, never a preview of the data. Passwords shows no status while locked.

---

## 8. New feature screens

### 8.1 Birthdays

List:

```
│ Birthdays                      (+)  │
│                                     │
│ november                            │
│ 12      Maya                        │  day number in serif Title
│         turns 26 · in 21 days       │
│ 30      Dad                         │
│         in 39 days                  │
│                                     │
│ december                            │
│ 03      Rafi                        │
```

A month label groups rows; the day number is the visual anchor. Nothing is colored except a countdown under 8 days, which turns Accent. Tap opens the detail sheet.

Detail and form (one sheet):

```
│ Maya                                │  Title
│ 12 November  ·  turns 26            │
│                                     │
│ celebration   00:00  ›              │  tap to set a time
│                                     │
│ remind me                           │
│ 1 month               (━●)          │
│ 2 weeks               (━●)          │
│ 1 week                (━●)          │
│ 3 days                (━●)          │
│ 1 day                 (━●)          │
│ 5 hours               (━●)          │
│ 30 minutes            (━●)          │
│ 10 minutes            (━●)          │
│ at the time           (━●)          │
│                                     │
│ next: 12 Oct 09:00  (1 month)       │  Muted, the next reminder still on
│                                     │
│ note (private)                      │
│ Likes fountain pens                 │
│                                     │
│ [ Save ]                            │
```

Add flow: tap (+), type a name (suggestions from People), pick month and day with two wheel columns, optional year, Save. Defaults come from Settings. "Import from contacts" is a Text button in the empty state and in the overflow menu.

### 8.2 Trips

```
│ Trips                          (+)  │
│                                     │
│ now                                 │
│ Dhaka                               │  Title
│ day 2 of 5 · until 24 Oct           │
│                                     │
│ planned                             │
│ Cox's Bazar                         │
│ 20 – 24 Nov · in 29 days            │
│                                     │
│ past                                │
│ Sylhet · Aug 2026                   │
```

Detail: the place as Title; a Display countdown or "day 2 of 5"; the date range; a thin thread with one dot per day for trips under 15 days (today filled); notes; **During this trip**: read-only rows for appointments and classes in range ("Wed 22, Dentist"). Reminder switches (1 week, 1 day) at the bottom. Form fields: name, place, start, end (date pickers inline), notes.

### 8.3 Achievements

```
│ Achievements                   (+)  │
│                                     │
│ 2026                                │
│ ●─ 3 Sep                            │
│ │  Passed IELTS                     │
│ │  Band 7.5                [photo]  │
│ │                                   │
│ ●─ 14 Jun                           │
│ │  Finished the thesis              │
│ │                                   │
│ 2025                                │
│ ●─ 2 Dec                            │
│ │  First 10 km run                  │
```

The thread again, one dot per achievement, date to its right, title as Heading, note in Muted, one thumbnail 56 dp. No counts, badges, streaks or totals. The form is title, date, note and up to 3 photos.

### 8.4 Vault

Folders:

```
│ Vault                          (+)  │
│                                     │
│ pinned                              │
│ Passport & IDs      🔒         ›    │
│ Important                      ›    │
│                                     │
│ folders                             │
│ Notes                    38    ›    │
│ Inbox                     3    ›    │
│ Medical                   7    ›    │
│ University               21    ›    │
```

Folder Rows with a count on the right, a small lock glyph for locked ones. The (+) offers **New folder**.

Inside a folder:

```
│ ‹ Medical                      ⋯    │
│                                     │
│ Blood test, March                   │  note
│ Fasting for 12 h before…            │
│ Vaccination card.pdf     2.1 MB     │  file
│ Prescription.jpg          640 KB    │
│                                     │
│ [ Write ]   [ Upload ]   [ Scan ]   │  three Text buttons fixed above the bar
```

Notes and files share one Row: title, then one Muted line (first words or size). A small file-type word, not an icon, sits in Muted on the right ("PDF", "JPG"). The overflow menu: Rename, Pin, Lock, This device only, Move, Delete.

Locked folder: opening shows a blank Page with "Unlock" as the Primary button and the phone's biometric prompt. After unlocking the window is secure (no screenshots), and a Muted line at the top reads "Only on this phone".

Note editor: full screen, Title field in serif, Body field in Body, nothing else. A toolbar of three Text buttons appears only with the keyboard: Summarise, Tag, Ask. Saves automatically.

Inbox: each Row has the clip text (two lines), the source app in Muted, and two Text buttons, **File** and **Delete**.

### 8.5 Passwords

Locked state: blank Page, "Passwords" Title, a Muted line "Stored only on this phone", Primary **Unlock**.

List:

```
│ Passwords                      (+)  │
│ Search…                             │
│                                     │
│ 2 are older than their change date  │  health line, Muted, tap to filter
│                                     │
│ Gmail                               │
│ rafi@…                              │
│ Bank                                │
│ rafi.k                              │
```

Row: title, site or masked username. No icons, no favicons (nothing is fetched).

Detail:

```
│ ‹ Gmail                             │
│                                     │
│ username                            │
│ rafi.k@gmail.com            [Copy]  │
│ password                            │
│ ••••••••••••               [Show]   │
│                             [Copy]  │
│ site       accounts.google.com  ›   │
│ changed    14 Mar 2026 · 7 months   │
│ change every   180 days             │
│                                     │
│ note                                │
│ Recovery phone: …                   │
```

Copy shows a Toast: "Copied. Clears in 30 s." Form: title, site, username, password with **Generate** (length slider 12 to 32, symbol switch), change-every chips (Never, 90, 180, 365 days), note. The whole flow is on a secure window.

### 8.6 App time

Not allowed:

```
│ App time                            │
│                                     │
│ See where your phone time goes.     │  serif sentence
│ Needs usage access in Settings.     │
│ [ Allow ]                           │
```

Allowed:

```
│ App time                            │
│ Today   7 days   30 days            │
│                                     │
│ 3 h 10 min                          │  Display
│ across 14 apps                      │
│                                     │
│ Instagram              1 h 05       │
│ ▬▬▬▬▬▬▬▬▬▬▬▬                        │
│ YouTube                  48 min     │
│ ▬▬▬▬▬▬▬▬▬                           │
│ Chrome                   31 min     │
│ ▬▬▬▬▬▬                              │
```

The total is the Display number. One thin ink bar under each app, scaled to the top app. No icons, no color per app. Tap an app: 14 thin vertical bars for the last 14 days, today highlighted in Accent, plus the average.

### 8.7 Wishes

Lives inside Money → Plan (6.4). Wish sheet:

```
│ Mechanical keyboard                 │
│ ৳ 8,500  ·  priority 2  ·  by Dec   │
│ wanted for 23 days                  │
│ link   keychron.com…            ›   │
│                                     │
│ note                                │
│ Quiet switches                      │
│                                     │
│ Start saving                        │
│ Buy soon                            │
│ Bought                              │
│ Drop                                │
```

---

## 9. Existing screens, restyled

All use the section 2 kit. Content and behaviour follow app-design.md section 8.

### 9.1 People

List: alphabetical with a letter label. Row: name, then org or role in Muted. Detail: Title name; below, quiet rows for **Birthday**, **Appointments**, **Owed**; then labelled groups **memories**, **moments** (photo thumbnails 56 dp in a row), **promises** (with a Check). Each memory shows its source ("From receipt, 11 Oct") in Muted and "inferred" in Out color if unconfirmed.

### 9.2 Jobs

Rows grouped by status labels (interview, applied, saved, offer, rejected). Row: role, company, last date. Sheet: company, role, status chips, link, date, notes; **Add interview** is a Text button. Share-in from other apps opens the same sheet filled and unsaved.

### 9.3 Pantry and shopping

Two Segments: **Pantry · Shopping**. Pantry rows: name, quantity and place on the second line, expiry in Small (Out if within 3 days). Shopping rows: Check, name, quantity. Add field fixed above the bar for both ("Add eggs…").

### 9.4 Wardrobe

A two-column photo grid with 12 dp radius, name under each in Small. No frames. Tap opens the piece; **Outfit ideas** is a Text button at the top. Suggestions list garment names only.

### 9.5 Household

Not set up: Empty (one sentence, Text button "Create a household"). Set up: members as Rows, then **shared lists** as Rows. A Muted line states what is never shared.

### 9.6 Search

Opens full screen with the field focused. Results grouped by labelled kind (plan, money, records, people), five per group, "show all" Text button. Passwords and locked folders never appear. Recent searches are Muted Rows before typing.

### 9.7 Ask

A conversation with no bubbles: the user's line is Heading in Ink, the answer beneath in Body, separated by 24 dp. Sources appear as Muted lines under an answer ("from 3 transactions"). Proposed actions are Rows with a Primary **Do it** or a Text **Skip** (nothing runs before the tap). History is a Row list reached from the top-right. Input field is fixed above the bar, with mic.

### 9.8 Settings

Reached from the avatar. A single list of labelled groups from app-design 13.1. Row = title left, current value right in Muted, chevron. Switches are the only control shown inline. Sheets for editing. At the bottom, Muted version text.

### 9.9 Sign in

```
│                                     │
│                                     │
│ Ledger                              │  serif Display
│ Your money, days and things,        │
│ in one quiet place.                 │
│                                     │
│ [ Continue with Google ]            │  Primary
│ Use on this phone only              │  Text button
```

Nothing else: no illustration, no feature carousel.

---

## 10. System surfaces

### 10.1 Suggestion sheet (mic result)

```
│ ───                                 │
│ You said                            │  label
│ "Lunch 450 and Maya's birthday is   │  Muted, quoted
│  14 November"                       │
│                                     │
│ Expense  ৳ 450                      │
│ Food · today                [Save]  │
│                                     │
│ Birthday                            │
│ Maya · 14 Nov               [Save]  │
│                                     │
│ [ Save all ]            Edit words  │
```

One Row per suggestion with its own Text **Save**; tapping the row body opens that item's form pre-filled. Saved rows turn Muted with a tick. A missing value is shown as an empty Field in Out color ("amount?"), never a guess. No suggestion: "Couldn't sort this." with Primary **Save as note**. Passwords: a single Row, "Open Passwords".

The speech screen itself is the phone's. While it is open the app shows nothing.

### 10.2 Notifications

Title is the item, one-line body is the distance or detail, nothing else. Birthday: `Maya's birthday` / `in 3 days · turns 26`. Actions only where useful: Done on tasks, Snooze 1 h on birthday clock reminders. No emoji, no images. Each family has its own channel.

### 10.3 Alarm ringing

Full screen, Page color. The time in Display, centred. The label in Heading below. **Dismiss** is a large Primary button at the bottom, **Snooze 5 min** a Text button above it. No animation except a slow 1 dp ring around the time.

### 10.4 Widgets

Today (4×2): Page background, no border. Date in Small, **Next** time and title in Heading, safe-to-spend in Title serif, up to 3 tasks with Checks. Mic widget (1×1 and 2×1): the filled Accent mic on a transparent background. Private mode replaces amounts with "••••". Dark theme follows the system.

### 10.5 Ledger AI panel

A small bottom sheet over the other app: the selected text in a quote block with a hairline on its left, a message Field, an optional **Add screen** Text button, and **Save** and **Ask** side by side (Save is a Text button, Ask is the Primary). Refuses on Passwords and locked folders with one line: "Can't read this screen".

---

## 11. States every screen must have

| State | Look |
|---|---|
| Loading | Rows replaced by hairline-only placeholders. No spinners, no shimmer. |
| Empty | The Empty component: one serif sentence, one Muted line, one Text button. |
| Error | A Muted line at the top of the list with a Text **Retry**. Existing data stays visible. |
| Offline | A Muted line "Offline. Changes will sync." once, under the title. |
| Saved / deleted | Toast with Undo for 5 seconds. |
| Locked | Blank Page, the title, one sentence, Primary **Unlock**. |

Accessibility: every control 48 dp, labels for every icon-only control, no meaning by color alone (overdue also says "overdue", income also has a "+"), text scales to 200% without clipping, Display numbers have a spoken form ("one thousand two hundred forty taka").

---

## 12. What happens to the current design code

| Current | Fate |
|---|---|
| `theme/Color.kt`, `Theme.kt`, `Type.kt` | Rewritten to section 2. `L.*` names kept as aliases so screens migrate one at a time. |
| `components/Ledger.kt`, `LayoutKit.kt`, `ToolKit.kt`, `PickChips.kt` | Rebuilt as the section 2.4 kit. Old card and group-row components deleted when the last screen stops using them. |
| `LedgerTabBar` in `AppNavigation.kt` | Replaced by the section 3 bar. |
| Widgets (Glance) | Restyled to 10.4 with the same data. |
| Splash and app icon | Icon unchanged until you decide to change the logo. |

---

## 13. Build order for the visual change

Independent of the feature steps in app-design.md section 19, and doable first because it changes no data.

| Step | Work | Check |
|---|---|---|
| 1 | Tokens and fonts (2.1 to 2.3), light and dark | A single "kit" preview screen shows every component in both themes. Contrast measured. |
| 2 | Components (2.4) | Each has a Compose preview, a TalkBack label, a 200% font test. |
| 3 | Shell: bottom bar and top line | All five tabs open; mic tap and hold still work. |
| 4 | Home, Plan Agenda and Tasks | Compared against section 4 and 5 wireframes on a small and a large phone. |
| 5 | Money (four segments) | Same. Numbers align in the Spend list. |
| 6 | Library and the existing feature screens | Each screen uses only kit components; no `Card` left. |
| 7 | New feature screens as their features land | Per app-design.md step order. |
| 8 | Sheets, notifications, alarm, widgets, panel | Matches section 10. |
| 9 | Delete old components and the old theme values | No reference to the old amber or card components remains. |

---

## 14. Open questions

| # | Question | Default |
|---|---|---|
| S1 | Serif for titles and numbers: yes or system-only? | Yes. It is the main thing that stops the app looking generic. |
| S2 | Accent color | Ink blue `#2F3E8F`. Easy to swap; only one value changes. |
| S3 | Should the app icon change to match? | No, not until you want a new logo. |
