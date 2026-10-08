-- Spend speculations, daily spend guide, and leave-by rules.
-- Apply after 004_calendar_events.sql (needs public.touch_row_updated_at()).
--
-- leave_rules.ref_local_id is the device's calendar_events local id. It is not a
-- foreign key: each device keeps its own Room ids, and the app writes this long back on pull.
-- spend_guide_days is keyed locally by date; the sync key is still id (uuid).

-- ─── spend_speculations ──────────────────────────────────────────────────────

create table if not exists public.spend_speculations (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  label text not null default '' check (char_length(label) <= 500),
  amount double precision not null default 0,
  direction text not null default 'EXPENSE' check (direction in ('EXPENSE', 'INCOME')),
  expected_date date not null,
  confidence text not null default 'MEDIUM' check (confidence in ('LOW', 'MEDIUM', 'HIGH')),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists spend_speculations_user_id_idx on public.spend_speculations (user_id);
create index if not exists spend_speculations_user_updated_idx
  on public.spend_speculations (user_id, updated_at, id);

alter table public.spend_speculations enable row level security;

create policy "spend_speculations_select_own"
  on public.spend_speculations for select
  using (user_id = auth.uid());

create policy "spend_speculations_insert_own"
  on public.spend_speculations for insert
  with check (user_id = auth.uid());

create policy "spend_speculations_update_own"
  on public.spend_speculations for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "spend_speculations_delete_own"
  on public.spend_speculations for delete
  using (user_id = auth.uid());

drop trigger if exists spend_speculations_touch on public.spend_speculations;
create trigger spend_speculations_touch
  before insert or update on public.spend_speculations
  for each row
  execute function public.touch_row_updated_at();

-- ─── spend_guide_days ────────────────────────────────────────────────────────

create table if not exists public.spend_guide_days (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  date date not null,
  guide_amount double precision not null default 0,
  spent double precision not null default 0,
  buffer double precision not null default 0,
  margin_used double precision not null default 0,
  updated_at timestamptz not null default now()
);

create index if not exists spend_guide_days_user_id_idx on public.spend_guide_days (user_id);
create index if not exists spend_guide_days_user_date_idx on public.spend_guide_days (user_id, date);
create index if not exists spend_guide_days_user_updated_idx
  on public.spend_guide_days (user_id, updated_at, id);

alter table public.spend_guide_days enable row level security;

create policy "spend_guide_days_select_own"
  on public.spend_guide_days for select
  using (user_id = auth.uid());

create policy "spend_guide_days_insert_own"
  on public.spend_guide_days for insert
  with check (user_id = auth.uid());

create policy "spend_guide_days_update_own"
  on public.spend_guide_days for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "spend_guide_days_delete_own"
  on public.spend_guide_days for delete
  using (user_id = auth.uid());

drop trigger if exists spend_guide_days_touch on public.spend_guide_days;
create trigger spend_guide_days_touch
  before insert or update on public.spend_guide_days
  for each row
  execute function public.touch_row_updated_at();

-- ─── leave_rules ─────────────────────────────────────────────────────────────

create table if not exists public.leave_rules (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  ref_type text not null default 'CALENDAR_EVENT' check (ref_type in ('CALENDAR_EVENT')),
  ref_local_id bigint not null,
  place_label text not null default '' check (char_length(place_label) <= 500),
  lat double precision,
  lng double precision,
  travel_minutes int not null default 15 check (travel_minutes >= 0),
  buffer_minutes int not null default 5 check (buffer_minutes >= 0),
  enabled boolean not null default true,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists leave_rules_user_id_idx on public.leave_rules (user_id);
create index if not exists leave_rules_user_updated_idx on public.leave_rules (user_id, updated_at, id);

alter table public.leave_rules enable row level security;

create policy "leave_rules_select_own"
  on public.leave_rules for select
  using (user_id = auth.uid());

create policy "leave_rules_insert_own"
  on public.leave_rules for insert
  with check (user_id = auth.uid());

create policy "leave_rules_update_own"
  on public.leave_rules for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "leave_rules_delete_own"
  on public.leave_rules for delete
  using (user_id = auth.uid());

drop trigger if exists leave_rules_touch on public.leave_rules;
create trigger leave_rules_touch
  before insert or update on public.leave_rules
  for each row
  execute function public.touch_row_updated_at();
