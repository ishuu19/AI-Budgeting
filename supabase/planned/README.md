# Planned backend

Migrations 012 to 023 were promoted to `supabase/migrations/` on 2026-10-22 (tested as one chain on a scratch Postgres, with a two-user RLS check on the new tables). They are applied to the hosted project with `supabase db push`. Edge Function stubs below are still planned.

Staged SQL and Edge Functions for the Life OS roadmap (`docs/life-os/roadmap.md`).
Files here are NOT run by `supabase db push` / `functions deploy`. To promote one:
move it to `supabase/migrations/` with the next sequential number (currently 012+),
add Room entity + sync handler, add an RLS test, then apply.
