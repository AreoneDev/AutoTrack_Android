package com.areonedev.autotrack.mapping;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import com.areonedev.autotrack.application.Services;
import com.areonedev.autotrack.auth.AccountSession;
import com.areonedev.autotrack.objects.Lead;
import com.areonedev.autotrack.objects.Vehicle;
import com.areonedev.autotrack.persistence.DataAccess;
import com.areonedev.autotrack.persistence.DataAccessObject;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class LeadMappingDatabaseTest {
    @Test public void identitiesSurviveReopenAndAreScopedToAccounts() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String a = UUID.randomUUID().toString(), b = UUID.randomUUID().toString();
        try {
            Services.initialize(context); Services.openAccount(a);
            DataAccess first = Services.getDataAccess("AutoTrack");
            Lead lead = synthetic(); assertNull(first.insertLead(lead));
            LeadIdentity id = first.getLeadIdentity(lead.getLeadID());
            assertEquals(a, id.ownerId); assertEquals(lead.getLeadID(), id.localId);
            assertNull(id.cloudId); assertEquals(0, id.baseVersion);
            assertEquals(id.syncId, first.getLeadIdentity(lead.getLeadID()).syncId);
            first.bindLeadIdentity(lead.getLeadID(), id.syncId, 9007199254740993L, 4);
            Services.closeDataAccess(); Services.openAccount(a);
            LeadIdentity restored = Services.getDataAccess("AutoTrack").findLeadIdentity(id.syncId);
            assertEquals(Long.valueOf(9007199254740993L), restored.cloudId); assertEquals(4, restored.baseVersion);
            Services.openAccount(b);
            DataAccess second = Services.getDataAccess("AutoTrack");
            Lead other = synthetic(); assertNull(second.insertLead(other));
            assertEquals(lead.getLeadID(), other.getLeadID());
            assertNotEquals(id.syncId, second.getLeadIdentity(other.getLeadID()).syncId);
            assertNull(second.findLeadIdentity(id.syncId));
            try { first.getLeadIdentity(lead.getLeadID()); fail("Stale account handle accepted."); }
            catch (IllegalStateException expected) { }
            try { second.bindLeadIdentity(other.getLeadID(), id.syncId, 12, 1); fail("Existing identity changed."); }
            catch (IllegalStateException expected) { }
        } finally { cleanup(context, a, b); }
    }

    @Test public void fieldsDatesAndVehicleClearingSurviveSQLiteRoundTrip() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String account = UUID.randomUUID().toString();
        try {
            Services.initialize(context); Services.openAccount(account);
            DataAccess dao = Services.getDataAccess("AutoTrack"); Lead lead = synthetic();
            lead.setLeadScore(81.25); lead.setLeadCreatedAt(new Date(1234567890123L));
            lead.setLeadFollowUpDate(null); lead.setLastInteractionDate(new Date(1234567890456L));
            lead.setLastInteractionBy("LEAD");
            lead.setLeadVehicleInterest(new Vehicle("Synthetic", "Example", "2024", "Custom"));
            lead.setTradeInVehicle(new Vehicle("Synthetic", "Old", "2018", "Base"));
            assertNull(dao.insertLead(lead));
            Lead copy = dao.getLeadRandom(lead).get(0);
            assertEquals(81.25, copy.getLeadScore(), 0); assertNull(copy.getLeadFollowUpDate());
            assertEquals(lead.getLeadCreatedAt(), copy.getLeadCreatedAt());
            assertEquals(lead.getLastInteractionDate(), copy.getLastInteractionDate());
            assertEquals("LEAD", copy.getLastInteractionBy());
            assertTrue(dao.getLeadIdentity(lead.getLeadID()).fieldsKnown);
            copy.setLeadVehicleInterest(null); copy.setTradeInVehicle(null); copy.setLeadCreatedAt(null);
            assertNull(dao.updateLead(copy));
            Lead cleared = dao.getLeadRandom(lead).get(0);
            assertNull(cleared.getLeadVehicleInterest()); assertNull(cleared.getTradeInVehicle());
            assertNull(cleared.getLeadCreatedAt());
        } finally { cleanup(context, account); }
    }

    @Test public void additiveMigrationRetainsLegacyIdsDatesAndUnknownFields() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String account = UUID.randomUUID().toString();
        try {
            try (SQLiteDatabase db = context.openOrCreateDatabase(AccountSession.databaseName(account), 0, null)) {
                db.execSQL("CREATE TABLE Leads (LeadID INTEGER PRIMARY KEY AUTOINCREMENT, Status INTEGER, " +
                        "FirstName TEXT, LastName TEXT, PhoneNumber TEXT, Email TEXT, Division TEXT, Address TEXT, " +
                        "City TEXT, Province TEXT, Country TEXT, PostalCode TEXT, Budget REAL, Stage TEXT, Notes TEXT, " +
                        "FollowUpDate TEXT, CreatedAt TEXT, VI_Make TEXT, VI_Model TEXT, VI_Year TEXT, VI_Trim TEXT, " +
                        "VI_Price REAL, VI_Color TEXT, VI_InStock INTEGER, VI_VIN TEXT, VI_Trans TEXT, " +
                        "TI_Make TEXT, TI_Model TEXT, TI_Year TEXT, TI_Trim TEXT, TI_Price REAL, TI_Color TEXT, " +
                        "TI_InStock INTEGER, TI_VIN TEXT, TI_Trans TEXT)");
                db.execSQL("INSERT INTO Leads(LeadID,FirstName,Status,FollowUpDate,CreatedAt) " +
                        "VALUES(77,'Synthetic legacy',1,'29/02/2024','01/11/2020')");
            }
            Services.initialize(context); Services.openAccount(account);
            DataAccess dao = Services.getDataAccess("AutoTrack");
            Lead criteria = new Lead(); criteria.setLeadID(77);
            Lead copy = dao.getLeadRandom(criteria).get(0);
            assertEquals("Synthetic legacy", copy.getLeadFirstName());
            assertEquals("29/02/2024", LeadMapper.format(copy.getLeadFollowUpDate(), "dd/MM/yyyy", TimeZone.getDefault()));
            LeadIdentity id = dao.getLeadIdentity(77); assertFalse(id.fieldsKnown);
            assertNull(dao.updateLead(copy)); assertFalse(dao.getLeadIdentity(77).fieldsKnown);
            Services.closeDataAccess(); Services.openAccount(account);
            assertEquals(id.syncId, Services.getDataAccess("AutoTrack").getLeadIdentity(77).syncId);
        } finally { cleanup(context, account); }
    }

    private static Lead synthetic() { Lead lead = new Lead(); lead.setLeadFirstName("Synthetic mapping"); return lead; }

    @Test public void remoteBindingsAreUniqueAndFailuresPreserveExistingMappings() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String account = UUID.randomUUID().toString();
        try {
            Services.initialize(context); Services.openAccount(account);
            DataAccess dao = Services.getDataAccess("AutoTrack");
            Lead first = synthetic(), second = synthetic();
            assertNull(dao.insertLead(first)); assertNull(dao.insertLead(second));
            String remoteSync = UUID.randomUUID().toString();
            dao.bindLeadIdentity(first.getLeadID(), remoteSync, 100, 3);
            assertEquals(first.getLeadID(), dao.findLeadIdentity(remoteSync).localId);
            try { dao.bindLeadIdentity(second.getLeadID(), remoteSync, 100, 3); fail("Duplicate mapping accepted."); }
            catch (android.database.sqlite.SQLiteConstraintException expected) { }
            try { dao.bindLeadIdentity(first.getLeadID(), remoteSync, 101, 4); fail("Cloud identity changed."); }
            catch (IllegalStateException expected) { }
            try { dao.bindLeadIdentity(first.getLeadID(), remoteSync, 100, 2); fail("Version regressed."); }
            catch (IllegalStateException expected) { }
            assertEquals(3, dao.findLeadIdentity(remoteSync).baseVersion);
            assertEquals(Long.valueOf(100), dao.findLeadIdentity(remoteSync).cloudId);
            assertNull(dao.deleteLead(first));
            assertEquals(remoteSync, dao.findLeadIdentity(remoteSync).syncId);
        } finally { cleanup(context, account); }
    }

    @Test public void failedMigrationRollsBackColumnsAndRetainsCustomerRows() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        java.io.File file = new java.io.File(context.getCacheDir(), "mapping-rollback-" + UUID.randomUUID() + ".db");
        try (SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file, null)) {
            db.execSQL("CREATE TABLE Leads(LeadID INTEGER PRIMARY KEY, FirstName TEXT)");
            db.execSQL("INSERT INTO Leads VALUES(77,'Synthetic rollback')");
            db.execSQL("CREATE TABLE MappingSchema(Version INTEGER NOT NULL)");
            db.execSQL("CREATE TRIGGER reject_migration BEFORE INSERT ON MappingSchema BEGIN SELECT RAISE(ABORT,'Synthetic failure'); END");
            try { LeadMappingSchema.migrate(db); fail("Migration failure hidden."); }
            catch (android.database.sqlite.SQLiteException expected) { }
            try (android.database.Cursor c = db.rawQuery("SELECT * FROM Leads", null)) {
                assertEquals(2, c.getColumnCount()); assertTrue(c.moveToFirst());
                assertEquals(77, c.getLong(0)); assertEquals("Synthetic rollback", c.getString(1));
            }
            db.execSQL("DROP TRIGGER reject_migration"); LeadMappingSchema.migrate(db); LeadMappingSchema.migrate(db);
        } finally { SQLiteDatabase.deleteDatabase(file); }
    }

    @Test public void legacyDatabaseCannotBeAssignedAnOwnerByTheMappingLayer() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        java.io.File file = new java.io.File(context.getCacheDir(), "mapping-legacy-" + UUID.randomUUID() + ".db");
        DataAccessObject dao = new DataAccessObject(file.getAbsolutePath(), false);
        try {
            dao.open(file.getAbsolutePath()); assertTrue(dao.isOpen());
            Lead lead = synthetic(); assertNull(dao.insertLead(lead));
            try { dao.getLeadIdentity(lead.getLeadID()); fail("Legacy data acquired an implicit owner."); }
            catch (IllegalStateException expected) { }
        } finally { dao.close(); SQLiteDatabase.deleteDatabase(file); }
    }
    private static void cleanup(Context context, String... accounts) {
        Services.closeDataAccess();
        for (String account : accounts) context.deleteDatabase(AccountSession.databaseName(account));
    }
}
