-- Stage 3. people, interactions, commitments, memories.
-- Stays in supabase/planned/ until promoted.
-- Room v15 uses the same columns in camelCase. No memory_chunks table and no vectors.
--   people(id, userId, name, org, role, notes, visibility, updatedAt, deletedAt)
--   interactions(id, userId, personId, occurredOn, where, summary, sourceId, updatedAt, deletedAt)
--   commitments(id, userId, personId, eventId, text, dueOn, status, updatedAt, deletedAt)
--   memories(id, userId, personId, text, kind, status, sourceType, sourceId, visibility, updatedAt, deletedAt)
-- People and memories stay private: owner RLS only, visibility defaults to private.
-- An interaction requires a person. A commitment may omit person_id.
-- due_on stays null when there is no date. It has no default.
-- Depends on 004_calendar_events.sql for public.touch_row_updated_at().

create table if not exists public.people (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name text not null check (char_length(name) between 1 and 300),
  org text check (org is null or char_length(org) between 1 and 300),
  role text check (role is null or char_length(role) between 1 and 200),
  notes text not null default '' check (char_length(notes) <= 20000),
  visibility text not null default 'private'
    check (visibility in ('private', 'household')),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists people_user_updated_idx
  on public.people (user_id, updated_at, id);

create table if not exists public.interactions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  person_id uuid not null references public.people (id) on delete no action,
  occurred_on date not null,
  "where" text check ("where" is null or char_length("where") between 1 and 300),
  summary text not null check (char_length(summary) between 1 and 20000),
  source_id text check (source_id is null or char_length(source_id) between 1 and 200),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists interactions_user_updated_idx
  on public.interactions (user_id, updated_at, id);

create index if not exists interactions_person_id_idx
  on public.interactions (person_id);

create table if not exists public.commitments (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  person_id uuid references public.people (id) on delete no action,
  event_id text check (event_id is null or char_length(event_id) between 1 and 200),
  text text not null check (char_length(text) between 1 and 20000),
  due_on date,
  status text not null check (char_length(status) between 1 and 100),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists commitments_user_updated_idx
  on public.commitments (user_id, updated_at, id);

create index if not exists commitments_person_id_idx
  on public.commitments (person_id);

create table if not exists public.memories (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  person_id uuid not null references public.people (id) on delete no action,
  text text not null check (char_length(text) between 1 and 20000),
  kind text not null check (kind in ('fact', 'preference', 'goal')),
  status text not null check (status in ('confirmed', 'inferred')),
  source_type text not null check (char_length(source_type) between 1 and 100),
  source_id text check (source_id is null or char_length(source_id) between 1 and 200),
  visibility text not null default 'private'
    check (visibility in ('private', 'household')),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists memories_user_updated_idx
  on public.memories (user_id, updated_at, id);

create index if not exists memories_person_id_idx
  on public.memories (person_id);

alter table public.people enable row level security;

create policy "people_select_own"
  on public.people for select
  using (user_id = auth.uid());

create policy "people_insert_own"
  on public.people for insert
  with check (user_id = auth.uid());

create policy "people_update_own"
  on public.people for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "people_delete_own"
  on public.people for delete
  using (user_id = auth.uid());

drop trigger if exists people_touch on public.people;
create trigger people_touch
  before insert or update on public.people
  for each row
  execute function public.touch_row_updated_at();

alter table public.interactions enable row level security;

create policy "interactions_select_own"
  on public.interactions for select
  using (user_id = auth.uid());

create policy "interactions_insert_own"
  on public.interactions for insert
  with check (user_id = auth.uid());

create policy "interactions_update_own"
  on public.interactions for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "interactions_delete_own"
  on public.interactions for delete
  using (user_id = auth.uid());

drop trigger if exists interactions_touch on public.interactions;
create trigger interactions_touch
  before insert or update on public.interactions
  for each row
  execute function public.touch_row_updated_at();

alter table public.commitments enable row level security;

create policy "commitments_select_own"
  on public.commitments for select
  using (user_id = auth.uid());

create policy "commitments_insert_own"
  on public.commitments for insert
  with check (user_id = auth.uid());

create policy "commitments_update_own"
  on public.commitments for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "commitments_delete_own"
  on public.commitments for delete
  using (user_id = auth.uid());

drop trigger if exists commitments_touch on public.commitments;
create trigger commitments_touch
  before insert or update on public.commitments
  for each row
  execute function public.touch_row_updated_at();

alter table public.memories enable row level security;

create policy "memories_select_own"
  on public.memories for select
  using (user_id = auth.uid());

create policy "memories_insert_own"
  on public.memories for insert
  with check (user_id = auth.uid());

create policy "memories_update_own"
  on public.memories for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "memories_delete_own"
  on public.memories for delete
  using (user_id = auth.uid());

drop trigger if exists memories_touch on public.memories;
create trigger memories_touch
  before insert or update on public.memories
  for each row
  execute function public.touch_row_updated_at();
