-- Stage 4. subscriptions. Stays in supabase/planned/ until promoted.
-- Room v16 uses the same columns in camelCase. amount has no default of zero.
--   subscriptions(id, userId, merchant, amount, period, nextRenewalOn, status,
--                 sourceId, updatedAt, deletedAt)
-- amount stays null when unknown.
-- Private by default: owner RLS only. No reminder worker in this file.
-- Depends on 004_calendar_events.sql for public.touch_row_updated_at().

create table if not exists public.subscriptions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  merchant text not null check (char_length(merchant) between 1 and 300),
  amount double precision,
  period text not null check (period in ('weekly', 'monthly', 'quarterly', 'yearly')),
  next_renewal_on date not null,
  status text not null default 'active'
    check (status in ('active', 'cancel_requested', 'cancelled_confirmed')),
  source_id text check (source_id is null or char_length(source_id) between 1 and 200),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists subscriptions_user_updated_idx
  on public.subscriptions (user_id, updated_at, id);

create index if not exists subscriptions_updated_at_idx
  on public.subscriptions (updated_at);

alter table public.subscriptions enable row level security;

create policy "subscriptions_select_own"
  on public.subscriptions for select
  using (user_id = auth.uid());

create policy "subscriptions_insert_own"
  on public.subscriptions for insert
  with check (user_id = auth.uid());

create policy "subscriptions_update_own"
  on public.subscriptions for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "subscriptions_delete_own"
  on public.subscriptions for delete
  using (user_id = auth.uid());

drop trigger if exists subscriptions_touch on public.subscriptions;
create trigger subscriptions_touch
  before insert or update on public.subscriptions
  for each row
  execute function public.touch_row_updated_at();
