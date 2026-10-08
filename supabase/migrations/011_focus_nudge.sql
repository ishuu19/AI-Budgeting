-- Focus sessions and nudge proposals.
-- block_local_id and note_local_id are device Room primary keys, not foreign keys.
-- Apply after 004_calendar_events.sql, which defines public.touch_row_updated_at().
-- Location points and visits stay on the device.

create table if not exists public.focus_sessions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  block_local_id bigint not null,
  started_at timestamp not null,
  ended_at timestamp,
  preset text not null default 'deep' check (char_length(preset) <= 40),
  updated_at timestamptz not null default now()
);

create index if not exists focus_sessions_user_id_idx on public.focus_sessions (user_id);
create index if not exists focus_sessions_user_updated_idx on public.focus_sessions (user_id, updated_at, id);

alter table public.focus_sessions enable row level security;

create policy "focus_sessions_select_own"
  on public.focus_sessions for select
  using (user_id = auth.uid());

create policy "focus_sessions_insert_own"
  on public.focus_sessions for insert
  with check (user_id = auth.uid());

create policy "focus_sessions_update_own"
  on public.focus_sessions for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "focus_sessions_delete_own"
  on public.focus_sessions for delete
  using (user_id = auth.uid());

drop trigger if exists focus_sessions_touch on public.focus_sessions;
create trigger focus_sessions_touch
  before insert or update on public.focus_sessions
  for each row
  execute function public.touch_row_updated_at();

create table if not exists public.nudge_proposals (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  note_local_id bigint not null,
  message text not null default '' check (char_length(message) <= 2000),
  suggested_at timestamp not null,
  reason text not null default '' check (char_length(reason) <= 2000),
  state text not null default 'PENDING'
    check (state in ('PENDING', 'ACCEPTED', 'DISMISSED')),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists nudge_proposals_user_id_idx on public.nudge_proposals (user_id);
create index if not exists nudge_proposals_user_updated_idx on public.nudge_proposals (user_id, updated_at, id);

alter table public.nudge_proposals enable row level security;

create policy "nudge_proposals_select_own"
  on public.nudge_proposals for select
  using (user_id = auth.uid());

create policy "nudge_proposals_insert_own"
  on public.nudge_proposals for insert
  with check (user_id = auth.uid());

create policy "nudge_proposals_update_own"
  on public.nudge_proposals for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "nudge_proposals_delete_own"
  on public.nudge_proposals for delete
  using (user_id = auth.uid());

drop trigger if exists nudge_proposals_touch on public.nudge_proposals;
create trigger nudge_proposals_touch
  before insert or update on public.nudge_proposals
  for each row
  execute function public.touch_row_updated_at();
