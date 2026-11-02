-- AutoTrack account ownership and sync metadata.
-- This project currently contains synthetic test data. Keep those fixtures and
-- assign them to the explicitly selected test login below. Future production
-- data belongs in a separate project. Vehicles remain a shared catalogue.
-- Run as the project database administrator, in one transaction.
begin;

do $$
declare account_id uuid; account_count integer;
begin
  select count(*), (array_agg(id))[1] into account_count, account_id
  from auth.users where lower(email) = lower('__TEST_OWNER_EMAIL__');
  if account_count <> 1 then
    raise exception 'Expected exactly one existing Auth account for the selected email; aborting.';
  end if;
  perform set_config('autotrack.migration_owner', account_id::text, true);
end $$;

-- Block concurrent writes while taking the baseline and applying ownership.
lock table public.leads, public.tasks, public.notifications, public.vehicles,
  public.lead_vehicle_interests, public.lead_trade_in_vehicles,
  public.imported_cars in share row exclusive mode;
create temporary table autotrack_migration_counts (
  table_name text primary key, row_count bigint not null
) on commit drop;
do $$
declare table_name text; row_count bigint;
begin
  foreach table_name in array array['leads','tasks','notifications','vehicles',
    'lead_vehicle_interests','lead_trade_in_vehicles','imported_cars'] loop
    execute format('select count(*) from public.%I', table_name) into row_count;
    insert into autotrack_migration_counts values(table_name, row_count);
  end loop;
end $$;

-- Preserve the pre-migration application rows in a schema not exposed by Data API.
create schema if not exists autotrack_backup;
revoke all on schema autotrack_backup from public, anon, authenticated;
do $$
declare table_name text;
begin
  foreach table_name in array array['leads','tasks','notifications','vehicles',
    'lead_vehicle_interests','lead_trade_in_vehicles','imported_cars'] loop
    execute format('create table if not exists autotrack_backup.%I as table public.%I',
      table_name || '_20261030', table_name);
  end loop;
end $$;
revoke all on all tables in schema autotrack_backup from public, anon, authenticated;

alter table public.leads add column if not exists owner_id uuid references auth.users(id) default auth.uid();
alter table public.tasks add column if not exists owner_id uuid references auth.users(id) default auth.uid();
alter table public.notifications add column if not exists owner_id uuid references auth.users(id) default auth.uid();
alter table public.vehicles add column if not exists owner_id uuid references auth.users(id) default auth.uid();

update public.leads set owner_id = current_setting('autotrack.migration_owner')::uuid where owner_id is null;
update public.tasks t set owner_id = l.owner_id from public.leads l where t.lead_id = l.lead_id and t.owner_id is null;
update public.notifications n set owner_id = l.owner_id from public.leads l where n.lead_id = l.lead_id and n.owner_id is null;
update public.tasks set owner_id = current_setting('autotrack.migration_owner')::uuid where owner_id is null;
update public.notifications set owner_id = current_setting('autotrack.migration_owner')::uuid where owner_id is null;
-- Do not backfill vehicles.owner_id: existing rows are shared catalogue entries.

alter table public.leads alter column owner_id set not null;
alter table public.tasks alter column owner_id set not null;
alter table public.notifications alter column owner_id set not null;

create index if not exists leads_owner_id_idx on public.leads(owner_id);
create index if not exists tasks_owner_id_idx on public.tasks(owner_id);
create index if not exists notifications_owner_id_idx on public.notifications(owner_id);
create index if not exists vehicles_owner_id_idx on public.vehicles(owner_id);
alter table public.tasks add column if not exists task_type text;
alter table public.tasks add column if not exists notes text;
alter table public.tasks add column if not exists completed_at timestamptz;

-- Stable cross-device identifiers do not replace existing primary keys.
do $$
declare table_name text;
begin
  foreach table_name in array array['leads','tasks','notifications'] loop
    execute format('alter table public.%I add column if not exists sync_id uuid not null default gen_random_uuid()', table_name);
    execute format('alter table public.%I add column if not exists version bigint not null default 1', table_name);
    execute format('alter table public.%I add column if not exists updated_at timestamptz not null default now()', table_name);
    execute format('create unique index if not exists %I on public.%I(sync_id)', table_name || '_sync_id_idx', table_name);
  end loop;
end $$;

create table if not exists public.sync_deleted (
  owner_id uuid not null references auth.users(id),
  entity text not null check(entity in ('leads','tasks','notifications')),
  sync_id uuid not null,
  version bigint not null,
  deleted_at timestamptz not null default now(),
  primary key (owner_id, entity, sync_id)
);
alter table public.sync_deleted enable row level security;

-- The trigger alone writes deletion history with its fixed OLD row identity.
-- Table mutations still obey RLS before this trigger executes.
create or replace function public.autotrack_record_revision()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if TG_OP = 'DELETE' then
    insert into public.sync_deleted(owner_id, entity, sync_id, version)
    values(old.owner_id, TG_TABLE_NAME, old.sync_id, old.version + 1)
    on conflict(owner_id, entity, sync_id) do update
      set version = greatest(public.sync_deleted.version, excluded.version), deleted_at = now();
    return old;
  end if;
  if new.sync_id is distinct from old.sync_id or new.owner_id is distinct from old.owner_id then
    raise exception 'Record identity and owner are immutable.';
  end if;
  new.version := old.version + 1;
  new.updated_at := clock_timestamp();
  return new;
end $$;
do $$
declare table_name text;
begin
  foreach table_name in array array['leads','tasks','notifications'] loop
    execute format('drop trigger if exists autotrack_revision on public.%I', table_name);
    execute format('create trigger autotrack_revision before update or delete on public.%I for each row execute function public.autotrack_record_revision()', table_name);
  end loop;
end $$;

-- A new restrictive policy does not neutralize old permissive policies.
-- Replace every policy on these application tables, within this transaction.
do $$
declare policy_row record; table_name text;
begin
  foreach table_name in array array['leads','tasks','notifications','vehicles',
    'lead_vehicle_interests','lead_trade_in_vehicles','imported_cars','sync_deleted'] loop
    execute format('alter table public.%I enable row level security', table_name);
    for policy_row in select policyname from pg_policies
      where schemaname = 'public' and tablename = table_name loop
      execute format('drop policy %I on public.%I', policy_row.policyname, table_name);
    end loop;
    execute format('revoke all on public.%I from public, anon', table_name);
    execute format('grant select, insert, update, delete on public.%I to authenticated', table_name);
  end loop;
end $$;

create policy own_leads on public.leads for all to authenticated
  using (owner_id = (select auth.uid()))
  with check (owner_id = (select auth.uid()));

create policy own_tasks on public.tasks for all to authenticated
  using (owner_id = (select auth.uid()))
  with check (owner_id = (select auth.uid()) and
    (lead_id is null or exists(select 1 from public.leads l
      where l.lead_id = tasks.lead_id and l.owner_id = (select auth.uid()))));

create policy own_notifications on public.notifications for all to authenticated
  using (owner_id = (select auth.uid()))
  with check (owner_id = (select auth.uid()) and
    (lead_id is null or exists(select 1 from public.leads l
      where l.lead_id = notifications.lead_id and l.owner_id = (select auth.uid()))));

create policy catalogue_and_own_vehicles on public.vehicles for select to authenticated
  using (owner_id is null or owner_id = (select auth.uid()));
create policy insert_own_vehicle on public.vehicles for insert to authenticated
  with check (owner_id = (select auth.uid()));
create policy update_own_vehicle on public.vehicles for update to authenticated
  using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
create policy delete_own_vehicle on public.vehicles for delete to authenticated
  using (owner_id = (select auth.uid()));

create policy own_interests on public.lead_vehicle_interests for all to authenticated
  using (
    exists(select 1 from public.leads l where l.lead_id = lead_vehicle_interests.lead_id and l.owner_id = (select auth.uid()))
    and exists(select 1 from public.vehicles v where v.vehicle_id = lead_vehicle_interests.vehicle_id and (v.owner_id is null or v.owner_id = (select auth.uid())))
  )
  with check (
    exists(select 1 from public.leads l where l.lead_id = lead_vehicle_interests.lead_id and l.owner_id = (select auth.uid()))
    and exists(select 1 from public.vehicles v where v.vehicle_id = lead_vehicle_interests.vehicle_id and (v.owner_id is null or v.owner_id = (select auth.uid())))
  );
create policy own_trade_ins on public.lead_trade_in_vehicles for all to authenticated
  using (
    exists(select 1 from public.leads l where l.lead_id = lead_trade_in_vehicles.lead_id and l.owner_id = (select auth.uid()))
    and exists(select 1 from public.vehicles v where v.vehicle_id = lead_trade_in_vehicles.vehicle_id and (v.owner_id is null or v.owner_id = (select auth.uid())))
  )
  with check (
    exists(select 1 from public.leads l where l.lead_id = lead_trade_in_vehicles.lead_id and l.owner_id = (select auth.uid()))
    and exists(select 1 from public.vehicles v where v.vehicle_id = lead_trade_in_vehicles.vehicle_id and (v.owner_id is null or v.owner_id = (select auth.uid())))
  );
create policy authenticated_catalogue on public.imported_cars for select to authenticated using (true);
revoke insert, update, delete on public.imported_cars from authenticated;

create policy read_own_deletions on public.sync_deleted for select to authenticated
  using (owner_id = (select auth.uid()));
-- Clients can read their deletion history but cannot forge entries.
revoke insert, update, delete on public.sync_deleted from authenticated;

revoke all on function public.autotrack_record_revision() from public, anon, authenticated;

create or replace function public.autotrack_delete_lead(p_lead_id bigint)
returns void language plpgsql security invoker set search_path = '' as $$
begin
  if auth.uid() is null then raise exception 'Sign in required.'; end if;
  perform 1 from public.leads where lead_id = p_lead_id and owner_id = auth.uid() for update;
  if not found then raise exception 'Lead not found.'; end if;
  delete from public.lead_vehicle_interests where lead_id = p_lead_id;
  delete from public.lead_trade_in_vehicles where lead_id = p_lead_id;
  delete from public.tasks where lead_id = p_lead_id;
  delete from public.notifications where lead_id = p_lead_id;
  delete from public.leads where lead_id = p_lead_id;
end $$;
revoke all on function public.autotrack_delete_lead(bigint) from public, anon;
grant execute on function public.autotrack_delete_lead(bigint) to authenticated;

-- Fail atomically if ownership, relationship integrity, or catalogue preservation
-- differs from the intended migration. No customer row is removed.
do $$
declare baseline record; actual_count bigint;
begin
  for baseline in select * from autotrack_migration_counts loop
    execute format('select count(*) from public.%I', baseline.table_name) into actual_count;
    if actual_count <> baseline.row_count then
      raise exception 'Row count changed for %; migration rolled back.', baseline.table_name;
    end if;
  end loop;
  if exists(select 1 from public.tasks t join public.leads l using(lead_id) where t.owner_id <> l.owner_id)
    or exists(select 1 from public.notifications n join public.leads l using(lead_id) where n.owner_id <> l.owner_id) then
    raise exception 'Cross-account relationship found; migration rolled back.';
  end if;
end $$;
notify pgrst, 'reload schema';
commit;
