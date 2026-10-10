# Assistant pipeline

One pipeline for every input. This is what makes the app one product.

```
Capture → Understand → Retrieve → Propose → Confirm? → Execute → Remember
```

| Step | Owner | Output |
|---|---|---|
| Capture | `presentation/screens/voice`, camera/share intents, workers | `RawInput{kind, payload, ts}` |
| Understand | Rule engine first (`RuleEngine`, `QuickParse`), model only if rules miss | `Intent[]` with entities, dates, amounts |
| Retrieve | `ContextRetriever` | Minimal context bundle (relevant items, events, people, recent spend) |
| Propose | Orchestrator (via `AiProviderRouter` / `ai-proxy`) | `ActionPlan` (validated JSON) |
| Confirm | UI confirm card (exists for finance) | approve / edit / reject |
| Execute | `Executor` | applied changes + `event_log` rows |
| Remember | Memory service | `memories` (inferred until accepted) |

Rules before models keeps cost and latency down and matches the existing design.

## Input kinds

`voice`, `text`, `image` (receipt, poster, note, screenshot), `document` (PDF), `shared_screen` (share-to-app), `event` (authorized notification, calendar, email later).

## ActionPlan (fixed schema)

```json
{
  "input_ref": "voice:2026-10-11T09:14:00Z",
  "actions": [
    { "type": "shopping.add_item", "args": {"name": "toothpaste", "qty": 1}, "risk": "low" },
    { "type": "money.record_transaction", "args": {"amount": 18.40, "merchant": "Lidl"}, "risk": "medium" }
  ],
  "unknowns": ["payer not stated"],
  "reply": "Added toothpaste. Record the 18.40 Lidl purchase?"
}
```

- `type` comes from a closed registry (`ActionRegistry`). Unknown types are rejected.
- `args` are validated against a per-type schema (`AiResponseValidator` pattern).
- `unknowns` must be surfaced, never guessed.

## Risk tiers

| Tier | Examples | Behaviour |
|---|---|---|
| low | add shopping item, add note, create reminder | Apply immediately, show undo |
| medium | record/edit transaction, create event from screenshot, update inventory from receipt | One-tap confirm card |
| high | settle a debt/split, cancel subscription, send message, delete data | Explicit confirm with summary; never auto-run; completion only reported after verification |

User settings can lower friction for specific low/medium types in Stage 4. High never auto-runs.

## Executor guarantees

1. Validate schema and permissions (current user, household membership).
2. Run in one DB transaction per action; partial failure leaves a `failed` row with the error.
3. Idempotent on `(input_ref, action_index)`.
4. Write `assistant_actions` + `event_log` for audit and "what did the AI do" history.
5. Emit domain events so other modules update (receipt → money + inventory + split + shopping).

## Failure honesty

If a step cannot be verified (no network, integration missing, permission denied), the reply says what was and wasn't done. No "cancelled" or "sent" without confirmation from the executor.

## Evaluation

Keep a small golden set per intent (`app/src/test/.../assistant/`): utterance or image → expected `ActionPlan`. A change to prompts or rules must not reduce pass rate. This is the verification loop for AI work.
