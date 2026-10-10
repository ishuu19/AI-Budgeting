# CLAUDE.md

Guidance for Claude Code in this repo. Start with the project rules, then the behavioral guidelines.

## Project

LedgerAI is a voice-first Android app (Kotlin, Compose, Room, Hilt, Supabase). It is growing into **Lifeline**, a personal "Life OS": one shared data layer plus one universal assistant. The plan lives in `docs/life-os/` and the original brief in `docs/full-plan.md`. Existing product docs: `PLAN.md`, `README.md`, `docs/ui-brief.md`.

Decision: **evolve the existing Android app**. Do not rewrite it and do not introduce a second stack. See `docs/life-os/adr/0001-evolve-existing-android.md`.

## Architecture rules (non-negotiable)

1. **One brain.** Features share one data model. No per-feature silo tables that duplicate people, items, money or events.
2. **AI reasons, the database remembers, the executor acts.** The model returns a typed action plan. Only the executor writes data, and only after a risk check.
3. **Risk tiers decide approval.** Low risk (add a grocery item) runs immediately. Medium (edit a transaction, create an event from a screenshot) needs one tap. High (money, cancellations, outbound messages) needs explicit confirmation. Never report an action as done unless the executor confirmed it.
4. **Never invent facts.** Unknown stock, price or date means ask or say unknown. Mark extracted values as `confirmed` or `inferred`.
5. **Keep sources.** Every derived record keeps a link to the document, receipt or utterance it came from, so the user can correct it.
6. **Privacy by default.** Personal data is private. Household sharing is opt-in per record type. Enforce it with Supabase RLS, not client code.
7. **Offline-first.** Room is the local source of truth and syncs to Supabase (existing pattern in `data/sync`). New tables follow the same `updated_at` / `deleted_at` soft-delete convention.
8. **Known facts use SQL, fuzzy recall uses vectors.** Don't push inventory counts through embeddings.
9. **Background work goes through WorkManager or Edge Functions**, never "while the app is open".

## Conventions

- Package layout: `data/` (Room, repositories, sync, AI), `domain/` (models, pure logic), `presentation/` (Compose screens), `service/`, `worker/`, `widget/`. New modules add subfolders to these; they do not add new top-level layers.
- Migrations: sequential `supabase/migrations/NNN_name.sql`, RLS enabled, `user_id default auth.uid()`, length checks on text, matching the existing files. Planned but unapplied SQL lives in `supabase/planned/` until promoted.
- Edge Functions validate the JWT and respond in fixed JSON shapes. The AI has read access to the signed-in user's data only and no direct write access.
- Keep secrets in `secrets.properties` / `.env`. Never commit them.

## Behavioral guidelines

Adapted from the four principles in [multica-ai/andrej-karpathy-skills](https://github.com/multica-ai/andrej-karpathy-skills), which derive from Andrej Karpathy's observations on common LLM coding mistakes. These bias toward caution over speed; for trivial edits use judgment.

To install the original as a plugin: `/plugin marketplace add forrestchang/andrej-karpathy-skills`, then `/plugin install andrej-karpathy-skills@karpathy-skills`.

### 1. Think before coding
- State assumptions. If uncertain, ask rather than guess.
- When a request has several readings, present them instead of silently picking one.
- Push back when a simpler approach exists. Stop and name what is confusing.

### 2. Simplicity first
- Write the minimum code that solves the stated problem. Nothing speculative.
- No abstractions for single-use code, no unrequested configurability, no handling of impossible cases.
- If 200 lines could be 50, rewrite it.

### 3. Surgical changes
- Touch only what the task needs. Match the existing style even if you'd do it differently.
- Don't refactor or reformat adjacent code. Mention unrelated dead code, don't delete it.
- Remove only the imports and symbols that your own change orphaned.
- Every changed line should trace to the request.

### 4. Goal-driven execution
- Turn tasks into verifiable goals: "fix the bug" becomes "write a test that reproduces it, then make it pass".
- For multi-step work, state a short plan where each step has a check:
  `1. step → verify: check`
- Loop until verified. Report honestly what was and wasn't run.

## Working with the roadmap

Log every assistant, data-model or roadmap change in `docs/life-os/CHANGELOG.md`.

Work is staged in `docs/life-os/roadmap.md`. Before starting a feature, confirm which stage it belongs to and that its prerequisites (shared data layer, assistant pipeline) exist. Don't build Stage 3 features on a Stage 1 gap.
