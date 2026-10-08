-- Study plans and the timed blocks generated from them.
-- Apply after 004_calendar_events.sql. Reuses public.touch_row_updated_at().
--
-- start_at and end_at are wall-clock times in timestamp without time zone.
-- source_plan_id and habit_id are nullable uuids. habit_id is filled by habit sync.

-- ─── study_plans ─────────────────────────────────────────────────────────────

create table if not exists public.study_plans (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  topic text not null default '',
  hours_total double precision not null default 0,
  deadline date,
  session_len_minutes int not null default 50,
  status text not null default 'active',
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists study_plans_user_id_idx on public.study_plans (user_id);
create index if not exists study_plans_user_updated_idx on public.study_plans (user_id, updated_at, id);

alter table public.study_plans enable row level security;

create policy "study_plans_select_own"
  on public.study_plans for select
  using (user_id = auth.uid());

create policy "study_plans_insert_own"
  on public.study_plans for insert
  with check (user_id = auth.uid());

create policy "study_plans_update_own"
  on public.study_plans for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "study_plans_delete_own"
  on public.study_plans for delete
  using (user_id = auth.uid());

drop trigger if exists study_plans_touch on public.study_plans;
create trigger study_plans_touch
  before insert or update on public.study_plans
  for each row
  execute function public.touch_row_updated_at();

-- ─── plan_blocks ─────────────────────────────────────────────────────────────

create table if not exists public.plan_blocks (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  kind text not null default 'STUDY'
    check (kind in ('STUDY', 'HABIT')),
  title text not null default '',
  topic text not null default '',
  start_at timestamp not null,
  end_at timestamp not null,
  status text not null default 'SCHEDULED'
    check (status in ('SCHEDULED', 'IN_PROGRESS', 'DONE', 'SKIPPED', 'MOVED')),
  source_plan_id uuid references public.study_plans (id) on delete set null,
  habit_id uuid,
  actual_minutes int,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists plan_blocks_user_id_idx on public.plan_blocks (user_id);
create index if not exists plan_blocks_user_updated_idx on public.plan_blocks (user_id, updated_at, id);
create index if not exists plan_blocks_source_plan_idx on public.plan_blocks (source_plan_id);

alter table public.plan_blocks enable row level security;

create policy "plan_blocks_select_own"
  on public.plan_blocks for select
  using (user_id = auth.uid());

create policy "plan_blocks_insert_own"
  on public.plan_blocks for insert
  with check (user_id = auth.uid());

create policy "plan_blocks_update_own"
  on public.plan_blocks for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "plan_blocks_delete_own"
  on public.plan_blocks for delete
  using (user_id = auth.uid());

drop trigger if exists plan_blocks_touch on public.plan_blocks;
create trigger plan_blocks_touch
  before insert or update on public.plan_blocks
  for each row
  execute function public.touch_row_updated_at();
