-- Match the web privilege correction without project-specific deployment history.
begin;
do $$
declare table_name text;
begin
  foreach table_name in array array['leads','tasks','notifications','vehicles',
    'lead_vehicle_interests','lead_trade_in_vehicles','imported_cars','sync_deleted'] loop
    execute format('revoke truncate, references, trigger on public.%I from authenticated', table_name);
  end loop;
end $$;
commit;
