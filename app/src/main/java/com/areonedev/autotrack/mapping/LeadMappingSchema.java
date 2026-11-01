package com.areonedev.autotrack.mapping;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.HashSet;
import java.util.Set;

/** Additive, transactional migration. Legacy customer data and dates are never rewritten. */
public final class LeadMappingSchema {
    private LeadMappingSchema() { }
    public static void migrate(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS MappingSchema (Version INTEGER NOT NULL)");
            Set<String> columns = new HashSet<>();
            try (Cursor c = db.rawQuery("PRAGMA table_info(Leads)", null)) {
                while (c.moveToNext()) columns.add(c.getString(c.getColumnIndexOrThrow("name")));
            }
            add(db, columns, "Score", "REAL");
            add(db, columns, "LastInteractionDate", "TEXT");
            add(db, columns, "LastInteractionBy", "TEXT");
            add(db, columns, "MappingFieldsKnown", "INTEGER NOT NULL DEFAULT 0");
            db.execSQL("CREATE TABLE IF NOT EXISTS LeadIdentities (OwnerID TEXT NOT NULL, " +
                    "LocalID INTEGER NOT NULL, SyncID TEXT NOT NULL, CloudID INTEGER, BaseVersion INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(OwnerID,LocalID), UNIQUE(OwnerID,SyncID), UNIQUE(OwnerID,CloudID), " +
                    "CHECK(LocalID > 0), CHECK(CloudID IS NULL OR CloudID > 0), CHECK(BaseVersion >= 0))");
            db.execSQL("INSERT INTO MappingSchema(Version) SELECT 1 WHERE NOT EXISTS(SELECT 1 FROM MappingSchema)");
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    private static void add(SQLiteDatabase db, Set<String> columns, String name, String type) {
        if (!columns.contains(name)) db.execSQL("ALTER TABLE Leads ADD COLUMN " + name + " " + type);
    }
}
