-- Run this in Supabase SQL Editor.
-- Prerequisite: public.users must already exist (see the schema in
-- ChatApiService.kt's doc comment if it doesn't).

create table public.device_tokens (
  user_id       uuid references public.users(id) on delete cascade,
  onesignal_id  text not null,
  updated_at    timestamptz default now(),
  primary key (user_id)
);

-- Enable RLS and let each user manage only their own row
alter table public.device_tokens enable row level security;

create policy "Users can upsert their own device token"
  on public.device_tokens for insert
  with check (auth.uid() = user_id);

create policy "Users can update their own device token"
  on public.device_tokens for update
  using (auth.uid() = user_id);

create policy "Users can read their own device token"
  on public.device_tokens for select
  using (auth.uid() = user_id);
