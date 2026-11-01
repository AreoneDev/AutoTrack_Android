package com.areonedev.autotrack.sync;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.areonedev.autotrack.mapping.*;
import com.areonedev.autotrack.objects.Lead;
import com.areonedev.autotrack.persistence.DataAccessObject;
import org.json.*;
import java.util.*;

/** Account-scoped journal. Frozen requests survive retries; later edits have their own revision. */
public final class OfflineLeadStore {
    private final SQLiteDatabase db;
    private final String owner;
    private final DataAccessObject dao;
    private final LeadIdentityStore identities;
    private final SyncCipher cipher;
    private final LeadMapper mapper = new LeadMapper(TimeZone.getDefault());
    public OfflineLeadStore(SQLiteDatabase db,String owner,DataAccessObject dao,LeadIdentityStore identities) {
        this.db=db;this.owner=owner;this.dao=dao;this.identities=identities;cipher=new SyncCipher(owner);
        db.execSQL("CREATE TABLE IF NOT EXISTS SyncState(LocalID INTEGER PRIMARY KEY, SyncID TEXT NOT NULL UNIQUE, Revision INTEGER NOT NULL, Kind TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS SyncOutbox(OperationID TEXT PRIMARY KEY, LocalID INTEGER NOT NULL UNIQUE, Revision INTEGER NOT NULL, Payload TEXT NOT NULL, Conflict TEXT, Sent INTEGER NOT NULL DEFAULT 0)");
    }
    public void requireOwner(String value) { if(!owner.equals(value)) throw new IllegalStateException("Wrong sync account."); }
    /** Caller must hold the business transaction, including for a local deletion. */
    public void changed(long localId,boolean deleted) {
        LeadIdentity id=deleted ? identity(localId) : identities.get(localId);
        db.execSQL("UPDATE SyncState SET Revision=Revision+1,Kind=? WHERE LocalID=?",new Object[]{deleted?"delete":"put",localId});
        db.execSQL("INSERT OR IGNORE INTO SyncState(LocalID,SyncID,Revision,Kind) VALUES(?,?,1,?)",new Object[]{localId,id.syncId,deleted?"delete":"put"});
        db.delete("SyncOutbox","LocalID=? AND Sent=0",new String[]{Long.toString(localId)});
        try { freeze(localId); } catch(Exception failure) { throw new IllegalStateException("Unable to queue customer securely.",failure); }
    }
    private void freeze(long localId) throws Exception {
        try(Cursor c=db.rawQuery("SELECT s.Revision,s.Kind FROM SyncState s WHERE s.LocalID=? AND NOT EXISTS(SELECT 1 FROM SyncOutbox WHERE LocalID=s.LocalID)",new String[]{Long.toString(localId)})) {
            if(!c.moveToFirst()) return;
            String op=UUID.randomUUID().toString();long revision=c.getLong(0);
            ContentValues v=new ContentValues();v.put("OperationID",op);v.put("LocalID",localId);v.put("Revision",revision);
            v.put("Payload",cipher.encrypt(request(localId,revision,c.getString(1),op).toString()));db.insertOrThrow("SyncOutbox",null,v);
        }
    }
    private LeadIdentity identity(long localId) {
        try(Cursor c=db.rawQuery("SELECT SyncID FROM LeadIdentities WHERE OwnerID=? AND LocalID=?",new String[]{owner,Long.toString(localId)})) {
            if(!c.moveToFirst()) throw new IllegalStateException("Missing lead identity.");return identities.find(c.getString(0));
        }
    }
    private JSONObject request(long localId,long revision,String kind,String operationId) throws Exception {
        LeadIdentity id=identity(localId);
        JSONObject body=new JSONObject().put("operation_id",operationId).put("sync_id",id.syncId).put("base_version",id.baseVersion).put("kind",kind);
        if(!kind.equals("delete")) {
            Lead criteria=new Lead();criteria.setLeadID(localId);List<Lead> rows=dao.getLeadRandom(criteria);
            if(rows.size()!=1) throw new IllegalStateException("Pending customer unavailable.");
            Lead lead=rows.get(0);Map<String,Object> fields=mapper.toCloudRow(lead);
            if(!id.fieldsKnown) fields=mapper.legacyPatch(fields);
            body.put("fields",new JSONObject(fields));
            body.put("interest",json(mapper.vehicleToRow(lead.getLeadVehicleInterest())));
            body.put("trade_in",json(mapper.vehicleToRow(lead.getTradeInVehicle())));
        }
        return body;
    }
    private static Object json(Map<String,Object> value) { return value==null?JSONObject.NULL:new JSONObject(value); }
    public List<SyncEngine.Operation> pending(String account) throws Exception {
        requireOwner(account);db.beginTransaction();
        try {
            // First-use migration queues existing account-owned rows, never the unclaimed legacy database.
            try(Cursor c=db.rawQuery("SELECT l.LeadID FROM Leads l LEFT JOIN LeadIdentities i ON i.LocalID=l.LeadID AND i.OwnerID=? WHERE (i.LocalID IS NULL OR i.CloudID IS NULL) AND l.LeadID NOT IN(SELECT LocalID FROM SyncState)",new String[]{owner})) {
                while(c.moveToNext()) changed(c.getLong(0),false);
            }
            try(Cursor c=db.rawQuery("SELECT LocalID FROM SyncState WHERE LocalID NOT IN(SELECT LocalID FROM SyncOutbox) ORDER BY LocalID LIMIT 100",null)) {
                while(c.moveToNext()) freeze(c.getLong(0));
            }
            List<SyncEngine.Operation> result=new ArrayList<>();
            try(Cursor c=db.rawQuery("SELECT OperationID,Payload FROM SyncOutbox WHERE Conflict IS NULL ORDER BY LocalID LIMIT 100",null)) {
                while(c.moveToNext()) {
                    result.add(new SyncEngine.Operation(c.getString(0),cipher.decrypt(c.getString(1))));
                    db.execSQL("UPDATE SyncOutbox SET Sent=1 WHERE OperationID=?",new Object[]{c.getString(0)});
                }
            }
            db.setTransactionSuccessful();return result;
        } finally { db.endTransaction(); }
    }
    public void acknowledge(String account,SyncEngine.Operation op,String response) throws Exception {
        requireOwner(account);JSONObject result=new JSONObject(response);db.beginTransaction();
        try(Cursor c=db.rawQuery("SELECT LocalID,Revision,Payload FROM SyncOutbox WHERE OperationID=?",new String[]{op.id})) {
            if(!c.moveToFirst()) return;
            if(!cipher.decrypt(c.getString(2)).equals(op.body)) throw new IllegalStateException("Operation changed.");
            long localId=c.getLong(0),revision=c.getLong(1);String status=result.getString("status");
            if(status.equals("conflict")) {
                ContentValues v=new ContentValues();v.put("Conflict",cipher.encrypt(response));db.update("SyncOutbox",v,"OperationID=?",new String[]{op.id});
            } else if(status.equals("ok")) {
                JSONObject request=new JSONObject(op.body);long version=result.getLong("version");
                if(request.getString("kind").equals("put")) {
                    LeadIdentity id=identity(localId);
                    if(version<id.baseVersion || (id.cloudId!=null && id.cloudId!=result.getLong("lead_id"))) throw new IllegalStateException("Invalid acknowledgement.");
                    db.execSQL("UPDATE LeadIdentities SET CloudID=?,BaseVersion=? WHERE OwnerID=? AND LocalID=?",new Object[]{result.getLong("lead_id"),version,owner,localId});
                }
                else db.execSQL("UPDATE LeadIdentities SET BaseVersion=? WHERE OwnerID=? AND LocalID=?",new Object[]{version,owner,localId});
                db.delete("SyncOutbox","OperationID=?",new String[]{op.id});
                db.delete("SyncState","LocalID=? AND Revision=?",new String[]{Long.toString(localId),Long.toString(revision)});
            } else throw new IllegalStateException("Unknown sync result.");
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public List<String> conflicts(String account) throws Exception {
        requireOwner(account);List<String> result=new ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT OperationID,Conflict,LocalID FROM SyncOutbox WHERE Conflict IS NOT NULL ORDER BY LocalID",null)) {
            while(c.moveToNext()) {
                String kind;long revision;
                try(Cursor state=db.rawQuery("SELECT Kind,Revision FROM SyncState WHERE LocalID=?",new String[]{Long.toString(c.getLong(2))})) {
                    if(!state.moveToFirst()) throw new IllegalStateException("Conflict intent is unavailable.");kind=state.getString(0);revision=state.getLong(1);
                }
                result.add(new JSONObject().put("operation_id",c.getString(0)).put("response",new JSONObject(cipher.decrypt(c.getString(1))))
                    .put("local",request(c.getLong(2),revision,kind,c.getString(0))).toString());
            }
        }return result;
    }
    private boolean dirty(long id) { try(Cursor c=db.rawQuery("SELECT 1 FROM SyncState WHERE LocalID=?",new String[]{Long.toString(id)})) {return c.moveToFirst();} }
    private void conflict(LeadIdentity id,JSONObject remote) throws Exception {
        ContentValues v=new ContentValues();v.put("Conflict",cipher.encrypt(new JSONObject().put("status","conflict").put("remote",remote).toString()));
        // Freeze unsent edits too before recording the remote conflict.
        pending(owner);db.update("SyncOutbox",v,"LocalID=?",new String[]{Long.toString(id.localId)});
    }
    public void apply(String account,String snapshot) throws Exception {
        requireOwner(account);JSONObject document=new JSONObject(snapshot);JSONArray leads=document.getJSONArray("leads"),deleted=document.getJSONArray("deleted");
        db.beginTransaction();
        try {
            for(int i=0;i<leads.length();i++) hydrate(leads.getJSONObject(i));
            for(int i=0;i<deleted.length();i++) {
                JSONObject tombstone=deleted.getJSONObject(i);LeadIdentity id=identities.find(tombstone.getString("sync_id"));
                if(id==null || tombstone.getLong("version")<=id.baseVersion) continue;
                if(dirty(id.localId)) conflict(id,tombstone.put("deleted",true));
                else { dao.syncDeleteLocal(id.localId);db.execSQL("UPDATE LeadIdentities SET BaseVersion=? WHERE OwnerID=? AND LocalID=?",new Object[]{tombstone.getLong("version"),owner,id.localId}); }
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    private void hydrate(JSONObject row) throws Exception {
        String sync=row.getString("sync_id");long version=row.getLong("version");LeadIdentity id=identities.find(sync);
        if(id!=null && version<=id.baseVersion) return;
        if(id!=null && dirty(id.localId)) { conflict(id,row);return; }
        Lead lead=mapper.fromRow(toMap(row),id==null?1:id.localId);
        lead.setLeadVehicleInterest(mapper.vehicleFromRow(optionalMap(row,"interest")));
        lead.setTradeInVehicle(mapper.vehicleFromRow(optionalMap(row,"trade_in")));
        long localId=dao.syncSaveLocal(lead,id==null);
        identities.bind(localId,sync,row.getLong("lead_id"),version);
    }
    public void resolve(String account,String operationId,boolean keepLocal) throws Exception {
        requireOwner(account);db.beginTransaction();
        try(Cursor c=db.rawQuery("SELECT LocalID,Conflict FROM SyncOutbox WHERE OperationID=?",new String[]{operationId})) {
            if(!c.moveToFirst() || c.isNull(1)) throw new IllegalArgumentException("Conflict not found.");
            long id=c.getLong(0);JSONObject remote=new JSONObject(cipher.decrypt(c.getString(1))).getJSONObject("remote");
            LeadIdentity identity=identity(id);
            if(keepLocal && remote.optBoolean("deleted",false)) throw new IllegalStateException("Deleted remote customers cannot be resurrected; save a new customer instead.");
            boolean localDeleted;
            try(Cursor state=db.rawQuery("SELECT Kind FROM SyncState WHERE LocalID=?",new String[]{Long.toString(id)})) {localDeleted=state.moveToFirst() && state.getString(0).equals("delete");}
            db.delete("SyncOutbox","OperationID=?",new String[]{operationId});
            db.delete("SyncState","LocalID=?",new String[]{Long.toString(id)});
            if(keepLocal) {
                // The local deletion has already removed the business row; preserve its durable identity.
                db.execSQL("UPDATE LeadIdentities SET CloudID=?,BaseVersion=? WHERE OwnerID=? AND LocalID=?",new Object[]{remote.getLong("lead_id"),remote.getLong("version"),owner,id});changed(id,localDeleted);
            } else if(remote.optBoolean("deleted",false)) {
                dao.syncDeleteLocal(id);db.execSQL("UPDATE LeadIdentities SET BaseVersion=? WHERE OwnerID=? AND LocalID=?",new Object[]{remote.getLong("version"),owner,id});
            } else {
                // Force hydration even when the last known version matches the chosen remote copy.
                db.execSQL("UPDATE LeadIdentities SET BaseVersion=0 WHERE OwnerID=? AND LocalID=?",new Object[]{owner,id});hydrate(remote);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    private static Map<String,Object> optionalMap(JSONObject row,String key) throws JSONException { return row.isNull(key)?null:toMap(row.getJSONObject(key)); }
    private static Map<String,Object> toMap(JSONObject json) throws JSONException {
        Map<String,Object> result=new LinkedHashMap<>();Iterator<String> keys=json.keys();
        while(keys.hasNext()) { String key=keys.next();Object value=json.get(key);result.put(key,value==JSONObject.NULL?null:value); }return result;
    }
}
