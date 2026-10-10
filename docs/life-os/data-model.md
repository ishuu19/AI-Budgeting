# Shared data model

Principles: one table per concept, owned by a user or a household, with provenance. Conventions match existing migrations (`user_id default auth.uid()`, `updated_at`, `deleted_at`, RLS on, text length checks).

## Reuse vs new

| Concept | Existing | New |
|---|---|---|
| Money | `transactions` (001), budgets, debts, goals, bills | `receipts`, `receipt_lines`, `subscriptions`, `expense_splits` |
| Events & commitments | `calendar_events` (004), `plans`, `habits` | `commitments` (link event ↔ person) |
| Work | `job_applications` (007) | `documents` link, `form_profile` |
| Notes & log | `notes`, `life_log` (008), `voice_history` (009) | `memories` (extracted facts) |
| Home | none | `items`, `item_locations`, `shopping_lists`, `shopping_items` |
| Households | none | `households`, `household_members` |
| People | none | `people`, `interactions` |
| Wardrobe | none | `wardrobe_items`, `outfits` |
| AI plumbing | `voice_history` | `assistant_actions`, `event_log`, `memory_chunks` |

## Entities (new)

```
households(id, name, created_by)
household_members(household_id, user_id, role[owner|member], joined_at)

items(id, owner_user_id, household_id?, name, category, quantity, unit,
      location, expires_on?, kind[food|supply|clothing|other],
      source_type, source_id, confidence[confirmed|inferred])
shopping_lists(id, household_id?, user_id, name)
shopping_items(id, list_id, item_name, qty, added_by, bought_by?, bought_at?, status)

receipts(id, user_id, household_id?, merchant, purchased_on, total, currency,
         paid_by, document_id, transaction_id)
receipt_lines(id, receipt_id, raw_text, item_id?, qty, unit_price, line_total, confidence)
expense_splits(id, transaction_id, user_id, share_amount, settled_at?)
subscriptions(id, user_id, merchant, amount, period, next_renewal_on,
              status[active|cancel_requested|cancelled_confirmed], source_id)

people(id, user_id, name, org?, role?, notes, visibility)
interactions(id, person_id, occurred_on, where?, summary, source_id)
commitments(id, person_id?, event_id?, text, due_on?, status)

documents(id, user_id, storage_path, mime, kind[receipt|job|invoice|form|other],
          extracted_json, extracted_at)
memories(id, user_id, text, kind[fact|preference|goal], status[confirmed|inferred],
         source_type, source_id, visibility[private|household])
memory_chunks(id, user_id, memory_id|document_id, content, embedding vector(768))   -- Stage 3

wardrobe_items(id, user_id, name, type, colors[], season[], photo_path, laundry_status)
outfits(id, user_id, occasion, item_ids[], worn_on?)

assistant_actions(id, user_id, input_ref, action_index, plan_json, risk, status[proposed|approved|applied|rejected|failed],
                  applied_at, error)
event_log(id, user_id, type, payload, source_action_id, created_at)
```

## Provenance

Every derived row carries `source_type` and `source_id` (receipt, voice_history, document, manual). Corrections edit the derived row, never the source. "Confirmed" means the user saw and accepted it, or entered it by hand. "Inferred" means the AI produced it.

## Visibility

`user_id` rows are private. `household_id` rows are visible to members. A row with both is owned by the user and shared to the household. Private-by-default types: transactions, notes, memories, people, documents, subscriptions. Shareable types: items, shopping lists, receipts (totals and lines), expense splits. See [privacy-security.md](privacy-security.md).

## Sync

Each new table gets a Room entity/DAO and a `data/sync/extra` handler like existing tables, using `updated_at, id` cursor paging and soft deletes. Planned SQL is staged in `supabase/planned/migrations/`.
