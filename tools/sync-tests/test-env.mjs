import test from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { PGlite } from '@electric-sql/pglite';
import { buildBundle } from '../supabase-test-env/prepare.mjs';

const owner = '00000000-0000-0000-0000-000000000001';
const other = '00000000-0000-0000-0000-000000000002';
const bundle = await buildBundle({ projectRef: 'abcdefghijklmnopqrst', ownerEmail: "qa'owner@example.invalid" });
async function platform() {
  const db = new PGlite();
  await db.exec(`CREATE ROLE anon; CREATE ROLE authenticated; CREATE SCHEMA auth;
    CREATE TABLE auth.users(id uuid PRIMARY KEY, email text);
    CREATE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql STABLE AS
    $$ SELECT nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
    GRANT USAGE ON SCHEMA auth TO authenticated;
    GRANT EXECUTE ON FUNCTION auth.uid() TO authenticated;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO anon, authenticated;`);
  return db;
}

test('bootstrap rejects a missing owner atomically and refuses a populated schema', async () => {
  const db = await platform();
  try {
    await assert.rejects(() => db.exec(bundle.sql), /Expected exactly one existing Auth account/);
    await db.exec('ROLLBACK');
    assert.equal((await db.query("SELECT to_regclass('public.leads') AS relation")).rows[0].relation, null);
    await db.exec('CREATE TABLE public.sentinel(value text); INSERT INTO public.sentinel VALUES (\'keep\')');
    await assert.rejects(() => db.exec(bundle.sql), /Expected an empty application schema/);
    await db.exec('ROLLBACK');
    assert.equal((await db.query('SELECT value FROM public.sentinel')).rows[0].value, 'keep');
  } finally { await db.close(); }
});

test('real baseline, ownership, privilege correction and sync compose with isolated accounts', async () => {
  const db = await platform();
  try {
    await db.query('INSERT INTO auth.users VALUES ($1,$2),($3,$4)', [owner,"qa'owner@example.invalid",other,'other@example.invalid']);
    await db.exec(bundle.sql);
    assert.equal((await db.query('SELECT count(*)::int AS n FROM public.leads')).rows[0].n, 0);
    const columns = (await db.query("SELECT column_name FROM information_schema.columns WHERE table_name='imported_cars' AND table_schema='public' ORDER BY ordinal_position")).rows;
    assert.deepEqual(columns.map(row => row.column_name), ['Make','Year','Model','Category']);
    await db.exec(`SET ROLE authenticated; SET request.jwt.claim.sub='${owner}'`);
    const rpc = async op => (await db.query('SELECT public.autotrack_sync_lead($1::jsonb) AS result',[JSON.stringify(op)])).rows[0].result;
    const snapshot = async () => (await db.query('SELECT public.autotrack_sync_snapshot() AS result')).rows[0].result;
    await db.exec('BEGIN; SAVEPOINT defaults_check');
    const defaultLead = (await db.query('INSERT INTO public.leads DEFAULT VALUES RETURNING lead_country')).rows[0];
    assert.equal(defaultLead.lead_country, 'Canada');
    const defaultVehicle = (await db.query('INSERT INTO public.vehicles(owner_id) VALUES(auth.uid()) RETURNING price::float8 AS price')).rows[0];
    assert.equal(defaultVehicle.price, 0);
    await db.exec('ROLLBACK TO SAVEPOINT defaults_check; COMMIT');
    const operation = { operation_id: randomUUID(), sync_id: randomUUID(), base_version: 0, kind: 'put', fields: { first_name:'Synthetic',last_name:'QA',notes:'created',lead_country:'Canada' }, interest: {make:'Synthetic',model:'QA',year:2026,price:0,in_stock:false,vin:'SYNTHETIC-QA',transmission:'Automatic'},trade_in:null };
    await assert.rejects(() => rpc({...operation,operation_id:randomUUID(),interest:{make:'Synthetic'}}), /not-null constraint/);
    assert.equal((await snapshot()).leads.length, 0, 'incomplete vehicle rolls back the aggregate');
    const created = await rpc(operation);
    assert.equal(created.status, 'ok');
    assert.deepEqual(await rpc(operation), created);
    const lead = (await snapshot()).leads[0];
    assert.equal(lead.owner_id, owner);
    assert.equal(lead.lead_country, 'Canada');
    assert.equal(lead.interest.price, 0);
    await db.query('INSERT INTO public.tasks(lead_id,title,task_date) VALUES($1,$2,now())',[created.lead_id,'Synthetic task']);
    await db.query('INSERT INTO public.notifications(lead_id,title) VALUES($1,$2)',[created.lead_id,'Synthetic notification']);
    await assert.rejects(() => db.exec('TRUNCATE public.leads CASCADE'), /permission denied/);
    await assert.rejects(() => db.exec('SELECT * FROM autotrack_sync.operations'), /permission denied/);
    await db.exec(`SET request.jwt.claim.sub='${other}'`);
    assert.equal((await snapshot()).leads.length, 0);
    assert.equal((await db.query('SELECT * FROM public.tasks')).rows.length, 0);
    assert.equal((await db.query('SELECT * FROM public.notifications')).rows.length, 0);
    assert.equal((await db.query('SELECT * FROM public.vehicles')).rows.length, 0);
    await assert.rejects(() => rpc({...operation,operation_id:randomUUID(),base_version:created.version}));
    await db.exec(`SET request.jwt.claim.sub='${owner}'`);
    await db.query('DELETE FROM public.leads WHERE lead_id=$1',[created.lead_id]);
    assert.equal((await snapshot()).deleted[0].sync_id, operation.sync_id);
    assert.equal((await db.query('SELECT * FROM public.tasks')).rows.length, 0);
    assert.equal((await db.query('SELECT * FROM public.notifications')).rows.length, 0);
    await db.exec('RESET ROLE; SET ROLE anon');
    await assert.rejects(snapshot, /permission denied/);
    await assert.rejects(() => db.exec('SELECT * FROM public.leads'), /permission denied/);
  } finally { await db.close(); }
});
