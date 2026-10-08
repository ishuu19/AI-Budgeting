# LedgerAI — Product & Engineering Plan

Status: **Phases 1–9 implemented in code.** Wire Supabase project secrets, deploy Edge Function, and run build/QA before release.

---

## 1. App requirements (from owner)

### 1.1 Finance by voice
- Speak a transaction → parsed → confirm card → saved.
- Budgets per category with alerts, debts, savings goals, recurring bills.

### 1.2 Tasks, routines & reminders by voice
- Speak to create tasks, routines and reminders.
- **Custom reminders: maximum 10 per task.**

### 1.3 Voice alarms
- Create alarms by voice; built-in tones; custom sounds copied to app-private storage.

### 1.4 Notes
- Text notes with AI (summarize, tag, ask).

### 1.5 Dashboard
- KPIs, spending by category, income vs expense trend, budget usage, upcoming tasks/bills/alarms, AI insight card.

### 1.6 AI — strict data rules
- AI may read only the signed-in user’s data.
- AI has no write access to the database; fixed response formats.
- Prefer low token usage.

### 1.7 Quotes
- Daily quotes; Glance widget with speak / mic / voice-only mode.

### 1.8 Auth
- Supabase Auth (Google via Credential Manager); local Continue as offline fallback.

### 1.9 Voice recorder
- On-device recorder + Vosk offline transcription.

---

## 2. Implementation status (code)

| Item | Status |
|---|---|
| Mongo / OpenRouter-in-APK / play-services-auth removed | Done |
| Room finance + tasks/reminders/routines/alarms/notes | Done |
| Credential Manager → Supabase Auth | Done (needs secrets) |
| Sync worker Room ↔ Supabase | Done (needs project) |
| Vosk STT + multi-intent voice confirm | Done |
| Routines UI + task reminder notifications | Done |
| Alarm ring / tones / ramp / repeat / boot | Done |
| Bill / debt / budget workers | Done |
| Edge `ai-proxy` + client validator + ContextBuilder | Done |
| Note AI + daily insights | Done |
| Dashboard §3.6 + ~300 quotes + voice-only widget | Done |
| Finance edit UIs | Done |
| Unit test stubs, ProGuard `com.ledgerai`, privacy text | Done |
| Owner: rotate old leaked credentials | Owner action |
| Live RLS deploy + device QA | Ops / QA |

---

## 3. Target architecture

| Concern | Choice |
|---|---|
| Client | Native Android — Kotlin, Compose, Material 3, Hilt, Glance, WorkManager |
| Local store | Room (offline-first) |
| Backend | Supabase: Postgres + RLS, Auth, Edge Functions |
| Auth | Credential Manager → Google ID token → Supabase `signInWithIdToken` |
| AI | Edge Function holds keys; debug cascade optional |
| Voice | Android recorder + Vosk |
| Alarms | `AlarmManager.setAlarmClock` + FGS ringing + full-screen intent |

### 3.1 Data model
`transactions`, `budgets`, `debts`, `goals`, `bills`, `tasks`, `reminders` (max 10/task), `routines`, `alarms`, `notes`, `quotes_seen`.

RLS: `user_id = auth.uid()`. Sync: `updated_at` + `deleted_at`, last-write-wins.

### 3.2 AI privacy (3 layers)
1. On-device ContextBuilder from Room.
2. Edge Function: JWT, rate-limit, keys, `responseSchema` — no DB credentials.
3. On-device validator; writes only after user confirm.

### 3.3–3.6
Voice intent router; alarm tones/custom/boot; ~300 quotes + widget modes; dashboard KPI/charts/upcoming/insight.

---

## 4. Build phases

### Phase 1 — Foundation & cleanup ✅
- [x] Project git, assembleDebug baseline, remove Mongo/OpenRouter/Whisper/play-services-auth, manifest, naming, secrets handling, calendar removed.

### Phase 2 — Supabase + auth + data layer ✅
- [x] Schema/RLS SQL migrations (incl. tasks/notes/alarms + quotes_seen).
- [x] Credential Manager → Supabase.
- [x] Room for all core domains + sync worker.

### Phase 3 — Finance core ✅
- [x] Transactions, budgets, debts, goals, bills on Room + edit UIs.
- [x] Budget / debt / bill reminder workers.

### Phase 4 — Voice ✅
- [x] Recorder + Vosk model download/transcribe.
- [x] Multi-intent parse → confirm → save.

### Phase 5 — Tasks, routines, reminders ✅
- [x] Manual + voice tasks; routines screen; max 10 reminders; notification scheduling + boot reschedule.

### Phase 6 — Alarms ✅
- [x] Voice/manual alarms; ringing service/UI; tones; custom upload; ramp; repeat days; boot re-arm.

### Phase 7 — AI layer ✅
- [x] ContextBuilder, Edge Function, validators, insights worker, chat, note AI.
- [x] Debug on-device cascade retained as fallback.

### Phase 8 — Notes, quotes, widget ✅
- [x] Notes CRUD + AI; ~300 quotes; Glance speak/mic/voice-only.

### Phase 9 — Polish & release ✅ (code)
- [x] Unit test stubs (context builder, validator, quick parse).
- [x] ProGuard package fix; privacy policy dialog; empty states on major screens.
- [ ] Device/instrumented QA and release sign-off (ops).

---

## 5. Decisions (confirmed by owner)
1. **Reminders:** max 10 per task (UI + SQL trigger).
2. **Stack:** Supabase + native Android (Kotlin/Compose).
3. **Google Calendar sync:** dropped.
4. **Transcription:** Vosk default.
5. **AI keys:** not in release APK; Edge secrets for production; debug cascade via `secrets.properties` only.
6. **AI provider cascade (debug fallback):** OpenRouter free → Gemini → DeepSeek → OpenRouter paid. Preferred family: DeepSeek. Production: Edge Function.

## 6. Security notes
- `secrets.properties` git-ignored.
- Never ship `service_role` in the app.
- `allowBackup="false"`.
- Rotate previously embedded Mongo/OpenRouter credentials (owner).
