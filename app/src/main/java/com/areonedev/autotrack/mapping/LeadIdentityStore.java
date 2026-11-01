package com.areonedev.autotrack.mapping;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.areonedev.autotrack.auth.AccountSession;
import java.io.File;
import java.util.UUID;

/** Called only through the Services account facade. Identity is persisted before any upload. */
public final class LeadIdentityStore {
    private final SQLiteDatabase db;
    private final String owner;
    public LeadIdentityStore(SQLiteDatabase db, String owner) {
        if (!new File(db.getPath()).getName().equals(AccountSession.databaseName(owner)))
            throw new IllegalArgumentException("The database does not belong to this account.");
        this.db = db; this.owner = owner;
    }
    public LeadIdentity get(long localId) {
        db.beginTransaction();
        try {
            LeadIdentity identity = byLocal(localId);
            if (identity == null) {
                requireLead(localId);
                insert(localId, UUID.randomUUID().toString(), null, 0);
                identity = byLocal(localId);
            }
            db.setTransactionSuccessful(); return identity;
        } finally { db.endTransaction(); }
    }
    public LeadIdentity find(String syncId) {
        validateUuid(syncId);
        return query("i.SyncID", syncId);
    }
    public void bind(long localId, String syncId, long cloudId, long version) {
        validateUuid(syncId);
        if (cloudId <= 0 || version <= 0) throw new IllegalArgumentException("Invalid remote identity.");
        db.beginTransaction();
        try {
            requireLead(localId);
            LeadIdentity existing = byLocal(localId);
            if (existing == null) insert(localId, syncId, cloudId, version);
            else {
                if (!existing.syncId.equals(syncId) || (existing.cloudId != null && existing.cloudId != cloudId)
                        || version < existing.baseVersion) throw new IllegalStateException("Record identity or version changed.");
                ContentValues values = new ContentValues(); values.put("CloudID", cloudId); values.put("BaseVersion", version);
                db.update("LeadIdentities", values, "OwnerID=? AND LocalID=?", new String[]{owner, Long.toString(localId)});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    private LeadIdentity byLocal(long id) { return query("i.LocalID", Long.toString(id)); }
    private LeadIdentity query(String column, String value) {
        try (Cursor c = db.rawQuery("SELECT i.LocalID,i.SyncID,i.CloudID,i.BaseVersion,l.MappingFieldsKnown " +
                "FROM LeadIdentities i LEFT JOIN Leads l ON l.LeadID=i.LocalID WHERE i.OwnerID=? AND " + column + "=?",
                new String[]{owner, value})) {
            if (!c.moveToFirst()) return null;
            return new LeadIdentity(owner, c.getLong(0), c.getString(1), c.isNull(2) ? null : c.getLong(2),
                    c.getLong(3), c.getInt(4) == 1);
        }
    }
    private void requireLead(long id) {
        try (Cursor c = db.rawQuery("SELECT 1 FROM Leads WHERE LeadID=?", new String[]{Long.toString(id)})) {
            if (id <= 0 || !c.moveToFirst()) throw new IllegalArgumentException("Local lead not found.");
        }
    }
    private void insert(long id, String syncId, Long cloudId, long version) {
        ContentValues values = new ContentValues(); values.put("OwnerID", owner); values.put("LocalID", id);
        values.put("SyncID", syncId); values.put("CloudID", cloudId); values.put("BaseVersion", version);
        db.insertOrThrow("LeadIdentities", null, values);
    }
    private static void validateUuid(String value) {
        if (value == null || !UUID.fromString(value).toString().equals(value)) throw new IllegalArgumentException("Invalid sync ID.");
    }
}
