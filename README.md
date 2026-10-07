# LedgerAI

Voice-first personal finance Android app (Kotlin, Compose, Material 3).

## Current status

**Phase 1 (foundation)** is in progress: MongoDB / OpenRouter / Whisper / cosmetic Google login removed. Data is held in an in-memory store so the UI stays runnable until Room + Supabase land in Phase 2.

See [PLAN.md](PLAN.md) for requirements, architecture, and build phases.

## Stack (target)

| Concern | Choice |
|---|---|
| Client | Kotlin, Compose, Hilt, Glance, WorkManager |
| Local store | Room (Phase 2) |
| Backend | Supabase (Auth, Postgres + RLS, Edge Functions) |
| AI | Gemini via Edge Function (key never in APK) |
| Voice | Vosk offline (Phase 4) |

## Setup

1. Open this folder in Android Studio (SDK 34+).
2. Copy secrets:

```bash
cp secrets.properties.example secrets.properties
```

3. Fill `secrets.properties` (see `secrets.properties.example`):
   - **Client:** `SUPABASE_URL` + `SUPABASE_ANON_KEY` (or `SUPABASE_PUBLISHABLE_KEY`)
   - **AI cascade (server/Edge only):** OpenRouter free → Gemini → DeepSeek → OpenRouter paid — never put those keys in the APK
4. Leave AI keys blank until Phase 7 Edge Function wiring.
4. Build:

```bash
./gradlew assembleDebug
```

## Security

- Never put Mongo URIs, OpenRouter keys, Gemini keys, or Supabase `service_role` in the APK.
- `secrets.properties` is gitignored.
- Cloud backup is disabled for app data.
- Rotate any credentials that were previously compiled into debug builds.

## Architecture (high level)

```
app/
├── data/
│   ├── local/          # InMemoryStore (→ Room in Phase 2)
│   ├── repository/     # Domain repositories
│   └── preferences/    # DataStore session + prefs
├── domain/model/
├── presentation/       # Compose screens + ViewModels
├── service/            # Voice FGS, notifications, boot receiver
├── widget/             # Glance voice widget
└── worker/             # Budget / debt reminders
```
