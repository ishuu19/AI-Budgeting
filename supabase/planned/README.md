# Planned backend (not applied)

Staged SQL and Edge Functions for the Life OS roadmap (`docs/life-os/roadmap.md`).
Files here are NOT run by `supabase db push` / `functions deploy`. To promote one:
move it to `supabase/migrations/` with the next sequential number (currently 012+),
add Room entity + sync handler, add an RLS test, then apply.
