-- Voice capture history. The spoken words and what they became.
-- linked item ids stay on the device (they are local Room row ids) and are not stored here.
-- Apply after 004_calendar_events.sql, which defines public.touch_row_updated_at().

create table if not exists public.voice_history (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  transcript text not null default '' check (char_length(transcript) <= 20000),
  result_kind text not null default '' check (char_length(result_kind) <= 64),
  result_summary text not null default '' check (char_length(result_summary) <= 2000),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists voice_history_user_id_idx on public.voice_history (user_id);
create index if not exists voice_history_user_updated_idx on public.voice_history (user_id, updated_at, id);

alter table public.voice_history enable row level security;

create policy "voice_history_select_own"
  on public.voice_history for select
  using (user_id = auth.uid());

create policy "voice_history_insert_own"
  on public.voice_history for insert
  with check (user_id = auth.uid());

create policy "voice_history_update_own"
  on public.voice_history for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "voice_history_delete_own"
  on public.voice_history for delete
  using (user_id = auth.uid());

drop trigger if exists voice_history_touch on public.voice_history;
create trigger voice_history_touch
  before insert or update on public.voice_history
  for each row
  execute function public.touch_row_updated_at();
