# Privacy and security

## Principles
1. Private by default; sharing is explicit and per record type.
2. Enforcement is server-side (RLS), never UI-only.
3. Users can see, edit and delete everything the AI remembers.
4. The AI reads only the signed-in user's permitted data and has no write access.

## Access model

| Data | Default | Household-shareable |
|---|---|---|
| Transactions, budgets, debts | Private | No. Only `expense_splits` rows are shared |
| Receipts | Private | Lines and totals, if marked shared |
| Items, shopping lists | Household if user is in one | Yes |
| Notes, memories, people, interactions | Private | No |
| Documents (job, invoices, forms) | Private | No |
| Subscriptions | Private | Optional per row |
| Wardrobe | Private | No |
| Voice history, assistant actions | Private | No |

RLS pattern: `user_id = auth.uid()` for private; for shared, `household_id in (select household_id from household_members where user_id = auth.uid())`. Helper SQL function `is_household_member(uuid)` avoids recursive policies.

## Sensitive data
- Storage buckets are private; access through signed URLs with short expiry.
- Secrets stay in `secrets.properties` / Supabase secrets. Never in the client for production (use `ai-proxy`).
- Encrypt especially sensitive fields (ID numbers in `form_profile`) with a per-user key; plan the key handling before Stage 3.
- Local DB: evaluate SQLCipher for Room before storing form profiles or documents.

## Memory controls
- A "What I remember" screen lists `memories` with source, confidence and visibility. Edit, confirm, delete.
- Inferred memories are not used for high-risk actions until confirmed.
- Delete cascades to derived chunks/embeddings.

## Audit
`assistant_actions` and `event_log` record who/what/when/source. Surfaced in an Activity screen with undo where possible.

## Screen and share access
Start with explicit share-to-app and user-triggered capture. No background screen reading. Accessibility-service approaches are out of scope until reviewed for Play policy.

## Threats to check each stage
- Prompt injection from receipts, web pages or emails: treat extracted text as data; the schema-validated action registry limits blast radius; high-risk actions always confirm.
- Cross-household leakage via joins: test RLS with two users in one household and one outside.
- Over-collection: log only what features need; allow export and full account deletion.
