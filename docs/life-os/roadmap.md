# Roadmap

Each stage ends with a verifiable demo. Do not start a stage until the previous one's exit checks pass.

## Stage 0: Foundation (shared layer) — in progress
Goal: AI understands input and the pipeline applies it safely. See [CHANGELOG.md](CHANGELOG.md).
- [x] AI-first voice parsing through a fast OpenRouter model, with the old chain as fallback
- [x] `domain/assistant`: `ActionPlan`, `Risk`, `ActionRegistry`
- [x] `data/assistant`: `Orchestrator`, `Executor`, `PlanParser` (tested, not yet called by the app)
- [ ] Release path: route the fast model through `ai-proxy` so no key ships in the APK
- [ ] Measure on device: latency, how often AI vs fallback answers, wrong-kind rate
- [ ] `ContextRetriever`: send the model a few relevant recent items (known merchants, open debts) so it organizes better
- [ ] Tables `assistant_actions`, `event_log` (+ Room v10→v11 + sync)
- [ ] `ActionConfirmCard` and `ProvenanceChip` components
- [ ] Golden-set test harness for intents
- Exit: "add toothpaste to the list" via voice produces a validated plan, applies, logs, and can be undone.

## Capture engine (cross-stage, in progress)
Photo and voice input that the AI files for the user. Feeds Stage 1 (receipts, pantry), Stage 3 (people, memories) and Stage 4 (wardrobe, subscriptions).
- [x] Private photo storage (Cloudflare R2 via presigned URLs), offline queue, WorkManager upload
- [x] One AI pass per photo: name, describe, file (clothing, food, receipt, person)
- [x] Meal and outfit suggestions from names and stock only (no photos sent)
- [x] Assistant gesture, tile, shortcuts, share-to-app
- [x] R2 bucket created, `media-sign` and `ai-proxy` deployed
- [x] `019_media.sql` applied
- [ ] Verify upload from a signed-in device
- [x] Route vision through `ai-proxy` so release builds can read photos (deploy pending)
- [x] Sync handler for `media_assets`
- [x] Capture actions registered; filing goes through `Executor`
- [x] Weather for outfit suggestions (needs a stored location)
- Exit: photograph a shirt, a receipt and a friend with a note; each is named, filed and backed up; ask "what can I cook" and "I have a party" and get answers from stock and wardrobe names.

## Stage 1: Buy Never Twice
- [ ] `items`, `shopping_lists`, `shopping_items`
- [ ] Receipt scan → `receipts`/`receipt_lines` → inventory + transaction (one confirm)
- [ ] "Do I already have X?" query
- [ ] Expiry alerts via WorkManager; low-stock → shopping suggestion
- [ ] Reminders reuse existing calendar events
- Exit: scan a real receipt, correct one line, see inventory + transaction updated once; ask "do I have eggs".

## Stage 2: Shared living and money
- [ ] `households`, `household_members`, RLS helper `is_household_member`
- [ ] Purchase attribution, shopping turns, `expense_splits`, balances
- [ ] Budgets per household
- [ ] RLS tests: two members + one outsider
- Exit: two accounts share a list; a split is computed and settled; private transactions stay invisible to the other member.

## Stage 3: Calendar, people, career
- [ ] `people`, `interactions`, `commitments`; pre-meeting brief
- [ ] `memories`, "What I remember" screen; pgvector + `memory_chunks`
- [ ] Document/screenshot → event or job extraction (extends `JobPasteParser`, `ScheduleImageRecognizer`)
- [ ] Form-assist via share-to-app; browser extension only if validated
- Exit: before a calendar event, the app shows last notes and the promised follow-up; a pasted job listing becomes a tracked application.

## Stage 4: Proactive AI
- [ ] `subscriptions` detection from receipts/notifications; renewal reminders; cancel *guidance* only
- [ ] Wardrobe + outfit suggestions (weather + event)
- [ ] Automation settings: choose what auto-runs vs asks
- [ ] Daily/weekly briefing (`pg_cron` + Edge Function)
- Exit: a user can turn each automation on/off; none perform high-risk actions unattended.

## Cross-cutting every stage
Migration promoted from `supabase/planned/` · RLS test · Room entity + sync handler · golden-set cases · privacy review of new data · docs updated.

## Risks

| Risk | Mitigation |
|---|---|
| Scope creep across ten features | Stage gates; shared layer first |
| OCR/vision errors corrupt inventory | Review step, confidence flags, keep source |
| Edge Function time limits | Chunk work; keep heavy parsing to single-document calls |
| Sync conflicts with multi-user data | `updated_at` cursor + soft delete; server-wins on shared rows, test with two devices |
| Platform limits on screen access | Share-to-app first; no background screen reading |
| AI cost | Rules first, small context bundles, cache, cheap extraction model |
| iOS/web demand | Revisit ADR 0001 after Stage 2 |
