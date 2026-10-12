-- Text clips captured from other apps. Stays in supabase/planned/ until 012–019 are applied.
-- Room uses a local Long for notes.id. Sync maps clips.note_id to the note's remote uuid.
-- Sensitive clips are not uploaded. Owner RLS only. No household column.

create table if not exists public.clips (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  text text not null check (char_length(text) between 1 and 10000),
  text_hash text not null check (char_length(text_hash) = 64),
  source_kind text not null check (source_kind in ('selection','share','clipboard','tile','bubble')),
  source_app text check (char_length(source_app) <= 200),
  url text check (char_length(url) <= 2000),
  note_id uuid references public.notes(id) on delete set null,
  status text not null default 'inbox' check (status in ('inbox','in_note','filed','dismissed')),
  sensitive boolean not null default false,
  ai_kind text check (char_length(ai_kind) <= 20),
  ai_json jsonb,
  filed_type text check (char_length(filed_type) <= 40),
  filed_id text check (char_length(filed_id) <= 64),
  captured_at timestamptz not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
alter table public.clips enable row level security;
create policy clips_owner on public.clips
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());
create index clips_user_updated on public.clips (user_id, updated_at);
