-- Life log: what you did, and the check-in windows that asked.
-- visit ids stay on the device (they point at local Room visits) and are not stored here.
-- start_at and end_at are wall-clock "floating" times in timestamp without time zone.
-- source and state store the app enum names as text.
-- Apply after 004_calendar_events.sql, which defines public.touch_row_updated_at().

-- ─── activity_entries ────────────────────────────────────────────────────────

create table if not exists public.activity_entries (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  start_at timestamp not null,
  end_at timestamp not null,
  text text not null default '' check (char_length(text) <= 20000),
  source text not null default 'MANUAL'
    check (source in ('MANUAL', 'VOICE', 'SUGGESTED')),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists activity_entries_user_id_idx on public.activity_entries (user_id);
create index if not exists activity_entries_user_updated_idx on public.activity_entries (user_id, updated_at, id);

alter table public.activity_entries enable row level security;

create policy "activity_entries_select_own"
  on public.activity_entries for select
  using (user_id = auth.uid());

create policy "activity_entries_insert_own"
  on public.activity_entries for insert
  with check (user_id = auth.uid());

create policy "activity_entries_update_own"
  on public.activity_entries for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "activity_entries_delete_own"
  on public.activity_entries for delete
  using (user_id = auth.uid());

drop trigger if exists activity_entries_touch on public.activity_entries;
create trigger activity_entries_touch
  before insert or update on public.activity_entries
  for each row
  execute function public.touch_row_updated_at();

-- ─── checkin_windows ─────────────────────────────────────────────────────────

create table if not exists public.checkin_windows (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  start_at timestamp not null,
  end_at timestamp not null,
  state text not null default 'PENDING'
    check (state in ('PENDING', 'ANSWERED', 'GAP')),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists checkin_windows_user_id_idx on public.checkin_windows (user_id);
create index if not exists checkin_windows_user_updated_idx on public.checkin_windows (user_id, updated_at, id);

alter table public.checkin_windows enable row level security;

create policy "checkin_windows_select_own"
  on public.checkin_windows for select
  using (user_id = auth.uid());

create policy "checkin_windows_insert_own"
  on public.checkin_windows for insert
  with check (user_id = auth.uid());

create policy "checkin_windows_update_own"
  on public.checkin_windows for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "checkin_windows_delete_own"
  on public.checkin_windows for delete
  using (user_id = auth.uid());

drop trigger if exists checkin_windows_touch on public.checkin_windows;
create trigger checkin_windows_touch
  before insert or update on public.checkin_windows
  for each row
  execute function public.touch_row_updated_at();
