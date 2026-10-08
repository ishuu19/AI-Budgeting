-- LedgerAI Phase 2/5/6 foundation: tasks, reminders (max 10/task), routines stub, alarms, notes + RLS
-- Soft-delete + updated_at for sync. Apply after 001_init.sql.
--
-- NOTE: The remote PostgREST table for task reminders is named "reminders" (not task_reminders).
--       The Android SyncRepository + PostgrestApi use path "reminders" for this table.
--       Local Room table is "task_reminders".

-- ─── tasks ───────────────────────────────────────────────────────────────────

create table if not exists public.tasks (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  title text not null,
  notes text not null default '',
  due_at timestamptz,
  is_completed boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists tasks_user_id_idx on public.tasks (user_id);
create index if not exists tasks_user_due_idx on public.tasks (user_id, due_at);

alter table public.tasks enable row level security;

create policy "tasks_select_own"
  on public.tasks for select
  using (user_id = auth.uid());

create policy "tasks_insert_own"
  on public.tasks for insert
  with check (user_id = auth.uid());

create policy "tasks_update_own"
  on public.tasks for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "tasks_delete_own"
  on public.tasks for delete
  using (user_id = auth.uid());

-- ─── reminders (max 10 active per task) ──────────────────────────────────────

create table if not exists public.reminders (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  task_id uuid not null references public.tasks (id) on delete cascade,
  label text not null default '',
  remind_at timestamptz not null,
  offset_minutes int,
  is_enabled boolean not null default true,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists reminders_user_id_idx on public.reminders (user_id);
create index if not exists reminders_task_id_idx on public.reminders (task_id);
create index if not exists reminders_task_active_idx
  on public.reminders (task_id)
  where deleted_at is null;

alter table public.reminders enable row level security;

create policy "reminders_select_own"
  on public.reminders for select
  using (user_id = auth.uid());

create policy "reminders_insert_own"
  on public.reminders for insert
  with check (
    user_id = auth.uid()
    and exists (
      select 1 from public.tasks t
      where t.id = task_id and t.user_id = auth.uid() and t.deleted_at is null
    )
  );

create policy "reminders_update_own"
  on public.reminders for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "reminders_delete_own"
  on public.reminders for delete
  using (user_id = auth.uid());

-- Enforce at most 10 non-deleted reminders per task (insert + revive via update).
create or replace function public.enforce_max_reminders_per_task()
returns trigger
language plpgsql
as $$
declare
  active_count int;
begin
  if new.deleted_at is not null then
    return new;
  end if;

  select count(*)::int into active_count
  from public.reminders r
  where r.task_id = new.task_id
    and r.deleted_at is null
    and r.id is distinct from new.id;

  if active_count >= 10 then
    raise exception 'Maximum 10 reminders per task'
      using errcode = 'check_violation';
  end if;

  return new;
end;
$$;

drop trigger if exists reminders_max_10_per_task on public.reminders;
create trigger reminders_max_10_per_task
  before insert or update of task_id, deleted_at on public.reminders
  for each row
  execute function public.enforce_max_reminders_per_task();

-- ─── routines (Phase 5 stub) ─────────────────────────────────────────────────

create table if not exists public.routines (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  title text not null,
  notes text not null default '',
  repeat_rule text not null default '',
  is_active boolean not null default true,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists routines_user_id_idx on public.routines (user_id);

alter table public.routines enable row level security;

create policy "routines_select_own"
  on public.routines for select
  using (user_id = auth.uid());

create policy "routines_insert_own"
  on public.routines for insert
  with check (user_id = auth.uid());

create policy "routines_update_own"
  on public.routines for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "routines_delete_own"
  on public.routines for delete
  using (user_id = auth.uid());

-- ─── alarms ──────────────────────────────────────────────────────────────────

create table if not exists public.alarms (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  label text not null default 'Alarm',
  time time not null,
  is_enabled boolean not null default true,
  -- Bitmask Sun=1 … Sat=64; 0 = one-shot
  repeat_days int not null default 0,
  tone_uri text,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists alarms_user_id_idx on public.alarms (user_id);

alter table public.alarms enable row level security;

create policy "alarms_select_own"
  on public.alarms for select
  using (user_id = auth.uid());

create policy "alarms_insert_own"
  on public.alarms for insert
  with check (user_id = auth.uid());

create policy "alarms_update_own"
  on public.alarms for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "alarms_delete_own"
  on public.alarms for delete
  using (user_id = auth.uid());

-- ─── notes ───────────────────────────────────────────────────────────────────

create table if not exists public.notes (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  title text not null,
  body text not null default '',
  tags text not null default '',
  created_at timestamptz not null default now(),
  edited_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists notes_user_id_idx on public.notes (user_id);
create index if not exists notes_user_edited_idx on public.notes (user_id, edited_at desc);

alter table public.notes enable row level security;

create policy "notes_select_own"
  on public.notes for select
  using (user_id = auth.uid());

create policy "notes_insert_own"
  on public.notes for insert
  with check (user_id = auth.uid());

create policy "notes_update_own"
  on public.notes for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "notes_delete_own"
  on public.notes for delete
  using (user_id = auth.uid());
