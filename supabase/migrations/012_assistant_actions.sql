-- Stage 0. Audit trail for the assistant pipeline (docs/life-os/assistant-pipeline.md).
-- Depends on 004_calendar_events.sql for public.touch_row_updated_at().

create table if not exists public.assistant_actions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  input_ref text not null default '' check (char_length(input_ref) <= 200),
  -- Executor idempotency key is (input_ref, action_index). One row per action, not per plan.
  action_index integer not null default 0 check (action_index >= 0 and action_index < 8),
  plan_json jsonb not null default '{}'::jsonb,
  risk text not null default 'low' check (risk in ('low', 'medium', 'high')),
  status text not null default 'proposed'
    check (status in ('proposed', 'approved', 'applied', 'rejected', 'failed')),
  error text not null default '' check (char_length(error) <= 2000),
  applied_at timestamptz,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create unique index if not exists assistant_actions_input_action_idx
  on public.assistant_actions (user_id, input_ref, action_index);
create index if not exists assistant_actions_user_updated_idx
  on public.assistant_actions (user_id, updated_at, id);

alter table public.assistant_actions enable row level security;

create policy "assistant_actions_select_own"
  on public.assistant_actions for select using (user_id = auth.uid());
create policy "assistant_actions_insert_own"
  on public.assistant_actions for insert with check (user_id = auth.uid());
create policy "assistant_actions_update_own"
  on public.assistant_actions for update
  using (user_id = auth.uid()) with check (user_id = auth.uid());

drop trigger if exists assistant_actions_touch on public.assistant_actions;
create trigger assistant_actions_touch
  before insert or update on public.assistant_actions
  for each row
  execute function public.touch_row_updated_at();

-- Append-only history of what the executor actually applied.
-- updated_at / deleted_at follow the sync convention (docs/life-os/data-model.md).
create table if not exists public.event_log (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  type text not null default '' check (char_length(type) <= 120),
  payload jsonb not null default '{}'::jsonb,
  source_action_id uuid references public.assistant_actions (id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists event_log_user_updated_idx
  on public.event_log (user_id, updated_at, id);
create index if not exists event_log_source_action_idx
  on public.event_log (source_action_id);

alter table public.event_log enable row level security;

create policy "event_log_select_own"
  on public.event_log for select using (user_id = auth.uid());
create policy "event_log_insert_own"
  on public.event_log for insert with check (user_id = auth.uid());
create policy "event_log_update_own"
  on public.event_log for update
  using (user_id = auth.uid()) with check (user_id = auth.uid());

drop trigger if exists event_log_touch on public.event_log;
create trigger event_log_touch
  before insert or update on public.event_log
  for each row
  execute function public.touch_row_updated_at();
