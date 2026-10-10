# ADR 0001: Evolve the existing Android app

Status: accepted (2026-10-11)

## Context
`docs/full-plan.md` suggests React Native/Expo, Next.js, NestJS, Postgres+pgvector, Redis/BullMQ. The repo already contains a working native Android app with Room, Hilt, Compose, Glance, WorkManager, Vosk, Supabase Auth/PostgREST, an `ai-proxy` Edge Function and 11 migrations.

## Decision
Keep Kotlin + Supabase. Map the plan's stack onto what exists:

| Plan | Here |
|---|---|
| Expo mobile app | Existing Compose app |
| NestJS backend | Supabase Edge Functions (Deno) for server-side logic |
| Postgres + RLS | Supabase Postgres + RLS (already in use) |
| pgvector | Supabase supports the `vector` extension; enable in Stage 3 |
| S3 storage | Supabase Storage buckets (receipts, documents, wardrobe photos) |
| BullMQ jobs | WorkManager on device, `pg_cron` + Edge Functions on server |
| Model gateway | Extend `AiProviderRouter` / `ai-proxy` |
| Browser extension, web dashboard | Deferred to Stage 3; not needed to prove the core |

## Consequences
- No rewrite risk; Stage 1 ships on current infrastructure.
- iOS and web are out of scope until Stage 3. If wanted later, the Supabase backend and the action schema are client-agnostic, which keeps that door open.
- Server logic is limited to Edge Function constraints (short-running). Long jobs (email scanning) need `pg_cron` plus chunking.

## Revisit if
We need iOS, heavy server-side processing, or multiple third-party integrations that don't fit Edge Functions.
