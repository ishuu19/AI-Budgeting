-- Notebook columns on clips, plus private chat threads and messages.
-- Stays in supabase/planned/ until 012–020 are applied. Owner RLS only.

alter table public.clips
  add column if not exists edited_text text check (char_length(edited_text) <= 10000),
  add column if not exists pinned boolean not null default false,
  add column if not exists pin_order int,
  add column if not exists prompt text check (char_length(prompt) <= 2000),
  add column if not exists window_title text check (char_length(window_title) <= 200),
  add column if not exists thread_id uuid,
  add column if not exists message_id uuid;
alter table public.clips drop constraint if exists clips_source_kind_check;
alter table public.clips add constraint clips_source_kind_check check (source_kind in
  ('selection','share','clipboard','tile','bubble','screen','ocr','typed','voice','ai'));

create table if not exists public.chat_threads (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  title text not null check (char_length(title) between 1 and 120),
  origin text not null default 'notebook' check (origin in ('notebook','home')),
  source_app text check (char_length(source_app) <= 200),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
create table if not exists public.chat_messages (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  thread_id uuid not null references public.chat_threads(id) on delete cascade,
  role text not null check (role in ('user','assistant')),
  text text not null check (char_length(text) between 1 and 20000),
  prompt text check (char_length(prompt) <= 2000),
  clip_ids jsonb,
  state text not null default 'done' check (state in ('queued','sent','done','failed')),
  actions_json jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
alter table public.chat_threads  enable row level security;
alter table public.chat_messages enable row level security;
create policy chat_threads_owner  on public.chat_threads
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy chat_messages_owner on public.chat_messages
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());
create index chat_threads_user_updated  on public.chat_threads  (user_id, updated_at);
create index chat_messages_thread_time  on public.chat_messages (thread_id, created_at);
