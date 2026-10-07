# LedgerAI — Product & Engineering Plan

Status: planning only. No code has been changed yet. Findings below come from reading the code; the project has not been compiled.

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

### Critical (privacy / security)
1. **App talks straight to MongoDB.** `MONGODB_URI` is compiled into the APK via `BuildConfig` (`app/build.gradle.kts`, `MongoProvider.kt`) — anyone can extract DB credentials. All queries are `col.find()` with no user filter and no document has a `userId` (`TransactionRepository.kt`, `OtherRepositories.kt`), so every user would see every user's data.
2. **OpenRouter API key ships in the APK** (`AppModule.kt`) and is also reused for the Whisper client.
3. **Login is cosmetic.** Google Sign-In only stores the account id in local DataStore (`AuthViewModel.kt`, `UserSession.kt`). Nothing server-side verifies it and the data layer never uses it. Uses the deprecated `play-services-auth` API.
4. **Transcription likely broken.** Whisper is pointed at OpenRouter `/audio/transcriptions`, which probably does not exist (to verify).

### Functional bugs
5. Repositories use one-shot `flow { emit(...) }` — UI will not refresh after inserts. Totals are computed by downloading all documents; dates filtered by regex on strings.
6. Entity ids are `mongoId.hashCode().toLong()` — collisions possible.
7. Manifest:
   - Mic foreground service lacks `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE` permissions (required at targetSdk 34) → crash.
   - `REQUEST_INSTALL_PACKAGES` is unnecessary (Play policy flag).
   - `BootReceiver` is `exported="true"` without need.
   - `allowBackup="true"` lets financial data enter cloud backups.
8. `DatabaseSeeder.seedIfEmpty()` is called in `Application.onCreate` against a network DB — check threading / ANR risk.
9. Housekeeping: README says Room but code uses Mongo; app is named both BudgetAI and LedgerAI; `libs.versions.toml` has a stale Mongo entry vs hardcoded version in gradle; AI JSON is extracted from free text with `extractJson` instead of a response schema.
10. Git repo root is `C:\`; the project's files are not tracked. Run `git init` in the project folder.

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

### Phase 1 — Foundation & cleanup
- `git init` in the project; commit baseline.
- First Gradle build; record compile errors.
- Remove MongoDB driver/`MongoProvider`, OpenRouter/Whisper services, `play-services-auth` Google login.
- Fix manifest (permissions, exported flags, backup rules).
- Unify naming (LedgerAI), clean `libs.versions.toml`, rewrite README.
- Move secrets handling: no API keys in `BuildConfig` except the public Supabase URL + anon key.

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
5. **Gemini API key:** do **not** paste into chat/repo; it will be set as a Supabase secret.

## 6. Security notes
- `secrets.properties` is git-ignored, but the old MongoDB/OpenRouter values have been compiled into builds — **rotate them** once they are no longer used.
- Never put the Supabase `service_role` key in the app; only the anon key + user JWT.
- Turn off cloud backup for app data (or exclude the Room DB and tones).
