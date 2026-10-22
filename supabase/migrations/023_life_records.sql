-- Rebuild records: folders, birthdays, trips, achievements, wishes (docs/life-os/app-design.md section 16).
-- Apply after 019_media.sql and 022_calendar_appointments.sql. Uses public.touch_row_updated_at() from 004.
-- Owner-only RLS on every table. Passwords, app usage and reminders have no server table on purpose:
-- they never leave the phone. Locked and device-only folders are never uploaded, so those flags
-- have no column here.
-- Existing notes need no data move: notes.folder_id null means the Notes folder.

-- Vault folders -------------------------------------------------------------------------------
create table if not exists public.folders (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name text not null check (char_length(name) between 1 and 40),
  parent_id uuid references public.folders (id) on delete set null,
  pinned boolean not null default false,
  sort_order integer not null default 0,
  system_key text check (system_key in ('notes', 'inbox')),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
create unique index if not exists folders_user_system_key
  on public.folders (user_id, system_key) where system_key is not null;
create index if not exists folders_user_updated on public.folders (user_id, updated_at, id);

alter table public.notes
  add column if not exists folder_id uuid references public.folders (id) on delete set null;
create index if not exists notes_user_folder on public.notes (user_id, folder_id);

-- Vault files reuse media_assets (019): a new 'file' kind plus mime and size.
alter table public.media_assets drop constraint if exists media_assets_kind_check;
alter table public.media_assets
  add constraint media_assets_kind_check
  check (kind in ('receipt', 'food', 'clothing', 'person', 'other', 'unknown', 'file'));
alter table public.media_assets
  add column if not exists mime text check (mime is null or char_length(mime) <= 100),
  add column if not exists size_bytes bigint check (size_bytes is null or size_bytes between 0 and 26214400);

-- Birthdays -----------------------------------------------------------------------------------
-- person_id is a plain uuid, not a foreign key: deleting a person keeps the birthday.
create table if not exists public.birthdays (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  person_id uuid not null,
  month smallint not null check (month between 1 and 12),
  day smallint not null check (day between 1 and 31),
  birth_year smallint check (birth_year is null or birth_year between 1900 and 2100),
  celebrate_minute smallint not null default 0 check (celebrate_minute between 0 and 1439),
  note text not null default '' check (char_length(note) <= 500),
  r_1mo boolean not null default true,
  r_2w boolean not null default true,
  r_1w boolean not null default true,
  r_3d boolean not null default true,
  r_1d boolean not null default true,
  r_5h boolean not null default true,
  r_30m boolean not null default true,
  r_10m boolean not null default true,
  r_at boolean not null default true,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
create index if not exists birthdays_user_updated on public.birthdays (user_id, updated_at, id);
create index if not exists birthdays_user_person on public.birthdays (user_id, person_id);

-- Trips ---------------------------------------------------------------------------------------
create table if not exists public.trips (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name text not null check (char_length(name) between 1 and 80),
  place text not null check (char_length(place) between 1 and 120),
  start_date date not null,
  end_date date not null,
  notes text not null default '' check (char_length(notes) <= 2000),
  r_1w boolean not null default true,
  r_1d boolean not null default true,
  visibility text not null default 'private' check (visibility in ('private', 'household')),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  check (end_date >= start_date)
);
create index if not exists trips_user_updated on public.trips (user_id, updated_at, id);
create index if not exists trips_user_start on public.trips (user_id, start_date);

-- Achievements --------------------------------------------------------------------------------
-- No points, streak or reminder columns, by design. The source task id is a device row id and is not synced.
create table if not exists public.achievements (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  title text not null check (char_length(title) between 1 and 120),
  happened_on date not null,
  note text not null default '' check (char_length(note) <= 1000),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
create index if not exists achievements_user_updated on public.achievements (user_id, updated_at, id);
create index if not exists achievements_user_date on public.achievements (user_id, happened_on desc);

-- Wishes --------------------------------------------------------------------------------------
-- amount stays null when the price is unknown. goal_id and transaction_id are plain uuids.
create table if not exists public.wishes (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name text not null check (char_length(name) between 1 and 120),
  amount numeric(14, 2) check (amount is null or amount >= 0),
  priority smallint not null default 2 check (priority between 1 and 3),
  wanted_by text check (wanted_by is null or wanted_by ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
  link text check (link is null or char_length(link) <= 2000),
  note text not null default '' check (char_length(note) <= 1000),
  status text not null default 'WANT' check (status in ('WANT', 'SAVING', 'BOUGHT', 'DROPPED')),
  goal_id uuid,
  transaction_id uuid,
  visibility text not null default 'private' check (visibility in ('private', 'household')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
create index if not exists wishes_user_updated on public.wishes (user_id, updated_at, id);
create index if not exists wishes_user_status on public.wishes (user_id, status);

-- RLS and updated_at --------------------------------------------------------------------------
do $$
declare t text;
begin
  foreach t in array array['folders', 'birthdays', 'trips', 'achievements', 'wishes'] loop
    execute format('alter table public.%I enable row level security', t);
    execute format('drop policy if exists %I on public.%I', t || '_owner', t);
    execute format(
      'create policy %I on public.%I for all using (user_id = auth.uid()) with check (user_id = auth.uid())',
      t || '_owner', t);
    execute format('drop trigger if exists %I on public.%I', t || '_touch', t);
    execute format(
      'create trigger %I before insert or update on public.%I for each row execute function public.touch_row_updated_at()',
      t || '_touch', t);
  end loop;
end $$;
