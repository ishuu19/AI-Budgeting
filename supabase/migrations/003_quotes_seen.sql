-- Optional cloud tracking of which quotes a user has already seen (widget / daily quote).
-- Soft-delete + updated_at for future sync. Apply after 002_tasks_reminders_alarms_notes.sql.
-- RLS enforced (user_id = auth.uid()). Used by QuoteRepository (not SyncRepository main loop).

create table if not exists public.quotes_seen (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  quote_hash text not null,
  quote_text text not null default '',
  author text not null default '',
  seen_on date not null default current_date,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (user_id, quote_hash, seen_on)
);

create index if not exists quotes_seen_user_id_idx on public.quotes_seen (user_id);
create index if not exists quotes_seen_user_seen_idx on public.quotes_seen (user_id, seen_on desc);

alter table public.quotes_seen enable row level security;

create policy "quotes_seen_select_own"
  on public.quotes_seen for select
  using (user_id = auth.uid());

create policy "quotes_seen_insert_own"
  on public.quotes_seen for insert
  with check (user_id = auth.uid());

create policy "quotes_seen_update_own"
  on public.quotes_seen for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "quotes_seen_delete_own"
  on public.quotes_seen for delete
  using (user_id = auth.uid());
