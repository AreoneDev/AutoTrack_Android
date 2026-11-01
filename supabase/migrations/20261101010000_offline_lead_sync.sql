-- Requires the web account-ownership migration. Apply only to an isolated test project first.
BEGIN;
CREATE SCHEMA IF NOT EXISTS autotrack_sync;
REVOKE ALL ON SCHEMA autotrack_sync FROM PUBLIC,anon,authenticated;
CREATE TABLE IF NOT EXISTS autotrack_sync.operations(
 owner_id uuid NOT NULL REFERENCES auth.users,operation_id uuid NOT NULL,
 request jsonb NOT NULL,result jsonb NOT NULL,PRIMARY KEY(owner_id,operation_id));
REVOKE ALL ON autotrack_sync.operations FROM PUBLIC,anon,authenticated;

CREATE OR REPLACE FUNCTION public.autotrack_lead_snapshot(p_sync uuid) RETURNS jsonb
LANGUAGE sql STABLE SECURITY INVOKER SET search_path='' AS $$
 SELECT to_jsonb(l) || jsonb_build_object(
 'interest',(SELECT to_jsonb(v)||jsonb_build_object('trim',coalesce(r.trim_override,v.trim))
 FROM public.lead_vehicle_interests r JOIN public.vehicles v USING(vehicle_id) WHERE r.lead_id=l.lead_id),
 'trade_in',(SELECT to_jsonb(v)||jsonb_build_object('trim',coalesce(r.trim_override,v.trim))
 FROM public.lead_trade_in_vehicles r JOIN public.vehicles v USING(vehicle_id) WHERE r.lead_id=l.lead_id))
 FROM public.leads l WHERE l.sync_id=p_sync AND l.owner_id=auth.uid()
$$;
REVOKE ALL ON FUNCTION public.autotrack_lead_snapshot(uuid) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.autotrack_lead_snapshot(uuid) TO authenticated;

-- One statement produces one MVCC snapshot of current rows and tombstones; no timestamp cursor.
CREATE OR REPLACE FUNCTION public.autotrack_sync_snapshot() RETURNS jsonb
LANGUAGE sql STABLE SECURITY INVOKER SET search_path='' AS $$
 SELECT jsonb_build_object('leads',coalesce((SELECT jsonb_agg(public.autotrack_lead_snapshot(l.sync_id) ORDER BY l.lead_id)
 FROM public.leads l WHERE owner_id=auth.uid()),'[]'::jsonb),
 'deleted',coalesce((SELECT jsonb_agg(to_jsonb(d) ORDER BY d.sync_id) FROM public.sync_deleted d
 WHERE d.owner_id=auth.uid() AND d.entity='leads'),'[]'::jsonb))
$$;
REVOKE ALL ON FUNCTION public.autotrack_sync_snapshot() FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.autotrack_sync_snapshot() TO authenticated;

-- Existing web relationship writes must also invalidate the aggregate customer's revision.
CREATE OR REPLACE FUNCTION public.autotrack_touch_relationship() RETURNS trigger
LANGUAGE plpgsql SECURITY INVOKER SET search_path='' AS $$
BEGIN
 IF pg_trigger_depth()>1 THEN
 IF TG_OP='DELETE' THEN RETURN old; END IF; RETURN new;
 END IF;
 IF TG_OP<>'INSERT' THEN UPDATE public.leads SET updated_at=clock_timestamp() WHERE lead_id=old.lead_id; END IF;
 IF TG_OP<>'DELETE' AND (TG_OP='INSERT' OR new.lead_id IS DISTINCT FROM old.lead_id) THEN
 UPDATE public.leads SET updated_at=clock_timestamp() WHERE lead_id=new.lead_id; END IF;
 IF TG_OP='DELETE' THEN RETURN old; END IF; RETURN new;
END $$;
REVOKE ALL ON FUNCTION public.autotrack_touch_relationship() FROM PUBLIC,anon,authenticated;
DROP TRIGGER IF EXISTS autotrack_relation_revision ON public.lead_vehicle_interests;
CREATE TRIGGER autotrack_relation_revision AFTER INSERT OR UPDATE OR DELETE ON public.lead_vehicle_interests
 FOR EACH ROW EXECUTE FUNCTION public.autotrack_touch_relationship();
DROP TRIGGER IF EXISTS autotrack_relation_revision ON public.lead_trade_in_vehicles;
CREATE TRIGGER autotrack_relation_revision AFTER INSERT OR UPDATE OR DELETE ON public.lead_trade_in_vehicles
 FOR EACH ROW EXECUTE FUNCTION public.autotrack_touch_relationship();

-- Catalogue or private vehicle edits change every referencing customer snapshot.
CREATE OR REPLACE FUNCTION public.autotrack_touch_vehicle() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
BEGIN
 UPDATE public.leads SET updated_at=clock_timestamp() WHERE lead_id IN(
 SELECT lead_id FROM public.lead_vehicle_interests WHERE vehicle_id=old.vehicle_id
 UNION SELECT lead_id FROM public.lead_trade_in_vehicles WHERE vehicle_id=old.vehicle_id);
 RETURN new;
END $$;
REVOKE ALL ON FUNCTION public.autotrack_touch_vehicle() FROM PUBLIC,anon,authenticated;
DROP TRIGGER IF EXISTS autotrack_vehicle_revision ON public.vehicles;
CREATE TRIGGER autotrack_vehicle_revision AFTER UPDATE ON public.vehicles FOR EACH ROW EXECUTE FUNCTION public.autotrack_touch_vehicle();

-- Preserve deletion semantics for the existing web repository's direct DELETE path too.
CREATE OR REPLACE FUNCTION public.autotrack_cascade_customer() RETURNS trigger
LANGUAGE plpgsql SECURITY INVOKER SET search_path='' AS $$
BEGIN
 DELETE FROM public.lead_vehicle_interests WHERE lead_id=old.lead_id;
 DELETE FROM public.lead_trade_in_vehicles WHERE lead_id=old.lead_id;
 DELETE FROM public.tasks WHERE lead_id=old.lead_id;
 DELETE FROM public.notifications WHERE lead_id=old.lead_id;
 RETURN old;
END $$;
REVOKE ALL ON FUNCTION public.autotrack_cascade_customer() FROM PUBLIC,anon,authenticated;
DROP TRIGGER IF EXISTS autotrack_cascade_customer ON public.leads;
CREATE TRIGGER autotrack_cascade_customer BEFORE DELETE ON public.leads FOR EACH ROW EXECUTE FUNCTION public.autotrack_cascade_customer();

-- Definer access is limited to an authenticated owner and fixed application tables.
-- Clients cannot forge operation receipts; both locks and the receipt share the write transaction.
CREATE OR REPLACE FUNCTION public.autotrack_sync_lead(p_operation jsonb) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE
 account uuid:=auth.uid(); op uuid:=(p_operation->>'operation_id')::uuid;
 sync uuid:=(p_operation->>'sync_id')::uuid; base bigint:=(p_operation->>'base_version')::bigint;
 kind text:=p_operation->>'kind'; previous autotrack_sync.operations;
 current_row public.leads; merged public.leads; tombstone public.sync_deleted;
 fields jsonb; result jsonb; relation text; vehicle jsonb; vehicle_row public.vehicles; vehicle_key bigint;
BEGIN
 IF account IS NULL OR op IS NULL OR sync IS NULL OR base IS NULL OR base<0 OR kind NOT IN('put','delete') OR kind IS NULL THEN
 RAISE EXCEPTION 'Invalid sync operation.'; END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended(account::text||op::text,0));
 SELECT * INTO previous FROM autotrack_sync.operations WHERE owner_id=account AND operation_id=op;
 IF FOUND THEN
 IF previous.request<>p_operation THEN RAISE EXCEPTION 'Operation payload changed.'; END IF;
 RETURN previous.result;
 END IF;
 PERFORM pg_advisory_xact_lock(hashtextextended(sync::text,1));
 SELECT * INTO current_row FROM public.leads WHERE sync_id=sync FOR UPDATE;
 IF FOUND AND current_row.owner_id<>account THEN RAISE EXCEPTION 'Record unavailable.'; END IF;
 SELECT * INTO tombstone FROM public.sync_deleted WHERE owner_id=account AND entity='leads' AND sync_id=sync;
 IF current_row.lead_id IS NULL AND tombstone.sync_id IS NOT NULL THEN
 result:=jsonb_build_object('status','conflict','remote',to_jsonb(tombstone)||jsonb_build_object('deleted',true));
 ELSIF current_row.lead_id IS NOT NULL AND current_row.version<>base THEN
 result:=jsonb_build_object('status','conflict','remote',public.autotrack_lead_snapshot(sync));
 ELSIF current_row.lead_id IS NULL AND base<>0 THEN
 RAISE EXCEPTION 'Record unavailable without deletion history.';
 ELSIF kind='delete' THEN
 IF current_row.lead_id IS NOT NULL THEN
 PERFORM public.autotrack_delete_lead(current_row.lead_id);
 SELECT * INTO tombstone FROM public.sync_deleted WHERE owner_id=account AND entity='leads' AND sync_id=sync;
 ELSE
 INSERT INTO public.sync_deleted(owner_id,entity,sync_id,version) VALUES(account,'leads',sync,1) RETURNING * INTO tombstone;
 END IF;
 result:=jsonb_build_object('status','ok','version',tombstone.version);
 ELSE
 fields:=p_operation->'fields';
 IF fields IS NULL OR jsonb_typeof(fields)<>'object' OR EXISTS(SELECT 1 FROM jsonb_object_keys(fields) k WHERE k NOT IN(
 'first_name','last_name','phone','lead_email','lead_division','lead_address','lead_city','lead_province','lead_country','lead_postal_code',
 'budget','stage','follow_up_date','score','notes','created_at','last_interaction_date','last_interaction_by','status')) THEN
 RAISE EXCEPTION 'Invalid customer fields.'; END IF;
 merged:=jsonb_populate_record(current_row,fields);
 IF current_row.lead_id IS NULL THEN
 INSERT INTO public.leads(owner_id,sync_id,first_name,last_name,phone,lead_email,lead_division,lead_address,lead_city,lead_province,lead_country,lead_postal_code,
 budget,stage,follow_up_date,score,notes,created_at,last_interaction_date,last_interaction_by,status)
 VALUES(account,sync,merged.first_name,merged.last_name,merged.phone,merged.lead_email,merged.lead_division,merged.lead_address,merged.lead_city,merged.lead_province,
 merged.lead_country,merged.lead_postal_code,merged.budget,merged.stage,merged.follow_up_date,merged.score,merged.notes,merged.created_at,merged.last_interaction_date,merged.last_interaction_by,merged.status)
 RETURNING * INTO current_row;
 ELSE
 UPDATE public.leads SET first_name=merged.first_name,last_name=merged.last_name,phone=merged.phone,lead_email=merged.lead_email,lead_division=merged.lead_division,
 lead_address=merged.lead_address,lead_city=merged.lead_city,lead_province=merged.lead_province,lead_country=merged.lead_country,lead_postal_code=merged.lead_postal_code,
 budget=merged.budget,stage=merged.stage,follow_up_date=merged.follow_up_date,score=merged.score,notes=merged.notes,created_at=merged.created_at,
 last_interaction_date=merged.last_interaction_date,last_interaction_by=merged.last_interaction_by,status=merged.status
 WHERE lead_id=current_row.lead_id RETURNING * INTO current_row;
 END IF;
 FOREACH relation IN ARRAY ARRAY['interest','trade_in'] LOOP
 IF NOT p_operation ? relation THEN RAISE EXCEPTION 'Vehicle snapshot is required.'; END IF;
 vehicle:=p_operation->relation;
 -- Keep catalogue entries immutable; each customer owns its uploaded vehicle snapshot.
 IF vehicle<>'null'::jsonb THEN
 IF jsonb_typeof(vehicle)<>'object' OR EXISTS(SELECT 1 FROM jsonb_object_keys(vehicle) k WHERE k NOT IN('make','model','year','trim','price','color','in_stock','vin','transmission')) THEN RAISE EXCEPTION 'Invalid vehicle snapshot.'; END IF;
 vehicle_row:=jsonb_populate_record(NULL::public.vehicles,vehicle);
 INSERT INTO public.vehicles(owner_id,make,model,year,trim,price,color,in_stock,vin,transmission)
 VALUES(account,vehicle_row.make,vehicle_row.model,vehicle_row.year,vehicle_row.trim,vehicle_row.price,vehicle_row.color,vehicle_row.in_stock,vehicle_row.vin,vehicle_row.transmission)
 RETURNING vehicle_id INTO vehicle_key;
 END IF;
 IF relation='interest' THEN
 DELETE FROM public.lead_vehicle_interests WHERE lead_id=current_row.lead_id;
 IF vehicle<>'null'::jsonb THEN INSERT INTO public.lead_vehicle_interests(lead_id,vehicle_id) VALUES(current_row.lead_id,vehicle_key); END IF;
 ELSE
 DELETE FROM public.lead_trade_in_vehicles WHERE lead_id=current_row.lead_id;
 IF vehicle<>'null'::jsonb THEN INSERT INTO public.lead_trade_in_vehicles(lead_id,vehicle_id) VALUES(current_row.lead_id,vehicle_key); END IF;
 END IF;
 END LOOP;
 SELECT * INTO current_row FROM public.leads WHERE sync_id=sync;
 result:=jsonb_build_object('status','ok','lead_id',current_row.lead_id,'version',current_row.version);
 END IF;
 INSERT INTO autotrack_sync.operations VALUES(account,op,p_operation,result);
 RETURN result;
END $$;
REVOKE ALL ON FUNCTION public.autotrack_sync_lead(jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.autotrack_sync_lead(jsonb) TO authenticated;
NOTIFY pgrst,'reload schema';
COMMIT;
