-- Appointments are calendar_events rows of kind APPOINTMENT (docs/life-os/app-design.md section 9.4).
-- Not a new table. Adds the kind, a person link and a status.
-- Apply after 016_people_memory.sql (people). Then set APPOINTMENT_SYNC_READY = true in SyncRepository.
-- The local job link (job_applications.id, a device Long) is not synced.

alter table public.calendar_events
  drop constraint if exists calendar_events_kind_check;

alter table public.calendar_events
  add constraint calendar_events_kind_check
  check (kind in ('EVENT', 'TASK', 'EXAM', 'CLASS', 'ROUTINE', 'ALARM', 'PERSONAL', 'APPOINTMENT'));

alter table public.calendar_events
  add column if not exists person_id uuid references public.people (id) on delete set null,
  add column if not exists status text
    check (status is null or status in ('UPCOMING', 'DONE', 'CANCELLED'));

-- Only appointments carry a status.
alter table public.calendar_events
  drop constraint if exists calendar_events_status_kind_check;
alter table public.calendar_events
  add constraint calendar_events_status_kind_check
  check (status is null or kind = 'APPOINTMENT');

create index if not exists calendar_events_user_person_idx
  on public.calendar_events (user_id, person_id)
  where person_id is not null;
