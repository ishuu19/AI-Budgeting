-- Stage 4. wardrobe_items, outfits. Stays in supabase/planned/ until promoted.
-- Room v17 uses the same columns in camelCase. photo_path stays null when absent.
-- Colors, seasons, and item ids are text, matching Room (no array columns).
--   wardrobe_items(id, userId, name, type, colors, seasons, photoPath,
--                  laundryStatus, updatedAt, deletedAt)
--   outfits(id, userId, occasion, itemIds, wornOn, updatedAt, deletedAt)
-- Private by default: owner RLS only. No vectors and no styling AI.
-- Depends on 004_calendar_events.sql for public.touch_row_updated_at().

create table if not exists public.wardrobe_items (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name text not null check (char_length(name) between 1 and 300),
  type text not null check (char_length(type) between 1 and 100),
  colors text not null check (char_length(colors) between 1 and 2000),
  seasons text not null default '' check (char_length(seasons) <= 2000),
  photo_path text check (photo_path is null or char_length(photo_path) between 1 and 1000),
  laundry_status text not null check (char_length(laundry_status) between 1 and 100),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists wardrobe_items_user_updated_idx
  on public.wardrobe_items (user_id, updated_at, id);

create index if not exists wardrobe_items_updated_at_idx
  on public.wardrobe_items (updated_at);

alter table public.wardrobe_items enable row level security;

create policy "wardrobe_items_select_own"
  on public.wardrobe_items for select
  using (user_id = auth.uid());

create policy "wardrobe_items_insert_own"
  on public.wardrobe_items for insert
  with check (user_id = auth.uid());

create policy "wardrobe_items_update_own"
  on public.wardrobe_items for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "wardrobe_items_delete_own"
  on public.wardrobe_items for delete
  using (user_id = auth.uid());

drop trigger if exists wardrobe_items_touch on public.wardrobe_items;
create trigger wardrobe_items_touch
  before insert or update on public.wardrobe_items
  for each row
  execute function public.touch_row_updated_at();

create table if not exists public.outfits (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  occasion text not null default '' check (char_length(occasion) <= 300),
  item_ids text not null check (char_length(item_ids) between 1 and 4000),
  worn_on date,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists outfits_user_updated_idx
  on public.outfits (user_id, updated_at, id);

create index if not exists outfits_updated_at_idx
  on public.outfits (updated_at);

alter table public.outfits enable row level security;

create policy "outfits_select_own"
  on public.outfits for select
  using (user_id = auth.uid());

create policy "outfits_insert_own"
  on public.outfits for insert
  with check (user_id = auth.uid());

create policy "outfits_update_own"
  on public.outfits for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "outfits_delete_own"
  on public.outfits for delete
  using (user_id = auth.uid());

drop trigger if exists outfits_touch on public.outfits;
create trigger outfits_touch
  before insert or update on public.outfits
  for each row
  execute function public.touch_row_updated_at();
