-- Job applications. Status stores the app enum name.
-- Apply after 004_calendar_events.sql, which defines public.touch_row_updated_at().

create table if not exists public.job_applications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  company text not null default '' check (char_length(company) <= 300),
  title text not null default '' check (char_length(title) <= 300),
  url text not null default '' check (char_length(url) <= 2000),
  source text not null default '' check (char_length(source) <= 200),
  status text not null default 'APPLIED'
    check (status in ('APPLIED', 'SCREENING', 'INTERVIEW', 'OFFER', 'REJECTED', 'WITHDRAWN')),
  applied_on date not null,
  follow_up_on date,
  notes text not null default '' check (char_length(notes) <= 20000),
  contact text not null default '' check (char_length(contact) <= 500),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists job_applications_user_id_idx on public.job_applications (user_id);
create index if not exists job_applications_user_updated_idx on public.job_applications (user_id, updated_at, id);

alter table public.job_applications enable row level security;

create policy "job_applications_select_own"
  on public.job_applications for select
  using (user_id = auth.uid());

create policy "job_applications_insert_own"
  on public.job_applications for insert
  with check (user_id = auth.uid());

create policy "job_applications_update_own"
  on public.job_applications for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "job_applications_delete_own"
  on public.job_applications for delete
  using (user_id = auth.uid());

drop trigger if exists job_applications_touch on public.job_applications;
create trigger job_applications_touch
  before insert or update on public.job_applications
  for each row
  execute function public.touch_row_updated_at();
