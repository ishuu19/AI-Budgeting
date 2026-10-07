# LedgerAI — Product & Engineering Plan

Status: **Phase 1 in progress** (foundation & cleanup applied). Next: Phase 2 (Supabase + auth + Room).

---

## 1. App requirements (from owner)

### 1.1 Finance by voice
- Speak a transaction ("spent 50 on groceries") → parsed → confirm card → saved to the user's database.
- Budgets per category with alerts, debts (owed to/by), savings goals, recurring bills.

### 1.2 Tasks, routines & reminders by voice
- Speak to create tasks, routines and reminders.
- **Custom reminders: maximum 10 per task** (e.g. 1 week / 1 day / 30 min before, or any custom time).

### 1.3 Voice alarms
- Create alarms by voice.
- Several built-in tones.
- **Custom sounds:** user uploads/picks an audio file; it is copied into app-private storage so it stays downloaded and works offline.

### 1.4 Notes
- A place to store text notes, with AI integration (summarize, tag, ask about a note).

### 1.5 Dashboard
- Very classic dashboard for all insights: KPI cards, spending by category, income vs expense trend, budget usage, upcoming tasks/bills, AI insight card.
- AI responds to problems it detects (overspending, bills due, missed budget, etc.).

### 1.6 AI (Gemini) — strict data rules
- AI may read **only the signed-in user's data**, never anyone else's.
- AI has **no write access** to the database. It only returns data in a fixed format.
- Prefer low token usage.

### 1.7 Quotes
- A few quotes every day.
- Home-screen widget: quote + voice (read aloud), or voice only.

### 1.8 Auth
- Remove the current Google login code.
- Use Supabase Auth (Google provider) or an equivalent service.

### 1.9 Voice recorder
- Use a good free, downloadable recorder/transcriber inside the app.

---

## 2. Audit — what is wrong today

### Critical (privacy / security) — addressed in Phase 1
1. ~~**App talks straight to MongoDB.**~~ Removed `MongoProvider`, driver, and `MONGODB_URI` BuildConfig. Temporary **in-memory** store until Room (Phase 2).
2. ~~**OpenRouter API key ships in the APK**~~ Removed OpenRouter/Whisper clients and API-key BuildConfig fields.
3. ~~**Login is cosmetic / play-services-auth.**~~ Removed. Temporary **Continue locally** session until Supabase (Phase 2).
4. ~~**Whisper via OpenRouter.**~~ Removed. Voice mic still records; transcript path waits for Vosk (Phase 4); text entry still parses locally.

### Functional (partially fixed / deferred)
5. ~~One-shot `flow { emit(...) }`~~ In-memory `StateFlow` updates reactively. Room Flows in Phase 2.
6. ~~`mongoId.hashCode()` ids~~ Local auto-increment `Long` ids; `remoteId` reserved for Supabase.
7. Manifest: added FGS mic permissions; removed calendar + `REQUEST_INSTALL_PACKAGES`; `BootReceiver` not exported; `allowBackup="false"`.
8. `DatabaseSeeder` still seeds demo data on empty store (IO coroutine) — fine for in-memory; revisit when Room lands.
9. Naming unified to **LedgerAI**; README rewritten; stale Mongo catalog entry removed; AI no longer uses free-text `extractJson` against OpenRouter.
10. ~~Git repo root was `C:\`~~ Project has its own `.git` with baseline commit.

### Still open
- Rotate previously embedded Mongo/OpenRouter credentials (owner action).
- Supabase project, RLS, Room, Credential Manager auth (Phase 2).
- Vosk (Phase 4), Gemini Edge Function (Phase 7).

---

## 3. Target architecture

| Concern | Choice |
|---|---|
| Client | Native Android — Kotlin, Compose, Material 3, Hilt, Glance, WorkManager (keep existing stack) |
| Local store | Room (offline-first source of truth for the UI) |
| Backend | Supabase: Postgres + Row Level Security, Auth, Edge Functions, Storage (optional, for note attachments) |
| Auth | Credential Manager → Google ID token → Supabase `signInWithIdToken`. Remove `play-services-auth` |
| AI | Gemini via Supabase Edge Function. Key stored as a Supabase secret, never in the APK |
| Voice | Android recorder + Vosk offline transcription (default); optional Gemini-audio mode |
| Alarms | `AlarmManager.setAlarmClock` + foreground ringing service + full-screen intent |

### 3.1 Data model (every table has `user_id uuid not null default auth.uid()`)
`transactions`, `budgets`, `debts`, `goals`, `bills`, `tasks`, `reminders` (task_id, offset, max 10 per task enforced by constraint/trigger), `routines`, `alarms`, `notes`, `quotes_seen` (optional).

RLS on all tables: `using (user_id = auth.uid()) with check (user_id = auth.uid())`.
Sync: Room ↔ Supabase using `updated_at` + soft-delete (`deleted_at`), last-write-wins per row.

### 3.2 AI privacy design (3 layers)
1. **On-device context builder** — reads only the signed-in user's Room data, aggregates into a compact summary (~500–1500 tokens): monthly totals by category, top merchants, budget usage, upcoming tasks/bills, a few recent items. Raw rows and notes are not sent unless the user explicitly asks about a specific note.
2. **Edge Function proxy** — verifies the user JWT, rate-limits per user, holds the Gemini key, forces JSON via Gemini `responseSchema`. It has **no database credentials**, so it cannot read or write the DB.
3. **On-device validator/applier** — validates the response against a fixed schema (`transaction`, `task`, `alarm`, `insight`, `note_summary`, `chat`). Writes happen only in app code, after a user confirm card.

Models: Gemini Flash-Lite for voice parsing; Flash for insights/chat. Use context caching and cache daily insights to save tokens.

### 3.3 Voice
- Record with Android `MediaRecorder`/`AudioRecord` (16 kHz mono).
- Transcribe with **Vosk** (Apache-2.0, offline, ~40–50 MB model downloaded once on first use and kept locally). Alternative: whisper.cpp (more accurate, needs NDK).
- Optional setting: send audio to Gemini for transcribe+parse in one call.
- Intent router: one parse call returns `transaction | task | reminder | alarm | note` in the fixed schema.

### 3.4 Alarms
- Stored in Room; re-armed on boot and app update (`BootReceiver`, not exported).
- Needs `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.
- Tones: bundled in `res/raw` + custom files picked via the system file picker (SAF) and copied to `filesDir/tones/`. Validate type and size (e.g. ≤ 10 MB, mp3/ogg/wav/m4a).
- Snooze, dismiss, volume ramp, vibrate.

### 3.5 Quotes & widget
- ~300 bundled quotes (offline). Optional daily Gemini-generated batch, cached.
- WorkManager picks N quotes/day and posts them.
- Glance widget: quote + speaker button (Android TextToSpeech) and mic button; "voice only" mode.

### 3.6 Dashboard
KPI row (income, expense, net, savings rate) · spending by category · income vs expense trend · budget bars · upcoming tasks/bills/alarms · AI insight card (one tap → chat with that insight as context).

---

## 4. Build phases

### Phase 1 — Foundation & cleanup ✅
- [x] `git init` in the project; commit baseline.
- [x] First Gradle build: `./gradlew assembleDebug` → **BUILD SUCCESSFUL**.
- [x] Remove MongoDB driver/`MongoProvider`, OpenRouter/Whisper services, `play-services-auth` Google login.
- [x] Fix manifest (permissions, exported flags, backup rules).
- [x] Unify naming (LedgerAI), clean `libs.versions.toml`, rewrite README.
- [x] Move secrets handling: only public Supabase URL + anon key in BuildConfig.
- [x] Remove `CalendarService` + calendar permissions (per decision §5.3).
- Ruling: in-memory store + local session + local AI stubs keep the app runnable until Phases 2/4/7.

### Phase 2 — Supabase + auth + data layer
- Supabase project, schema, RLS policies, tests that user A cannot read user B.
- Google sign-in via Credential Manager → Supabase.
- Room entities/DAOs with Flow, sync worker, migrations.

### Phase 3 — Finance core
- Transactions, budgets, debts, goals, bills on the new layer (reactive Flows, SQL aggregates, proper ids).
- Budget-alert worker.

### Phase 4 — Voice
- Recorder screen/widget, Vosk model download + storage, permission flow.
- Parse → confirm card → save for finance first.

### Phase 5 — Tasks, routines, reminders
- Voice/manual tasks, routines (repeat rules), up to 10 custom reminders each, notifications via WorkManager/AlarmManager.

### Phase 6 — Alarms
- Voice alarm creation, ringing service/screen, built-in tones, custom upload, boot re-arm.

### Phase 7 — AI layer
- Context builder, Edge Function, response schemas, validator, dashboard insights, chat, note AI actions.

### Phase 8 — Notes, quotes, widget
- Notes CRUD + AI actions; quote engine; Glance widget with voice.

### Phase 9 — Polish & release
- Unit tests (context builder, validators, parsers), instrumented tests (alarms, Room), RLS tests.
- ProGuard/R8 check, accessibility, empty/error states, privacy policy text.

---

## 5. Decisions (confirmed by owner)
1. **Reminders:** maximum 10 custom reminders per task (enforced in DB constraint/trigger and in the UI).
2. **Stack:** owner delegated the choice → **Supabase + native Android (Kotlin/Compose)**, as described in section 3.
3. **Google Calendar sync:** **dropped**. In-app reminders replace it; remove `CalendarService` and calendar permissions.
4. **Transcription:** use a free option if it is the best → **Vosk** (free, Apache-2.0, offline) as the default. whisper.cpp / Gemini-audio remain optional upgrades, not in v1.
5. **AI keys:** do **not** paste into chat/repo or APK BuildConfig. Hold in `secrets.properties` / Edge Function secrets.
6. **AI provider cascade (confirmed):**  
   1) OpenRouter **free** models (`OPENROUTER_FREE_API_KEY` → else `OPENROUTER_API_KEY` + `AI_MODEL_OPENROUTER_FREE`)  
   2) Gemini (`GEMINI_API_KEY` then `GEMINI_API_KEYS`)  
   3) DeepSeek official (`DEEPSEEK_API_KEY`, `AI_MODEL_DEEPSEEK`)  
   4) OpenRouter paid/extra (`OPENROUTER_API_KEY` then `OPENROUTER_API_KEYS` + `AI_MODEL_OPENROUTER`)  
   Preferred model family: **DeepSeek** (free via OpenRouter `:free` first, then native DeepSeek API, then OpenRouter paid DeepSeek).

## 6. Security notes
- `secrets.properties` is git-ignored. Values previously compiled into APKs (Mongo URI, OpenRouter key) must be **rotated** by the owner.
- Never put the Supabase `service_role` key in the app; only the anon key + user JWT.
- Turn off cloud backup for app data (or exclude the Room DB and tones) — `allowBackup="false"` now.
