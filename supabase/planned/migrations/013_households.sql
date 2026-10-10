-- Stage 2. households and household_members. Stays in supabase/planned/ until promoted.
-- Room v12 uses the same columns in camelCase:
--   households(id, name, createdBy, updatedAt, deletedAt)
--   household_members(householdId, userId, role, joinedAt, updatedAt, deletedAt)
-- Room stores joinedAt as TEXT (LocalDateTime) and updatedAt/deletedAt as INTEGER millis.
-- Depends on 004_calendar_events.sql for public.touch_row_updated_at().
-- No expense splits.

create table if not exists public.households (
  id uuid primary key default gen_random_uuid(),
  name text not null check (char_length(name) between 1 and 300),
  created_by uuid not null default auth.uid() references auth.users (id) on delete cascade,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists households_created_by_updated_idx
  on public.households (created_by, updated_at, id);

-- Composite key, so this table cannot use touch_row_updated_at() (that function looks up `id`).
create table if not exists public.household_members (
  household_id uuid not null references public.households (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  role text not null check (role in ('owner', 'member')),
  joined_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  primary key (household_id, user_id)
);

create index if not exists household_members_user_id_idx
  on public.household_members (user_id);

-- Reads household_members without triggering its RLS, so member policies can call this.
create or replace function public.is_household_member(household_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.household_members m
    where m.household_id = $1
      and m.user_id = auth.uid()
      and m.deleted_at is null
  );
$$;

create or replace function public.is_household_owner(household_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.household_members m
    where m.household_id = $1
      and m.user_id = auth.uid()
      and m.role = 'owner'
      and m.deleted_at is null
  );
$$;

revoke all on function public.is_household_member(uuid) from public, anon, authenticated;
revoke all on function public.is_household_owner(uuid) from public, anon, authenticated;
grant execute on function public.is_household_member(uuid) to authenticated;
grant execute on function public.is_household_owner(uuid) to authenticated;

alter table public.households enable row level security;

create policy "households_select_own"
  on public.households for select
  using (created_by = auth.uid() or public.is_household_member(id));

create policy "households_insert_own"
  on public.households for insert
  with check (created_by = auth.uid());

create policy "households_update_own"
  on public.households for update
  using (created_by = auth.uid())
  with check (created_by = auth.uid());

create policy "households_delete_own"
  on public.households for delete
  using (created_by = auth.uid());

drop trigger if exists households_touch on public.households;
create trigger households_touch
  before insert or update on public.households
  for each row
  execute function public.touch_row_updated_at();

alter table public.household_members enable row level security;

create policy "household_members_select_own"
  on public.household_members for select
  using (user_id = auth.uid() or public.is_household_member(household_id));

create policy "household_members_insert_own"
  on public.household_members for insert
  with check (
    (
      user_id = auth.uid()
      and role = 'owner'
      and exists (
        select 1 from public.households h
        where h.id = household_id
          and h.created_by = auth.uid()
          and h.deleted_at is null
      )
    )
    or (
      public.is_household_owner(household_id)
      and role = 'member'
    )
  );

create policy "household_members_update_own"
  on public.household_members for update
  using (user_id = auth.uid() or public.is_household_owner(household_id))
  with check (
    public.is_household_owner(household_id)
    or (user_id = auth.uid() and role = 'member')
  );

create policy "household_members_delete_own"
  on public.household_members for delete
  using (user_id = auth.uid() or public.is_household_owner(household_id));

create or replace function public.touch_household_member_updated_at()
returns trigger
language plpgsql
as $$
declare
  stored_at timestamptz;
begin
  if tg_op = 'INSERT' then
    select updated_at into stored_at
    from public.household_members
    where household_id = new.household_id and user_id = new.user_id;
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

drop trigger if exists household_members_touch on public.household_members;
create trigger household_members_touch
  before insert or update on public.household_members
  for each row
  execute function public.touch_household_member_updated_at();
