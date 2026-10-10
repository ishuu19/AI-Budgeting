# Life OS changelog

Newest first. Add an entry for every change that touches the assistant, data model or roadmap. Keep each line to what changed and why.

## 2026-10-11

### Added
- **AI-first voice parsing.** `AiRepository.parseVoiceIntentsRouted` now asks a fast OpenRouter model first (6 s limit). On failure, timeout, no key, offline, or the "cloud fallback" setting off, the previous chain runs unchanged: on-device model → rules → older cloud path.
- Release fast-model calls go through the `ai-proxy` Edge Function (`fast_completion`). No provider key is added to the APK.
- `AiProviderRouter.completeFast`: one OpenRouter call in JSON mode, latency-sorted, falls back to the normal cascade.
- `AI_MODEL_FAST` setting (default `google/gemini-3.1-flash-lite`) and `AiConfig.modelFast` / `hasFastModelKey`.
- `domain/assistant`: `Risk`, `ProposedAction`, `ActionPlan`, `ActionSpec`, `ActionRegistry`.
- `data/assistant`: `Executor` (risk gating, validation, idempotency, undo), `PlanParser`, `Orchestrator`. 10 unit tests.
- Docs: `docs/life-os/*`, `CLAUDE.md` (with Karpathy-style behavioral rules), `supabase/planned/` (unapplied SQL).
- Room v11: `assistant_actions` and `event_log`, with `RoomAuditSink` so the executor can record a step and log it only after it applies. `Orchestrator`, `Executor`, and `ActionRegistry` are in Hilt. `012` now includes `event_log` and stays in `supabase/planned/`.
- Room v12: `households` and `household_members` (`Migration11To12` only). `HouseholdDao` and `HouseholdRepository` are in Hilt. Route `household` opens the existing household screen. `013_households.sql` is filled (RLS, `is_household_member`) and stays in `supabase/planned/`. No household sync handler yet.
- Room v13: `items`, `shopping_lists`, and `shopping_items` (`Migration12To13` only). Quantity stays null when unknown. `ItemDao`, `ShoppingListDao`, `ShoppingItemDao`, and `InventoryRepository` are in Hilt. Route `inventory` opens the existing pantry screen from Settings. `014_inventory_shopping.sql` is filled (owner RLS) and stays in `supabase/planned/`. Location is a column on `items`.
- Room v14: `receipts` and `receipt_lines` (`Migration13To14` only). Prices stay null when unknown. `ReceiptDao` and `ReceiptRepository` are in Hilt. Route `receipt/{id}` opens the existing receipt review screen from Settings after that receipt is loaded. `015_receipts.sql` is filled (owner RLS) and stays in `supabase/planned/`. No expense splits.
- Room v15: `people`, `interactions`, `commitments`, and `memories` (`Migration14To15` only). People and memories stay private. No embeddings or `memory_chunks`. The existing repositories are in Hilt; in-memory stores stay for unit tests. Route `people` opens the existing people screen from Settings, and `person/{personId}` opens the existing memory screen. `016_people_memory.sql` is filled (owner RLS) and stays in `supabase/planned/`.
- Room v16: `subscriptions` (`Migration15To16` only). Amount stays null when unknown. `SubscriptionDao` and `SubscriptionRepository` are in Hilt; the in-memory DAO stays for unit tests. Route `subscriptions` opens the existing subscriptions screen from Settings. `017_subscriptions.sql` is filled (owner RLS) and stays in `supabase/planned/`. No reminder worker.
- Room v17: `wardrobe_items` and `outfits` (`Migration16To17` only). Photo path stays null when absent. `WardrobeItemDao`, `OutfitDao`, and `WardrobeRepository` are in Hilt; the in-memory DAOs stay for unit tests. Route `wardrobe` opens the existing wardrobe screen from Settings. `018_wardrobe.sql` is filled (owner RLS) and stays in `supabase/planned/`. No vectors and no styling AI.
- Route `home` opens the existing home screen from Settings. Spend comes from active transactions already loaded through `TransactionRepository`. Review spending opens the Money spend tab. No new table and no balance.

### Decisions
- Evolve the existing Android app (ADR 0001).
- Reuse the existing voice pipeline (parse → confirm card → save → undo → history) instead of building a second one. The fast model returns the same `items` JSON the older cloud path used, so cards, history and undo work unchanged.
- Default model chosen by test, not by name. Same 4-item message: `gemini-3.1-flash-lite` ≈ 1.7 s and correct; `claude-haiku-5.5` ≈ 3.8 s with output cut off at 600 tokens.

### Verified
- Full unit suite: 394 tests, 0 failures.
- Live OpenRouter call with the real voice prompt: a three-item message (expense, reminder with date, debt) returned correct items in ~2.6 s; an ambiguous message returned amount `null` instead of a guess.

### Not verified
- Not run on a device or emulator. The voice screen itself was not exercised.
- Cost and latency over a real session.

### Known limits
- `preferLocalKinds` still lets rules override the AI when the words clearly name a bill, debt, goal or budget. Kept on purpose until the fast prompt carries the bill and debt fields; revisit with real transcripts.
- The OpenRouter key is compiled into **debug** builds only. Release fast-model calls go through `ai-proxy`; a release build does not send a client provider key.
- `Orchestrator` and `Executor` are injectable, and a low-risk plan can be executed through them. The voice screen still uses confirm cards and does not call them yet. The action registry is empty until feature packages contribute specs. `assistant_actions` / `event_log` sync is not bound, and `012` stays in `supabase/planned/`.
