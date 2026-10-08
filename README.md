# LedgerAI

Voice-first personal finance Android app (Kotlin, Compose, Material 3).

## Current status

**Phases 1–9 coded.** Offline-first Room store, Supabase Auth + sync, Vosk STT, multi-intent voice, routines/reminders, alarm ringing, Edge AI proxy, dashboard §3.6, notes AI, quotes widget. Build/runtime verification is separate — wire real secrets and deploy Edge Function before production use.

| Area | Status |
|---|---|
| Room (finance + tasks/reminders/routines/alarms/notes) | Done |
| Supabase Auth (Credential Manager → Google ID token) | Done (needs secrets) |
| Sync worker (Room ↔ PostgREST) | Done (needs project + JWT) |
| Vosk offline STT + multi-intent voice | Done |
| Routines + task/bill/debt/budget reminders | Done |
| Alarms (ring, tones, ramp, repeat days, boot re-arm) | Done |
| AI cascade (debug) + Edge `ai-proxy` (production path) | Done |
| Note AI (summarize / tag / ask) | Done |
| Dashboard §3.6 + ~300 quotes + voice-only widget | Done |
| Unit test stubs + ProGuard package fix + privacy text | Done |

See [PLAN.md](PLAN.md) for architecture and owner decisions.

## Stack

| Concern | Implementation |
|---|---|
| Client | Kotlin, Compose, Hilt, Glance, WorkManager |
| Local store | Room (`ledgerai.db`) |
| Backend | Supabase Auth + PostgREST + Edge Function `ai-proxy` |
| Auth | Credential Manager → Supabase `signInWithIdToken` (+ Continue locally) |
| AI | Edge Function when URL+JWT present; else debug cascade from `secrets.properties` |
| Voice | MediaRecorder + Vosk offline STT → intent router |

## Setup

1. Open this folder in Android Studio (SDK 34+).
2. Copy secrets:

```bash
cp secrets.properties.example secrets.properties
```

3. Fill `secrets.properties`:
   - `SUPABASE_URL`, `SUPABASE_ANON_KEY` (or publishable key)
   - `SUPABASE_GOOGLE_WEB_CLIENT_ID` for Google Sign-In
   - Optional debug AI keys (OpenRouter / Gemini / DeepSeek) — release BuildConfig stays empty
4. Apply SQL under `supabase/migrations/` to your Supabase project; deploy `supabase/functions/ai-proxy`.
5. Build:

```bash
./gradlew assembleDebug
```

## Security

- Never put provider keys or Supabase `service_role` in a **release** APK.
- Debug may inject AI keys into BuildConfig for local testing only.
- `secrets.properties` is gitignored.
- `allowBackup="false"`.
- Rotate any credentials that were previously compiled into debug builds.

## Architecture (high level)

```
app/
├── data/
│   ├── local/room/       # Offline source of truth
│   ├── sync/             # PostgREST push/pull
│   ├── voice/            # Vosk model + transcriber
│   ├── ai/               # Edge client, cascade, validator, context
│   ├── repository/
│   └── preferences/      # DataStore session + prefs
├── domain/model/
├── presentation/         # Compose screens + ViewModels
├── service/              # Alarms, notifications, boot
├── widget/               # Glance voice + quotes
└── worker/               # Budget, debt, bill, task, quote, sync, insights
supabase/
├── migrations/           # RLS schema
└── functions/ai-proxy/   # JWT + rate-limit + Gemini/OpenRouter
```
