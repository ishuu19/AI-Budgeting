-- Stage 1. receipts and receipt_lines. Stays in supabase/planned/ until promoted.
-- Room v14 uses the same columns in camelCase. Prices have no default of zero.
--   receipts(id, userId, householdId, merchant, merchantConfidence, purchasedOn,
--            purchasedOnConfidence, total, totalConfidence, currency, paidBy,
--            documentId, transactionId, locallyConfirmed, updatedAt, deletedAt)
--   receipt_lines(id, receiptId, rawText, itemId, qty, unitPrice, lineTotal,
--                 confidence, updatedAt, deletedAt)
-- total, qty, unit_price, and line_total stay null when unknown.
-- No documents table, expense_splits, or storage bucket in this file.
-- household_id is a nullable id. Apply after 013_households.sql.
-- Depends on 004_calendar_events.sql for public.touch_row_updated_at().

create table if not exists public.receipts (
  id uuid primary key default gen_random_uuid(),
  user_id uuid default auth.uid() references auth.users (id) on delete cascade,
  household_id uuid references public.households (id) on delete set null,
  merchant text check (merchant is null or char_length(merchant) between 1 and 300),
  merchant_confidence text check (
    merchant_confidence is null or merchant_confidence in ('confirmed', 'inferred')
  ),
  purchased_on date,
  purchased_on_confidence text check (
    purchased_on_confidence is null or purchased_on_confidence in ('confirmed', 'inferred')
  ),
  total double precision,
  total_confidence text check (
    total_confidence is null or total_confidence in ('confirmed', 'inferred')
  ),
  currency text check (currency is null or char_length(currency) between 1 and 16),
  paid_by text check (paid_by is null or char_length(paid_by) between 1 and 200),
  document_id text check (document_id is null or char_length(document_id) between 1 and 200),
  transaction_id text check (transaction_id is null or char_length(transaction_id) between 1 and 200),
  locally_confirmed boolean not null default false,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists receipts_user_updated_idx
  on public.receipts (user_id, updated_at, id);

create table if not exists public.receipt_lines (
  id uuid primary key default gen_random_uuid(),
  receipt_id uuid not null references public.receipts (id) on delete cascade,
  raw_text text not null check (char_length(raw_text) between 1 and 500),
  item_id text check (item_id is null or char_length(item_id) between 1 and 200),
  qty double precision,
  unit_price double precision,
  line_total double precision,
  confidence text not null check (confidence in ('confirmed', 'inferred')),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists receipt_lines_receipt_id_idx
  on public.receipt_lines (receipt_id);

alter table public.receipts enable row level security;

create policy "receipts_select_own"
  on public.receipts for select
  using (user_id = auth.uid());

create policy "receipts_insert_own"
  on public.receipts for insert
  with check (user_id = auth.uid());

create policy "receipts_update_own"
  on public.receipts for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "receipts_delete_own"
  on public.receipts for delete
  using (user_id = auth.uid());

drop trigger if exists receipts_touch on public.receipts;
create trigger receipts_touch
  before insert or update on public.receipts
  for each row
  execute function public.touch_row_updated_at();

alter table public.receipt_lines enable row level security;

create policy "receipt_lines_select_own"
  on public.receipt_lines for select
  using (
    exists (
      select 1 from public.receipts r
      where r.id = receipt_lines.receipt_id
        and r.user_id = auth.uid()
    )
  );

create policy "receipt_lines_insert_own"
  on public.receipt_lines for insert
  with check (
    exists (
      select 1 from public.receipts r
      where r.id = receipt_lines.receipt_id
        and r.user_id = auth.uid()
    )
  );

create policy "receipt_lines_update_own"
  on public.receipt_lines for update
  using (
    exists (
      select 1 from public.receipts r
      where r.id = receipt_lines.receipt_id
        and r.user_id = auth.uid()
    )
  )
  with check (
    exists (
      select 1 from public.receipts r
      where r.id = receipt_lines.receipt_id
        and r.user_id = auth.uid()
    )
  );

create policy "receipt_lines_delete_own"
  on public.receipt_lines for delete
  using (
    exists (
      select 1 from public.receipts r
      where r.id = receipt_lines.receipt_id
        and r.user_id = auth.uid()
    )
  );

drop trigger if exists receipt_lines_touch on public.receipt_lines;
create trigger receipt_lines_touch
  before insert or update on public.receipt_lines
  for each row
  execute function public.touch_row_updated_at();
