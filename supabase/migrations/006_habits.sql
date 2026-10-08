-- LedgerAI habits and daily habit logs.
-- Apply after 004_calendar_events.sql (uses public.touch_row_updated_at(); do not recreate it).
--
-- start_time is a clock time. date is a calendar date. Enums are the app's .name strings.

-- ─── habits ──────────────────────────────────────────────────────────────────

create table if not exists public.habits (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  title text not null default '' check (char_length(title) <= 500),
  category text not null default 'CUSTOM'
    check (category in ('EXERCISE', 'READING', 'PRAYER', 'CUSTOM')),
  -- Bitmask Sun=1 ... Sat=64; 0 = no days selected.
  days_mask int not null default 0 check (days_mask between 0 and 127),
  start_time time not null,
  duration_minutes int not null default 30 check (duration_minutes >= 1),
  nudge_enabled boolean not null default true,
  quiet_override boolean not null default false,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists habits_user_id_idx on public.habits (user_id);
create index if not exists habits_user_updated_idx on public.habits (user_id, updated_at, id);

alter table public.habits enable row level security;

create policy "habits_select_own"
  on public.habits for select
  using (user_id = auth.uid());

create policy "habits_insert_own"
  on public.habits for insert
  with check (user_id = auth.uid());

create policy "habits_update_own"
  on public.habits for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "habits_delete_own"
  on public.habits for delete
  using (user_id = auth.uid());

-- ─── habit_logs ──────────────────────────────────────────────────────────────

create table if not exists public.habit_logs (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  habit_id uuid not null references public.habits (id) on delete cascade,
  date date not null,
  outcome text not null default 'DONE'
    check (outcome in ('DONE', 'SKIPPED', 'MISSED')),
  minutes int check (minutes is null or minutes >= 0),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists habit_logs_user_id_idx on public.habit_logs (user_id);
create index if not exists habit_logs_habit_id_idx on public.habit_logs (habit_id);
create index if not exists habit_logs_user_updated_idx on public.habit_logs (user_id, updated_at, id);

alter table public.habit_logs enable row level security;

create policy "habit_logs_select_own"
  on public.habit_logs for select
  using (user_id = auth.uid());

create policy "habit_logs_insert_own"
  on public.habit_logs for insert
  with check (
    user_id = auth.uid()
    and exists (
      select 1 from public.habits h
      where h.id = habit_id and h.user_id = auth.uid()
    )
  );

create policy "habit_logs_update_own"
  on public.habit_logs for update
  using (user_id = auth.uid())
  with check (
    user_id = auth.uid()
    and exists (
      select 1 from public.habits h
      where h.id = habit_id and h.user_id = auth.uid()
    )
  );

create policy "habit_logs_delete_own"
  on public.habit_logs for delete
  using (user_id = auth.uid());

drop trigger if exists habits_touch on public.habits;
create trigger habits_touch
  before insert or update on public.habits
  for each row
  execute function public.touch_row_updated_at();

drop trigger if exists habit_logs_touch on public.habit_logs;
create trigger habit_logs_touch
  before insert or update on public.habit_logs
  for each row
  execute function public.touch_row_updated_at();
