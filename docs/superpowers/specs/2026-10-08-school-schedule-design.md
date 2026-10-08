# School schedule + finance (LedgerAI)

## Product

One app: **personal finance** (existing) + **school schedules** (courses, weekly timetable, exams, calendar).

## Data (Room v4, local-first)

| Table | Purpose |
|-------|---------|
| `courses` | Code, name, default location, color token |
| `schedule_slots` | Weekly class blocks: `routineId`, optional `courseId`, title, `dayOfWeek` (1=Mon…7=Sun), start/end `LocalTime`, location |
| `routine_slot_reminders` | Up to **3** per slot: label, `remindAt`, optional offset, enabled |
| `calendar_events` | Exams and personal events: title, `courseId?`, `taskId?`, start/end `LocalDateTime`, kind (`EXAM`, `CLASS`, `PERSONAL`) |
| `tasks` | Add `courseId` (nullable), `eventKind` (`TASK`, `EXAM`, `EVENT`) |

Routines UI title: **Daily Routine** (route stays `routines`).

## Notifications

- Task reminders: existing WorkManager path; **1s vibration** on notify.
- Routine slot reminders: new worker + scheduler; same vibration pattern.
- Channels: `task_reminders`, `routine_reminders`.

## Import

- **CSV**: columns `Day,Start,End,Title,Course,Location` (flexible header). Creates slots on active routine.
- **Image**: pick image → ML Kit text recognition → same line parser as CSV text fallback.
- **Paste**: multiline text in sheet.

## Screens (Life hub)

Tasks, **Daily Routine**, **Courses**, **Calendar**, Notes, Alarms.

## Widget

Glance **Calendar** widget: next 3 days of events + exams (read from Room).

## Out of scope (v1)

- Supabase sync for new tables (local only until migration SQL added).
- Google Calendar export.

## Agent ownership

1. Foundation — Room, repos, parsers, reminder workers, vibration.
2. Courses screen — CRUD `CoursesScreen.kt`.
3. Daily Routine — rename, grid, CSV/image import, 3 reminders per slot.
4. Tasks — exam/event kind, course picker, calendar feed on save.
5. Calendar — month view, `CalendarScreen.kt`.
6. Calendar widget — Glance + manifest.
7. Navigation — Life hub tiles + routes.
