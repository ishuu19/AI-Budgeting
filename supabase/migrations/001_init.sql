-- LedgerAI Phase 2 schema: finance tables + RLS
-- Apply in Supabase SQL editor or via CLI (in exact order: 001, 002, 003).
-- Every table uses RLS policy "xxx_select/insert/update/delete_own" with "user_id = auth.uid()".
-- App uses PostgREST paths matching table names (e.g. "reminders").

create extension if not exists "pgcrypto";

-- ─── transactions ────────────────────────────────────────────────────────────

create table if not exists public.transactions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  amount double precision not null,
  type text not null check (type in ('INCOME', 'EXPENSE')),
  category text not null,
  merchant text not null default '',
  note text not null default '',
  date date not null default current_date,
  created_at timestamptz not null default now(),
  is_recurring boolean not null default false,
  currency text not null default 'USD',
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists transactions_user_id_idx on public.transactions (user_id);
create index if not exists transactions_user_date_idx on public.transactions (user_id, date);

alter table public.transactions enable row level security;

create policy "transactions_select_own"
  on public.transactions for select
  using (user_id = auth.uid());

create policy "transactions_insert_own"
  on public.transactions for insert
  with check (user_id = auth.uid());

create policy "transactions_update_own"
  on public.transactions for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "transactions_delete_own"
  on public.transactions for delete
  using (user_id = auth.uid());

-- ─── budgets ─────────────────────────────────────────────────────────────────

create table if not exists public.budgets (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  category text not null,
  monthly_limit double precision not null,
  spent double precision not null default 0,
  month int not null check (month between 1 and 12),
  year int not null,
  alert_threshold int not null default 80,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (user_id, category, month, year)
);

create index if not exists budgets_user_id_idx on public.budgets (user_id);

alter table public.budgets enable row level security;

create policy "budgets_select_own"
  on public.budgets for select
  using (user_id = auth.uid());

create policy "budgets_insert_own"
  on public.budgets for insert
  with check (user_id = auth.uid());

create policy "budgets_update_own"
  on public.budgets for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "budgets_delete_own"
  on public.budgets for delete
  using (user_id = auth.uid());

-- ─── debts ───────────────────────────────────────────────────────────────────

create table if not exists public.debts (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  friend_name text not null,
  amount double precision not null,
  direction text not null check (direction in ('I_OWE', 'THEY_OWE')),
  date_lent date not null default current_date,
  due_date date,
  phone text not null default '',
  email text not null default '',
  note text not null default '',
  is_paid boolean not null default false,
  currency text not null default 'USD',
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists debts_user_id_idx on public.debts (user_id);

alter table public.debts enable row level security;

create policy "debts_select_own"
  on public.debts for select
  using (user_id = auth.uid());

create policy "debts_insert_own"
  on public.debts for insert
  with check (user_id = auth.uid());

create policy "debts_update_own"
  on public.debts for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "debts_delete_own"
  on public.debts for delete
  using (user_id = auth.uid());

-- ─── goals ───────────────────────────────────────────────────────────────────

create table if not exists public.goals (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name text not null,
  target_amount double precision not null,
  saved_amount double precision not null default 0,
  target_date date,
  emoji text not null default '🎯',
  note text not null default '',
  is_completed boolean not null default false,
  created_at date not null default current_date,
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists goals_user_id_idx on public.goals (user_id);

alter table public.goals enable row level security;

create policy "goals_select_own"
  on public.goals for select
  using (user_id = auth.uid());

create policy "goals_insert_own"
  on public.goals for insert
  with check (user_id = auth.uid());

create policy "goals_update_own"
  on public.goals for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "goals_delete_own"
  on public.goals for delete
  using (user_id = auth.uid());

-- ─── bills ───────────────────────────────────────────────────────────────────

create table if not exists public.bills (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name text not null,
  amount double precision not null,
  frequency text not null default 'MONTHLY'
    check (frequency in ('WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY')),
  next_due_date date not null,
  category text not null default 'SUBSCRIPTIONS',
  note text not null default '',
  is_active boolean not null default true,
  currency text not null default 'USD',
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create index if not exists bills_user_id_idx on public.bills (user_id);

alter table public.bills enable row level security;

create policy "bills_select_own"
  on public.bills for select
  using (user_id = auth.uid());

create policy "bills_insert_own"
  on public.bills for insert
  with check (user_id = auth.uid());

create policy "bills_update_own"
  on public.bills for update
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "bills_delete_own"
  on public.bills for delete
  using (user_id = auth.uid());
