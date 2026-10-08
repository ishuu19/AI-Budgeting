-- LedgerAI calendar merge: tasks, exams, classes, routines, reminders and alarms are all calendar events.
-- Apply after 003_quotes_seen.sql.
--
-- Numbering: the owner brief asked for 003_calendar_events.sql, but 003_quotes_seen.sql already exists and
-- Supabase keys migrations by their numeric prefix, so this file is 004.
--
-- The legacy tables (tasks, reminders, routines, alarms) are left in place and are no longer synced by the app.
-- Each device migrates its own local rows into calendar events and pushes them as new rows, so there is
-- deliberately no server-side copy (it would create duplicates on every device).
--
-- Times (start_at, end_at, completed_at, remind_at) are wall-clock "floating" times in
-- `timestamp without time zone`, exactly what the app shows. They are not instants.

-- ─── calendar_events ─────────────────────────────────────────────────────────

create table if not exists public.calendar_events (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  title text not null default '' check (char_length(title) <= 500),
  notes text not null default '' check (char_length(notes) <= 20000),
  location text not null default '' check (char_length(location) <= 500),
  links text not null default '' check (char_length(links) <= 4000),
  start_at timestamp not null,
  end_at timestamp not null,
  all_day boolean not null default false,
  -- false only for undated tasks (start_at then holds the creation day for ordering)
  has_date boolean not null default true,
  kind text not null default 'EVENT'
    check (kind in ('EVENT', 'TASK', 'EXAM', 'CLASS', 'ROUTINE', 'ALARM', 'PERSONAL')),
  is_completed boolean not null default false,
  completed_at timestamp,
  -- alarm on/off, routine or class active flag
  is_enabled boolean not null default true,
  alarm_tone_uri text,
  -- Bitmask Sun=1 ... Sat=64; 0 = one-shot. Only meaningful for ALARM.
  alarm_repeat_days int not null default 0 check (alarm_repeat_days between 0 and 127),
  recurrence_frequency text not null default 'NONE'
    check (recurrence_frequency in ('NONE', 'DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY', 'SPECIFIC_DATES')),
  recurrence_interval int not null default 1 check (recurrence_interval >= 1),
  -- comma separated ISO weekdays, Mon=1 ... Sun=7
  recurrence_weekdays text not null default '',
  -- JSON array of dates for SPECIFIC_DATES
  specific_dates text not null default '',
  recurrence_until date,
  -- comma separated dates skipped from a repeating series (the exceptions)
  excluded_dates text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists calendar_events_user_id_idx on public.calendar_events (user_id);
create index if not exists calendar_events_user_start_idx on public.calendar_events (user_id, start_at);
create index if not exists calendar_events_user_kind_idx on public.calendar_events (user_id, kind);
create index if not exists calendar_events_user_updated_idx on public.calendar_events (user_id, updated_at, id);

alter table public.calendar_events enable row level security;

create policy "calendar_events_select_own"
  on public.calendar_events for select
  using (user_id = auth.uid());

create policy "calendar_events_insert_own"
  on public.calendar_events for insert
  with check (user_id = auth.uid());

create policy "calendar_events_update_own"
  on public.calendar_events for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "calendar_events_delete_own"
  on public.calendar_events for delete
  using (user_id = auth.uid());

-- ─── event_reminders (max 10 active per event) ───────────────────────────────

create table if not exists public.event_reminders (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  event_id uuid not null references public.calendar_events (id) on delete cascade,
  label text not null default '' check (char_length(label) <= 200),
  -- Minutes before each occurrence start (0 = at time). When null, remind_at is a single absolute time.
  offset_minutes int check (offset_minutes is null or offset_minutes between 0 and 525600),
  remind_at timestamp,
  is_enabled boolean not null default true,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  check (offset_minutes is not null or remind_at is not null)
);

create index if not exists event_reminders_user_id_idx on public.event_reminders (user_id);
create index if not exists event_reminders_event_id_idx on public.event_reminders (event_id);
create index if not exists event_reminders_event_active_idx
  on public.event_reminders (event_id)
  where deleted_at is null;
create index if not exists event_reminders_user_updated_idx on public.event_reminders (user_id, updated_at, id);

alter table public.event_reminders enable row level security;

create policy "event_reminders_select_own"
  on public.event_reminders for select
  using (user_id = auth.uid());

create policy "event_reminders_insert_own"
  on public.event_reminders for insert
  with check (
    user_id = auth.uid()
    and exists (
      select 1 from public.calendar_events e
      where e.id = event_id and e.user_id = auth.uid()
    )
  );

-- The event must be owned too, otherwise a reminder could be re-pointed at someone else's event.
create policy "event_reminders_update_own"
  on public.event_reminders for update
  using (user_id = auth.uid())
  with check (
    user_id = auth.uid()
    and exists (
      select 1 from public.calendar_events e
      where e.id = event_id and e.user_id = auth.uid()
    )
  );

create policy "event_reminders_delete_own"
  on public.event_reminders for delete
  using (user_id = auth.uid());

-- ─── server-owned updated_at, with last-write-wins protection ────────────────
-- The server stamps updated_at on every write, so pulls that filter on "updated_at > cursor" can never miss a
-- row a device pushed late. A row that arrives with a client updated_at more than two minutes older than the
-- stored row is ignored (stale device) instead of silently overwriting newer data. The check also runs on the
-- INSERT half of an upsert, because PostgREST upserts are INSERT ... ON CONFLICT DO UPDATE and the stamp below
-- would otherwise hide the client timestamp from the update path. The two minute slack absorbs clock skew.

create or replace function public.touch_row_updated_at()
returns trigger
language plpgsql
as $$
declare
  stored_at timestamptz;
begin
  if tg_op = 'INSERT' then
    execute format('select updated_at from %I.%I where id = $1', tg_table_schema, tg_table_name)
      into stored_at using new.id;
    if stored_at is not null and new.updated_at < stored_at - interval '2 minutes' then
      return null;
    end if;
    new.updated_at := now();
  else
    if new.updated_at < old.updated_at - interval '2 minutes' then
      return null;
    end if;
    new.updated_at := greatest(now(), old.updated_at);
  end if;
  return new;
end;
$$;

drop trigger if exists calendar_events_touch on public.calendar_events;
create trigger calendar_events_touch
  before insert or update on public.calendar_events
  for each row
  execute function public.touch_row_updated_at();

drop trigger if exists event_reminders_touch on public.event_reminders;
create trigger event_reminders_touch
  before insert or update on public.event_reminders
  for each row
  execute function public.touch_row_updated_at();

-- ─── at most 10 active reminders per event ───────────────────────────────────
-- Locks the parent event row first, so two concurrent inserts for the same event are serialized and cannot
-- both see a count of 9. Also checks that the reminder owner owns the event.

create or replace function public.enforce_max_reminders_per_event()
returns trigger
language plpgsql
as $$
declare
  event_owner uuid;
  active_count int;
begin
  select e.user_id into event_owner
  from public.calendar_events e
  where e.id = new.event_id
  for update;

  if event_owner is null then
    raise exception 'Event not found' using errcode = 'foreign_key_violation';
  end if;
  if event_owner is distinct from new.user_id then
    raise exception 'Reminder owner must own the event' using errcode = 'insufficient_privilege';
  end if;

  if new.deleted_at is not null then
    return new;
  end if;

  select count(*)::int into active_count
  from public.event_reminders r
  where r.event_id = new.event_id
    and r.deleted_at is null
    and r.id is distinct from new.id;

  if active_count >= 10 then
    raise exception 'Maximum 10 reminders per event' using errcode = 'check_violation';
  end if;

  return new;
end;
$$;

drop trigger if exists event_reminders_max_10 on public.event_reminders;
create trigger event_reminders_max_10
  before insert or update of event_id, deleted_at, user_id on public.event_reminders
  for each row
  execute function public.enforce_max_reminders_per_event();
