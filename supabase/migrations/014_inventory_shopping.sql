-- Stage 1. items, shopping_lists, shopping_items. Stays in supabase/planned/ until promoted.
-- Room v13 uses the same columns in camelCase. Location is a text column on items.
-- There is no item_locations table.
--   items(id, ownerUserId, householdId, name, category, quantity, unit, location,
--         expiresOn, kind, sourceType, sourceId, confidence, updatedAt, deletedAt)
--   shopping_lists(id, householdId, userId, name, updatedAt, deletedAt)
--   shopping_items(id, listId, itemName, qty, addedBy, boughtBy, boughtAt, status,
--                  updatedAt, deletedAt)
-- quantity and qty are null when unknown. They have no default of zero.
-- household_id is a nullable id. Apply after 013_households.sql.
-- Depends on 004_calendar_events.sql for public.touch_row_updated_at().

create table if not exists public.items (
  id uuid primary key default gen_random_uuid(),
  owner_user_id uuid default auth.uid() references auth.users (id) on delete cascade,
  household_id uuid references public.households (id) on delete set null,
  name text not null check (char_length(name) between 1 and 300),
  category text not null default '' check (char_length(category) <= 200),
  quantity double precision,
  unit text not null default '' check (char_length(unit) <= 64),
  location text not null default '' check (char_length(location) <= 300),
  expires_on date,
  kind text not null default 'food'
    check (kind in ('food', 'supply', 'clothing', 'other')),
  source_type text not null default 'manual' check (char_length(source_type) <= 100),
  source_id text check (source_id is null or char_length(source_id) <= 200),
  confidence text not null default 'confirmed'
    check (confidence in ('confirmed', 'inferred')),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists items_owner_updated_idx
  on public.items (owner_user_id, updated_at, id);

create table if not exists public.shopping_lists (
  id uuid primary key default gen_random_uuid(),
  household_id uuid references public.households (id) on delete set null,
  user_id uuid default auth.uid() references auth.users (id) on delete cascade,
  name text not null check (char_length(name) between 1 and 300),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists shopping_lists_user_updated_idx
  on public.shopping_lists (user_id, updated_at, id);

create table if not exists public.shopping_items (
  id uuid primary key default gen_random_uuid(),
  list_id uuid not null references public.shopping_lists (id) on delete cascade,
  item_name text not null check (char_length(item_name) between 1 and 300),
  qty double precision,
  added_by uuid references auth.users (id) on delete set null,
  bought_by uuid references auth.users (id) on delete set null,
  bought_at timestamptz,
  status text not null default 'open' check (status in ('open', 'checked')),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists shopping_items_list_id_idx
  on public.shopping_items (list_id);

alter table public.items enable row level security;

create policy "items_select_own"
  on public.items for select
  using (owner_user_id = auth.uid());

create policy "items_insert_own"
  on public.items for insert
  with check (owner_user_id = auth.uid());

create policy "items_update_own"
  on public.items for update
  using (owner_user_id = auth.uid())
  with check (owner_user_id = auth.uid());

create policy "items_delete_own"
  on public.items for delete
  using (owner_user_id = auth.uid());

drop trigger if exists items_touch on public.items;
create trigger items_touch
  before insert or update on public.items
  for each row
  execute function public.touch_row_updated_at();

alter table public.shopping_lists enable row level security;

create policy "shopping_lists_select_own"
  on public.shopping_lists for select
  using (user_id = auth.uid());

create policy "shopping_lists_insert_own"
  on public.shopping_lists for insert
  with check (user_id = auth.uid());

create policy "shopping_lists_update_own"
  on public.shopping_lists for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "shopping_lists_delete_own"
  on public.shopping_lists for delete
  using (user_id = auth.uid());

drop trigger if exists shopping_lists_touch on public.shopping_lists;
create trigger shopping_lists_touch
  before insert or update on public.shopping_lists
  for each row
  execute function public.touch_row_updated_at();

alter table public.shopping_items enable row level security;

create policy "shopping_items_select_own"
  on public.shopping_items for select
  using (
    exists (
      select 1 from public.shopping_lists l
      where l.id = shopping_items.list_id
        and l.user_id = auth.uid()
    )
  );

create policy "shopping_items_insert_own"
  on public.shopping_items for insert
  with check (
    exists (
      select 1 from public.shopping_lists l
      where l.id = shopping_items.list_id
        and l.user_id = auth.uid()
    )
  );

create policy "shopping_items_update_own"
  on public.shopping_items for update
  using (
    exists (
      select 1 from public.shopping_lists l
      where l.id = shopping_items.list_id
        and l.user_id = auth.uid()
    )
  )
  with check (
    exists (
      select 1 from public.shopping_lists l
      where l.id = shopping_items.list_id
        and l.user_id = auth.uid()
    )
  );

create policy "shopping_items_delete_own"
  on public.shopping_items for delete
  using (
    exists (
      select 1 from public.shopping_lists l
      where l.id = shopping_items.list_id
        and l.user_id = auth.uid()
    )
  );

drop trigger if exists shopping_items_touch on public.shopping_items;
create trigger shopping_items_touch
  before insert or update on public.shopping_items
  for each row
  execute function public.touch_row_updated_at();
