-- Stage 1/3 support. media_assets (text data only). Stays in supabase/planned/ until promoted.
-- The image files are NOT in Supabase: they live in a private Cloudflare R2 bucket at
-- `{user_id}/{asset_id}.jpg`, reached through the `media-sign` Edge Function (presigned URLs).
-- Room v18 `media_assets` uses the same columns in camelCase. There is no sync handler yet.
-- Private by default: owner RLS only.
-- Includes a verbatim copy of public.touch_row_updated_at() from 004_calendar_events.sql so this file
-- works on a database where 004 is not applied yet. `create or replace` makes the copy harmless later.

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

create table if not exists public.media_assets (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  remote_path text check (remote_path is null or char_length(remote_path) between 1 and 300),
  kind text not null default 'unknown' check (kind in ('receipt', 'food', 'clothing', 'person', 'other', 'unknown')),
  title text not null default '' check (char_length(title) <= 300),
  description text not null default '' check (char_length(description) <= 2000),
  note text not null default '' check (char_length(note) <= 2000),
  analysis_state text not null default 'pending' check (analysis_state in ('pending', 'done', 'needs_input', 'failed')),
  linked_type text check (linked_type is null or char_length(linked_type) <= 100),
  linked_id text check (linked_id is null or char_length(linked_id) <= 100),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists media_assets_user_updated_idx
  on public.media_assets (user_id, updated_at, id);

create index if not exists media_assets_updated_at_idx
  on public.media_assets (updated_at);

alter table public.media_assets enable row level security;

create policy "media_assets_select_own"
  on public.media_assets for select
  using (user_id = auth.uid());

create policy "media_assets_insert_own"
  on public.media_assets for insert
  with check (user_id = auth.uid());

create policy "media_assets_update_own"
  on public.media_assets for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "media_assets_delete_own"
  on public.media_assets for delete
  using (user_id = auth.uid());

drop trigger if exists media_assets_touch on public.media_assets;
create trigger media_assets_touch
  before insert or update on public.media_assets
  for each row execute function public.touch_row_updated_at();
