# Life OS changelog

Newest first. Add an entry for every change that touches the assistant, data model or roadmap. Keep each line to what changed and why.

## 2026-10-11

### Added
- **Capture engine.** One input for everything (Capture tab, floating sheet). Photos are saved on the phone first, then a background `CaptureWorker` (network required, backoff retry) has the AI look at each photo once, name it, describe it and file it; afterwards it uploads the photo to a private **Cloudflare R2** bucket (`{userId}/{assetId}.jpg`). Text data stays in Supabase; files live in R2. Offline capture just waits in the queue. Local-only accounts keep photos on the phone and never retry uploads.
- Room v18: `media_assets` (`Migration17To18` only). Each filed record keeps the photo id as its source (`photoPath`, `sourceId`). `019_media.sql` (table + owner RLS) stays in `supabase/planned/`. The `media-sign` Edge Function is in `supabase/functions/`; the private R2 bucket `ledgerai-media` exists and the secrets are in the gitignored `supabase/.env`. R2 secrets are set on Supabase and `media-sign` (v1) and `ai-proxy` (v8, adds `vision_capture`) are deployed. A signed PUT, GET and DELETE against the bucket returned 200/200/204. `media-sign` itself has not been called with a real user JWT yet. The APK never holds R2 keys: `media-sign` checks the user's JWT and returns a 5-minute presigned URL for `{user.id}/{id}.jpg`, and the app sends or fetches the bytes directly. No sync handler for `media_assets` yet.
- Filing by kind: clothing → `wardrobe_items`; food (fridge/pantry photo) → `items` marked `inferred`, never overwriting a count the user already has; receipt → draft `receipts` (unconfirmed, no transaction); person → `people` + `interactions` + `memories` on the capture date, only when the user's own words give the name (otherwise the photo asks "Who is this?"). A repeating charge on a receipt becomes a one-tap "Track it" subscription suggestion.
- **Ask from names only.** `AskService` routes a message with one fast-model call (`meal` / `outfit` / `log`), then plans from text: stock lines for meals, garment lines for outfits. Photos are never sent. Outfit ids returned by the model are validated against the wardrobe; unowned meal ingredients move to "missing". Anything else goes to the existing voice pipeline.
- **App without opening the app.** `QuickCaptureActivity` (floating sheet) is reachable from the assistant gesture (`VoiceInteractionService` + session, pick the app under Default digital assistant), a Quick Settings tile, launcher shortcuts (Speak, Snap) and Share → image from any gallery.
- **New in-app look.** Deep blue and white with one amber highlight, light and dark (follows the system). Tokens live in `presentation/theme/Color.kt`; `L.*` getters follow the theme, `L.Primary` is the brand ink on the page. Widget colors are unchanged.
- The center tab is now **Capture** (tap = Capture segment, hold = voice as before). The old Speak, Notes and Ask segments are still there.

### AI first, from anywhere (Hong Kong fix)
- **Cause of "Looking at this…" forever:** Gemini answered "User location is not supported" and OpenRouter "model not available in your region" to calls made straight from a phone in Hong Kong. All AI calls now go through `ai-proxy`, invoked with `x-region: us-east-1`, so the server's region is what the providers see.
- **`fast_completion` is a provider cascade on the server:** Gemini flash-lite, Gemini flash, xAI Grok, OpenRouter (fast model, then a free model), DeepSeek. The first valid JSON object wins. `XAI_API_KEY`, `XAI_MODEL` and `DEEPSEEK_API_KEY` were added as Edge secrets (never in the APK). Tested end to end with a throwaway user (deleted): three sample sentences and a photo were all answered through the US route.
- **The AI decides, not the rules.** Voice parsing asks the proxy first (the phone's own key is only a debug fallback), with a 14 s budget. `preferLocalKinds` no longer overrides the AI for bills, debts, goals, budgets or jobs; rules only run if the AI cannot answer (offline or every provider failed), plus stock adjustments, which the AI has no field for.
- Receipts now give one-tap suggestions: add the food lines to the pantry, log the total as spending, track a subscription. Cards say "Working on it…", "Offline…" or "Sign in again…" instead of waiting silently. The mic only opens when tapped.
- **Plan and Money screens rebuilt** (Calendar, Tasks, Jobs, Overview, Spend and its add form, Today's spend guide, Budgets, Goals, Bills, Debts, Notes): hierarchy and one-tap actions, an AI bar above each tab, "Say: …" empty states, pickers and chips instead of typed formats.

### Input fixes: direct photo upload, no typed formats
- **No screen asks for a file path any more.** Wardrobe had a "Photo path" text field. Every photo field now offers Take photo and Choose (`PhotoField`, `rememberPhotoActions`); the photo is stored, uploaded to R2 and linked by id (`PhotoSaveViewModel`).
- **Wardrobe** rebuilt: snap clothes and the AI names them, a photo grid, one-tap "Wore it today", chips for type, season and laundry, a date picker instead of typing `yyyy-MM-dd`.
- **People and person page** rebuilt: add a person with a photo and one line (the AI files the photo on the date and keeps the memory); "Add photo or moment" with a date picker, photos shown on each moment; promises use a due-date picker and Open/Done chips. The typed "Source", "Event" and date-format fields are gone.
- **Pantry** gets Scan food, Scan receipt and From gallery. **Receipt review** shows the photo it was read from. **Household** no longer asks you to type a user id: Copy my member ID, Paste member ID.

### Chat-first rebuild (UI only; backend unchanged)
- **Home is one conversation** (`screens/chat/ChatScreen`). Say it, type it or send photos; the AI replies in the thread: photos get filed (cards with the one answer or tap they need), meal and outfit questions are answered, and expenses, reminders and notes appear as the same confirm cards as before (`VoiceRecorderViewModel`, with Save, Edit and Undo). A "Today" strip on top opens the full day.
- **Tabs: Home, Plan, big mic, Money, Life.** The mic starts listening on tap or hold and sends by itself when you stop talking. Capture and Today tabs are gone; Notes moved into Plan (Calendar, Tasks, Notes, Jobs); the old Speak screen is now "Voice log".
- **More** (the dots on Home) holds Full day, Voice log, Life log, Ask about my money, Insights, Subscriptions, Focus, and Settings.
- "Add manually" sits under the composer on Home and opens one sheet for every manual form.
- Photo bug and analysis ordering fixes are above. UI screens other than Home, the shell and More keep their old structure with the new colors.

### AI-first redesign
- **Photo bug fixed.** "Couldn't read that photo" came from treating the null that a bounds-only `BitmapFactory` decode returns as a failure. Photos now save.
- **Photo analysis no longer waits for the upload login.** `CaptureWorker` runs analysis first; only the R2 upload needs a fresh Supabase session. A phone whose refresh token is dead ("Already Used") now still gets photos named and filed; only the backup waits for a new sign-in.
- Photo reading uses `gemini-flash-latest` (the old `gemini-2.0-flash` returned 404), in the app and in `ai-proxy` (redeployed).
- **New layout around "say or snap, the AI does it".** Bottom tabs are Today, Plan, Capture, Money, Life. Settings moved behind a gear on Today. The old "You" list is gone: Pantry, Wardrobe, People and Home live in the new **Life** tab; Subscriptions moved into Money Overview; Money Overview is one main figure plus tiles.
- **Today is the AI front door.** The voice/photo/text composer sits at the top of Today, with a "Needs your OK" queue under it for anything the AI could not finish (a name to confirm, a subscription to track).
- **Manual is the backup.** "Add manually" under every composer opens one sheet: expense, task, event, note, pantry item, clothes, person.
- Appearance setting (System / Light / Dark); window background and splash follow the theme; floating tab bar.

### Capture decisions
- Split storage by data type (user decision): text data in Supabase (Postgres + RLS), files in Cloudflare R2 (S3-compatible, no egress fees). Access control for files is the Edge Function, which builds the key from the verified user id.
- Capture filing goes through `Executor` with registered `capture.*` actions (`CaptureActions`). Garment, food, receipt draft and person are LOW; `capture.track_subscription` is MEDIUM and runs only on the user's tap. The input ref is the photo id, so a retried worker cannot file twice, and every step lands in `assistant_actions`.
- `media_assets` now syncs (`MediaAssetExtraSync`); `019_media.sql` is applied to the live project (RLS on, 4 policies) with its own copy of `touch_row_updated_at()`. NOTE: the live project only has migrations 001-003; 004-011 (calendar, plans, habits, jobs, voice history, ...) are not applied, so sync for those tables cannot work until they are.
- Outfit asks add current weather (Open-Meteo) from the last stored location when it is under 24 h old; otherwise weather is omitted, never guessed.

### Capture: not verified
- Not run on a device. Assistant-gesture registration, the tile, shortcuts, share target, camera and mic permission flows, and the dark theme have not been seen on a phone.
- Release photo reading and meal/outfit asks go through `ai-proxy` (new `vision_capture` type; `fast_completion` for asks) via `AiGateway`. Debug builds with a local key call the provider directly. Deployed; `GEMINI_API_KEY` already exists on the project.

- **AI-first voice parsing.** `AiRepository.parseVoiceIntentsRouted` now asks a fast OpenRouter model first (6 s limit). On failure, timeout, no key, offline, or the "cloud fallback" setting off, the previous chain runs unchanged: on-device model → rules → older cloud path.
- Voice parses are measured in memory (latency, fast vs fallback, wrong-kind). Nothing is stored.
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
- `domain/assistant`: `KnownMerchant`, `OpenDebtFact`, and `ContextBundle`, so context selection and prompt rendering compile.
- The voice prompt receives selected known merchants and open debts, and unknown amounts stay unknown.

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
