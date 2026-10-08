# LedgerAI Full Build Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a working LedgerAI Android app covering finance, voice/text entry, tasks/reminders, alarms, notes, quotes widget, dashboard UX, and AI cascade — offline-first Room locally; Supabase schema prepared; AI keys never in release APK.

**Architecture:** Room as UI source of truth; repositories emit Flows; AI cascade OpenRouter-free → Gemini → DeepSeek → OpenRouter-paid via a dedicated router (keys from secrets at build for debug only, empty in release); presentation redesigned with Clarity/Tableau/Maze principles.

**Tech Stack:** Kotlin, Compose, Material 3, Hilt, Room, WorkManager, Glance, Retrofit/OkHttp, Supabase (schema SQL + public URL/anon only in BuildConfig).

**Spec:** `PLAN.md` at repo root.

**Wave 1 status (2026-10-08):** Landed — Room, dashboard/login UX, AI cascade + Edge path, Tasks/Notes/Alarms/Quotes, Credential Manager → Supabase Auth, sync worker, alarm ring UI, Vosk STT. Follow-ups: JWT refresh persistence, device QA, live RLS deploy.

## Global Constraints

- Package: `com.ledgerai.app`; brand name **LedgerAI** only (never BudgetAI).
- Do **not** put `SUPABASE_SECRET_KEY`, `OPENROUTER_*`, `GEMINI_*`, `DEEPSEEK_*` into release BuildConfig. Debug may inject from `secrets.properties` into BuildConfig for local AI; release must clear those fields. Production target: Edge Function secrets.
- `allowBackup=false`; BootReceiver `exported=false`.
- Max **10** reminders per task.
- No Google Calendar; no Mongo; no play-services-auth Google Sign-In (Credential Manager later).
- Do **not** commit secrets.properties. Do **not** git commit unless asked.
- Preserve existing screen navigation routes where possible; extend NavHost for new destinations.
- Match existing code style (Hilt `@Inject`, StateFlow UI state).

## UX constraints (Clarity · Tableau · Maze)

- Dashboard: F-pattern — brand/title upper-left, **one primary KPI** as big number early, sparse contrast, subtitle takeaway, then supporting charts, then upcoming list. One primary CTA (e.g. Add / Voice).
- Login: one primary CTA, no competing actions; clear success path (“Continue” → home).
- No rage-click traps: decorative surfaces must not look like buttons.
- Each screen: one job, one headline, one short supporting line.
- Avoid purple-on-white AI cliché, cream+terracotta, broadsheet dense columns; keep existing Material 3 brand colors unless improving hierarchy.

## Wave 1 (parallel — non-overlapping paths)

### Task A — Room data layer
- [x] Add Room deps to `libs.versions.toml` + `app/build.gradle.kts`
- [x] Entities/DAOs for: transactions, budgets, debts, goals, bills (+ soft sync fields `updatedAt`, `deletedAt`, `remoteId`, `userId` nullable for now)
- [x] `LedgerDatabase` + Hilt module
- [x] Replace `InMemoryStore` usage in repositories with Room DAOs + Flow
- [x] Keep `DatabaseSeeder` working against Room
- [x] SQL file `supabase/migrations/001_init.sql` with RLS stubs (for later deploy)

### Task B — Dashboard & auth UX polish
- [x] Redesign `DashboardScreen` for Tableau scan path + Clarity CTA placement
- [x] Polish `LoginScreen` (one CTA, clearer hierarchy) — local “Continue” + Google via Credential Manager when secrets set
- [x] Light theme token cleanup if needed; keep `LedgerAITheme`
- [x] Do **not** change data layer files (constraint followed for this task)

### Task C — AI cascade
- [x] `data/ai/AiProviderRouter.kt` implementing cascade order from PLAN §5.6
- [x] Models/schemas for chat + parse JSON
- [x] Rewrite `AiRepository` to use router; keep local health-score fallback
- [x] Wire Retrofit clients; keys: `AiConfig` from BuildConfig; debug may populate from `secrets.properties`; release fields empty; Edge Function is production path
- [x] Do **not** redesign Compose screens except fixing compile breaks in AI screen

### Task D — Tasks, Notes, Alarms, Quotes shells
- [x] Domain models + Room entities/DAOs for Tasks/Reminders/Alarms/Notes (feature `*LocalStore`s removed; finance already on Room)
- [x] Screens: Tasks, Notes, Alarms list + basic CRUD
- [x] Nav routes + drawer/bottom nav entries
- [x] Quotes: bundled JSON asset + `QuoteDailyWorker` + Glance widget quote + TTS button
- [x] Reminders: enforce max 10 in UI + repository
- [x] SQL file `supabase/migrations/002_tasks_reminders_alarms_notes.sql` (RLS stubs; for later deploy)

### Wave 1 former stubs (completed)
- [x] Supabase Auth (Credential Manager → Google ID token → Supabase; local “Continue” fallback)
- [x] Sync worker / remote sync (Room ↔ Postgrest; soft deletes + cursors)
- [x] Alarm ring UI (foreground service + full-screen intent)
- [x] Vosk offline transcription (model download + file STT; sibling `.txt` stub for tests only)
- [x] JWT refresh: persist `refreshToken` in `UserSession`; `AuthRepository.refreshIfNeeded()` via `SessionGuard` before sync / Edge AI

## Wave 2 (after Wave 1 integrate)

- [x] Wire finance screens to Room aggregates
- [x] Voice: text parse + Vosk model download / transcribe UI
- [x] Budget alert worker against Room
- [x] Empty/error states

## Review Focus

1. Keys leaking into release APK
2. Navigation regressions
3. Dashboard hierarchy / competing CTAs
4. Reminder max-10 enforcement
5. Room Flow reactivity after insert
6. JWT refresh before sync / Edge AI (refresh token persisted)
