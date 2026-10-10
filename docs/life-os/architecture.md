# Architecture

## Layers

```
 Capture          voice · text · camera · document · shared screen · authorized events
    │
 Assistant        Understand → Retrieve context → Propose ActionPlan        (AI, no writes)
    │
 Executor         Risk check → (confirm) → apply → audit log → notify modules (deterministic)
    │
 Domain modules   Home · Household · Money · People · Time&Work · Wardrobe     (plain Kotlin, SQL)
    │
 Shared data      Room (offline) ⇄ sync ⇄ Supabase Postgres (+RLS, +pgvector, +Storage)
```

Rule: the model never touches the database. It outputs an `ActionPlan` (see [assistant-pipeline.md](assistant-pipeline.md)); the executor validates and applies it.

## Module map

Package root: `app/src/main/java/com/ledgerai/app/`. Existing folders are kept. New ones are marked `+`.

| Module | domain/ | data/ | presentation/screens/ | Notes |
|---|---|---|---|---|
| Assistant (core) | `+assistant` (ActionPlan, Risk, Intent) | `ai/` (existing router, rules) + `+assistant` (Orchestrator, Executor, ContextRetriever) | `ai/`, `voice/`, `search/` | Extends the existing multi-intent voice router instead of replacing it |
| Home & Shopping | `+home` | `+home` (inventory, shopping, expiry) | `+home` | "Buy Never Twice" |
| Household | `+household` | `+household` | `+household` | Memberships, splits, shopping turns |
| Money | `finance/` | `finance/`, `repository/Transaction*` | `money/`, `transactions/`, `bills/`, `budget/`, `debts/`, `goals/` | Add receipts, line items, subscriptions |
| People | `+people` | `+people` | `+people` | Contacts, interactions, commitments |
| Time & Work | `schedule/` | `schedule/`, `repository/Calendar*`, `Job*` | `calendar/`, `jobs/`, `tasks/`, `today/` | Existing; add document/screenshot parsing |
| Wardrobe | `+wardrobe` | `+wardrobe` | `+wardrobe` | Stage 4 |
| Memory & Documents | `+memory` | `+memory` | `+memory` ("What I remember") | Facts with provenance, embeddings later |
| Platform | `util/` | `sync/`, `local/`, `auth/`, `preferences/` | `settings/` | Existing |

## Cross-module communication

Modules do not call each other's repositories. They publish **domain events** on an in-process bus and the executor writes an `event_log` row for each.

```
ReceiptConfirmed ──► Money.recordTransaction
                 ├─► Home.upsertItems
                 ├─► Household.attributePurchase
                 └─► Home.recomputeShoppingList
```

Subscribers are idempotent (keyed by `source_id` + event id) so retries and re-sync cannot double-apply.

## Server side (Supabase)

| Piece | Purpose |
|---|---|
| Postgres + RLS | Source of truth; every table has `user_id` or `household_id` policies |
| Storage | Private buckets: `receipts`, `documents`, `wardrobe` |
| Edge `ai-proxy` (exists) | Provider-independent model gateway, JWT-checked, fixed response shapes |
| Edge `assistant` (planned) | Orchestration for heavy requests (multi-module retrieval) |
| Edge `receipt-parse`, `document-parse` (planned) | Vision/OCR extraction to structured JSON |
| `pg_cron` jobs (planned) | Expiry alerts, renewal reminders, daily briefing |

Skeletons live in `supabase/planned/` until promoted.

## Retrieval

- **Structured questions** (what's in the fridge, spend this month): SQL against Room first, Postgres when online and needed.
- **Fuzzy recall** (what did I tell that recruiter): `memory_chunks` with pgvector, filtered by `user_id` and visibility before similarity ranking.
- The orchestrator builds a small context bundle (existing `ContextBuilder`) to keep token use low, per the project's AI data rules.

## Non-goals (for now)

iOS, web dashboard, browser extension, bank-feed integrations, automatic subscription cancellation. See [roadmap.md](roadmap.md).
