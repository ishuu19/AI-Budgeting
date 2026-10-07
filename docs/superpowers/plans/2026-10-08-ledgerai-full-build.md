# LedgerAI Full Build Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a working LedgerAI Android app covering finance, voice/text entry, tasks/reminders, alarms, notes, quotes widget, dashboard UX, and AI cascade — offline-first Room locally; Supabase schema prepared; AI keys never in release APK.

**Architecture:** Room as UI source of truth; repositories emit Flows; AI cascade OpenRouter-free → Gemini → DeepSeek → OpenRouter-paid via a dedicated router (keys from secrets at build for debug only, empty in release); presentation redesigned with Clarity/Tableau/Maze principles.

**Tech Stack:** Kotlin, Compose, Material 3, Hilt, Room, WorkManager, Glance, Retrofit/OkHttp, Supabase (schema SQL + public URL/anon only in BuildConfig).

**Spec:** `PLAN.md` at repo root.

## Global Constraints

- Package: `com.ledgerai.app`; brand name **LedgerAI** only (never BudgetAI).
- Do **not** put `SUPABASE_SECRET_KEY`, `OPENROUTER_*`, `GEMINI_*`, `DEEPSEEK_*` into release BuildConfig. Debug may inject empty-safe optional fields only if needed for local AI; prefer reading via a sealed `AiSecrets` loaded only in debug.
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
- [ ] Add Room deps to `libs.versions.toml` + `app/build.gradle.kts`
- [ ] Entities/DAOs for: transactions, budgets, debts, goals, bills (+ soft sync fields `updatedAt`, `deletedAt`, `remoteId`, `userId` nullable for now)
- [ ] `LedgerDatabase` + Hilt module
- [ ] Replace `InMemoryStore` usage in repositories with Room DAOs + Flow
- [ ] Keep `DatabaseSeeder` working against Room
- [ ] SQL file `supabase/migrations/001_init.sql` with RLS stubs (for later deploy)

### Task B — Dashboard & auth UX polish
- [ ] Redesign `DashboardScreen` for Tableau scan path + Clarity CTA placement
- [ ] Polish `LoginScreen` (one CTA, clearer hierarchy)
- [ ] Light theme token cleanup if needed; keep `LedgerAITheme`
- [ ] Do **not** change data layer files

### Task C — AI cascade
- [ ] `data/ai/AiProviderRouter.kt` implementing cascade order from PLAN §5.6
- [ ] Models/schemas for chat + parse JSON
- [ ] Rewrite `AiRepository` to use router; keep local health-score fallback
- [ ] Wire Retrofit clients; keys: create `AiConfig` reading from BuildConfig fields that are **blank by default**; document that Edge Function is production path. For debug builds only, optionally populate from secrets via gradle `buildTypes.debug` — **never release**.
- [ ] Do **not** redesign Compose screens except fixing compile breaks in AI screen

### Task D — Tasks, Notes, Alarms, Quotes shells
- [ ] Domain models + in-memory or Room DAOs if Task A not merged yet — prefer Room entities in `domain` + repositories that compile; if Room DB not ready, use temporary stores under `data/local` named clearly
- [ ] Screens: Tasks, Notes, Alarms list + basic CRUD
- [ ] Nav routes + drawer/bottom nav entries
- [ ] Quotes: bundled JSON asset (~50 quotes ok for v1) + WorkManager stub + Glance widget update for quote+TTS button
- [ ] Reminders: enforce max 10 in UI + repository

## Wave 2 (after Wave 1 integrate)

- Wire finance screens to Room aggregates
- Voice: keep text parse; soft Vosk placeholder download UI
- Budget alert worker against Room
- Empty/error states

## Review Focus

1. Keys leaking into release APK
2. Navigation regressions
3. Dashboard hierarchy / competing CTAs
4. Reminder max-10 enforcement
5. Room Flow reactivity after insert
