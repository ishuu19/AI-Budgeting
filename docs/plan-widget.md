# LedgerAI — Widget Plan

Status: Implemented (W1–W6, W8). Glance 1.1; old three widgets removed.
Replaces the three current widgets (Quote/Voice, Day Schedule, Calendar Events) with one widget family. Uses Glance, the existing repositories and the features in [plan-new-features.md](plan-new-features.md).

---

## 1. Problems with the current widgets

| Issue | Cause |
|---|---|
| Background blends with tiles | Widget background and inner boxes are both brand emerald (`#0F5C45`) |
| Three unrelated widgets | Quote/voice, day schedule and calendar list each solve one thing |
| Display only | Little or no action without opening the app |
| No finance, plan or job data | Not connected to money, habits, study or jobs |

---

## 2. Visual system

The fix is **layering**: canvas, tile, accent. Each layer must be clearly different from the one beneath it.

### 2.1 Palette

| Role | Dark | Light |
|---|---|---|
| Canvas (widget background) | `#06231A` deep ink green | `#FFF9EE` ivory |
| Tile | `#0F5C45` emerald | `#0F5C45` emerald |
| Tile raised (active, selected) | `#147A5A` | `#147A5A` |
| Text on tile | `#FFFDF8` | `#FFFDF8` |
| Text muted on tile | `#D4C48A` at 80% | `#D4C48A` |
| Text on canvas | `#FFFDF8` | `#0C2F24` |
| Accent | `#D4AF37` gold | `#D4AF37` gold |
| Danger | `#E5645A` | `#C0392B` |
| Success | `#3FBF84` | `#2E8B57` |
| Canvas edge | 1dp gold at 25% | 1dp emerald at 15% |

- Widget follows the system theme (dark or light). Both are defined; neither relies on wallpaper.
- Tiles never use the canvas colour. Canvas never uses the tile colour.
- Contrast: all text on tiles at least 4.5:1. Gold is used for numbers, icons and the primary action only.

### 2.2 Shape and spacing

| Element | Spec |
|---|---|
| Canvas corner | 28dp (system radius on Android 12+) |
| Tile corner | 18dp |
| Canvas padding | 10dp |
| Tile gap | 8dp |
| Tile inner padding | 12dp |
| Action button | 44dp minimum touch target, pill or circle |
| Type | Hero number 28sp bold, tile label 11sp caps muted, body 13sp |

### 2.3 Rules
- One hero number per widget.
- Labels are one or two words. No sentences, no emojis.
- A single gold element per tile at most.
- Red is used only for overdue, over-budget and unlogged gaps.

---

## 3. Widget family

One widget, three responsive layouts, plus two small single-purpose widgets. Glance `SizeMode.Responsive` selects the layout from the actual size.

| Widget | Size | Purpose |
|---|---|---|
| **LedgerAI Home** | 2×2, 4×2, 4×4 (responsive) | Everything at a glance and quick actions |
| **Quick Actions** | 4×1 | Row of action buttons only |
| **Focus** | 2×2 | Next session countdown and Start |

Existing receivers are removed and replaced. Installed widgets migrate automatically to the closest new layout.

---

## 4. LedgerAI Home layouts

### 4.1 Small (2×2)

```
┌──────────────────────┐
│ Safe today           │
│ $42                  │  gold hero
│ ▓▓▓▓▓░░░  $18 spent  │
│ Next: Gym 6:00 PM    │
│ (mic) (+) (timer)    │  3 actions
└──────────────────────┘
```
- Hero: safe-to-spend today.
- One line of next item.
- Three circular actions: Voice, Add spend, Focus.

### 4.2 Medium (4×2)

```
┌──────────────────────────────────────────┐
│ Tue 8 Oct              (mic)(+)(task)(⏱) │
├───────────────┬──────────────────────────┤
│ Safe today    │ Next up                  │
│ $42           │ 14:00  Algorithms        │
│ ▓▓▓▓░░ $18    │ 16:30  Study: Graphs     │
│               │ 18:00  Gym               │
├───────────────┴──────────────────────────┤
│ 2 tasks due   1 bill   1 gap             │
└──────────────────────────────────────────┘
```
- Header row: date and four actions.
- Left tile: spend guide with progress.
- Right tile: next three items from calendar, classes, tasks, plan blocks.
- Footer chips: counts that deep-link to the matching screen. Gap chip is red.

### 4.3 Large (4×4)

```
┌──────────────────────────────────────────┐
│ Tue 8 Oct   Quote line      (mic)(+)(task)│
├───────────────┬──────────────────────────┤
│ Safe today    │ Now / Next               │
│ $42           │ ▶ Study: Graphs  0:24    │
│ ▓▓▓▓░░ $18    │   18:00 Gym              │
│ Month  72%    │   20:00 Pay rent         │
├───────────────┼──────────────────────────┤
│ Tasks         │ Habits today             │
│ ☐ Lab report  │ ● Read  ● Gym  ○ Prayer  │
│ ☐ Email prof  │ Streak 6                 │
├───────────────┴──────────────────────────┤
│ Jobs 3 this week   Log: 1 gap   Bill 2d  │
└──────────────────────────────────────────┘
```
- Adds checkable tasks, habit dots, and a status strip for jobs, log gaps and bills.
- Tasks can be completed directly from the widget.

### 4.4 Priority when space is short
Order of what survives as the widget shrinks: Safe today → Next up → Actions → Tasks → Habits → Status strip → Quote.

---

## 5. Content and data

| Tile | Source | Refresh |
|---|---|---|
| Safe today | Spend guide (F5) | On new transaction, midnight, hourly |
| Month progress | Budgets | On transaction |
| Next up | Calendar events, class occurrences, tasks with time, plan blocks, alarms | Every 15 min, on change, on alarm |
| Tasks | Open tasks due today, overdue first | On change |
| Habits | Today's habit sessions and streak | On change |
| Status strip | Bills due, job follow-ups, Log gaps | Hourly |
| Quote | Existing quote repository | Daily |
| Now / Next with timer | Active focus session | Per second while running via ongoing notification, widget updates on minute boundaries |

- Data is read from Room only; no network from the widget.
- A single `WidgetStateBuilder` produces one lightweight payload so the three layouts stay consistent.
- Updates are pushed by repositories on write (`updateAll`) and by a periodic WorkManager job as a safety net. `updatePeriodMillis` is set to 0.

---

## 6. Actions

All actions start in the least intrusive way. None opens the full app unless needed.

| Action | Behaviour |
|---|---|
| Mic | Opens transparent capture overlay (existing `WidgetVoiceCaptureActivity`), shows confirm card, saves |
| Add spend | Transparent quick-add sheet: amount, category chips, Save |
| Add task | Transparent quick-add: title, due chips (Today, Tomorrow), Save |
| Focus | Starts the next plan block or a default 25 min session |
| Complete task | Checkbox tap marks done via a background action, with instant refresh |
| Habit dot | Tap marks done; long-press opens skip/snooze |
| Next-up row | Deep link to the item |
| Status chips | Deep link to Bills, Jobs, Log gap list |
| Hero number | Opens Money → Today |
| Date header | Opens Calendar |

- Quick-add sheets are small translucent activities styled as `LSheet`, so the user stays on the home screen.
- Every background action writes through the existing repositories and then refreshes the widget.

---

## 7. Quick Actions widget (4×1)

Row of up to five buttons. User picks which in widget settings.

| Available actions |
|---|
| Voice, Spend, Task, Note, Focus, Alarm, Job, Check-in, Ask AI |

- Tiles are emerald on the canvas, gold icons, no text under icons when width is below 4 cells.
- Check-in button shows a red dot when a gap exists.

---

## 8. Focus widget (2×2)

```
┌────────────────┐
│ Study: Graphs  │
│ 24:10          │  gold, large
│ ▓▓▓▓▓░░░░      │
│ (pause)  (end) │
└────────────────┘
```
- Idle state: next plan block and a gold Start button.
- Running state: remaining time, progress bar, Pause and End.
- Finished state: "Done" with Add next.

---

## 9. States

| State | Display |
|---|---|
| Signed out or first run | One tile: app logo and Open |
| Nothing scheduled | Next up shows "Clear day" |
| Over guide | Hero turns danger red, progress bar full red |
| Near guide (above 80%) | Hero stays gold, progress bar amber |
| Data stale (over 6 h) | Small muted timestamp in header |
| Permission missing (notifications, exact alarm) | Status chip "Fix" opening settings |
| Private mode (setting) | Amounts shown as `•••`, event titles hidden until tapped |

---

## 10. Configuration

A single configuration screen opened on placement and from long-press → Reconfigure.

| Option | Choices |
|---|---|
| Theme | System, Dark, Light |
| Transparency of canvas | Solid, 85%, 70% (tiles stay solid) |
| Show | Spend, Next up, Tasks, Habits, Status strip, Quote (toggles) |
| Actions | Choose and order up to four |
| Private mode | On or Off |
| Next-up sources | Calendar, Classes, Tasks, Plan |

Defaults: System theme, solid canvas, all sections on.

---

## 11. Implementation outline

| Area | Plan |
|---|---|
| Framework | Glance 1.1+, `SizeMode.Responsive` with three size breakpoints |
| State | `WidgetStateBuilder` (domain) → serialisable `WidgetPayload` stored in Glance preferences state |
| Theme | One `WidgetColors` object with dark and light sets resolved via `ColorProvider(day, night)` |
| Background | Replace `widget_bg.xml` with canvas drawable and a separate tile drawable; remove reuse of one colour for both |
| Actions | `actionStartActivity` for overlays and deep links, `actionRunCallback` for task and habit toggles |
| Updates | Repository write hooks, WorkManager periodic fallback, midnight and timezone receivers |
| Files | Remove `CalendarEventsWidget`, `DayScheduleWidget`, `VoiceTransactionWidget`; add `HomeWidget`, `QuickActionsWidget`, `FocusWidget`, shared `WidgetTheme`, `WidgetActions`, `WidgetConfigActivity` |
| Manifest | Three receivers, one config activity, overlay activities with translucent theme |
| Previews | Provide `previewLayout` and Android 15 `generatedPreviews` for the picker |

---

## 12. Build steps

| Step | Deliverable |
|---|---|
| W1 | `WidgetTheme` palette, canvas and tile drawables, shared primitives (tile, chip, round action) |
| W2 | `WidgetStateBuilder` and payload from current data (tasks, calendar, classes, alarms, budget, quote) |
| W3 | Home widget small, medium and large layouts |
| W4 | Actions: voice, quick-add spend, quick-add task, task complete, deep links |
| W5 | Configuration screen, private mode, transparency |
| W6 | Quick Actions widget |
| W7 | Spend guide, habits, plan blocks and status strip once those features land |
| W8 | Focus widget tied to the focus service |
| W9 | Remove old widgets, migration, picker previews, QA on Pixel and Samsung launchers, light and dark, small and large font scales |

W1 to W6 do not depend on the new features. W7 and W8 follow phases 11 to 15 in [plan-new-features.md](plan-new-features.md).

---

## 13. QA checklist

- Canvas and tile visibly distinct on light, dark and busy wallpapers.
- Text contrast at 4.5:1 or better on every tile.
- Layout holds at font scale 1.3 and in 2×2 minimum size.
- Actions work with the app closed and the device locked down to home screen.
- Widget refreshes within 2 seconds of a change made in the app.
- Private mode hides all amounts and titles.
- Battery: no more than one scheduled refresh every 15 minutes outside active focus.
