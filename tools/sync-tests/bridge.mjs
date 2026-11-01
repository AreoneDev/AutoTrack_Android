// Ephemeral, loopback-only test service. Synthetic bearer IDs are not real Supabase authentication.
import http from 'node:http';
import { PGlite } from '@electric-sql/pglite';
import { readFile } from 'node:fs/promises';
const db=new PGlite();
for(const file of ['./fixtures/base.sql','./fixtures/ownership.sql','../../supabase/migrations/20261101010000_offline_lead_sync.sql']) await db.exec(await readFile(new URL(file,import.meta.url),'utf8'));
const accounts=new Set(['00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002']);
let queue=Promise.resolve();
const server=http.createServer((req,res)=> {
 queue=queue.then(async()=> {
  try {
   const owner=req.headers.authorization?.replace('Bearer ','');if(!accounts.has(owner)) {res.writeHead(401);res.end('{}');return;}
   const chunks=[];for await(const chunk of req) chunks.push(chunk);const body=JSON.parse(Buffer.concat(chunks).toString()||'{}');
   await db.exec('SET ROLE authenticated');await db.query("SELECT set_config('request.jwt.claim.sub',$1,false)",[owner]);
   let result;
   if(req.url==='/autotrack_sync_lead') result=(await db.query('SELECT public.autotrack_sync_lead($1::jsonb) AS value',[JSON.stringify(body.p_operation)])).rows[0].value;
   else if(req.url==='/autotrack_sync_snapshot') result=(await db.query('SELECT public.autotrack_sync_snapshot() AS value')).rows[0].value;
   else if(req.url==='/web') {
    // Match the existing web repository's scalar UPDATE/DELETE paths; no privileged data API bypass.
    if(body.action==='edit') await db.query('UPDATE public.leads SET notes=$1 WHERE sync_id=$2',[body.notes,body.sync_id]);
    else if(body.action==='delete') await db.query('DELETE FROM public.leads WHERE sync_id=$1',[body.sync_id]);
    else if(body.action==='create') await db.query('INSERT INTO public.leads(first_name,last_name,notes,created_at,status) VALUES($1,$2,$3,now(),true)',['WebSynthetic','Bridge',body.notes]);
    result=(await db.query('SELECT public.autotrack_sync_snapshot() AS value')).rows[0].value;
   } else {res.writeHead(404);res.end('{}');return;}
   res.setHeader('content-type','application/json');res.end(JSON.stringify(result));
  } catch(error) {res.writeHead(409);res.end(JSON.stringify({error:'Synthetic test operation rejected'}));console.error('Synthetic bridge failure:',error.code??error.name);}
  finally {await db.exec('RESET ROLE');}
 }).catch(error=>console.error(error.name));
});
server.listen(18765,'127.0.0.1',()=>console.log('Synthetic sync bridge ready on loopback port 18765'));
for(const signal of ['SIGTERM','SIGINT']) process.on(signal,async()=>{server.close();await db.close();process.exit(0);});
