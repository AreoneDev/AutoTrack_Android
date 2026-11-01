package com.areonedev.autotrack.sync;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import com.areonedev.autotrack.application.Services;
import com.areonedev.autotrack.auth.AccountSession;
import com.areonedev.autotrack.objects.Lead;
import com.areonedev.autotrack.persistence.DataAccessObject;
import org.json.*;
import org.junit.*;
import java.io.File;
import java.util.*;
import static org.junit.Assert.*;

public class OfflineSyncDatabaseTest {
    Context context; File file; String owner; DataAccessObject dao;
    @Before public void open() {
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Services.initialize(context); owner=UUID.randomUUID().toString();
        file=new File(context.getCacheDir(),AccountSession.databaseName(owner));
        dao=new DataAccessObject(file.getPath(),false,owner); dao.open(file.getPath()); assertTrue(dao.isOpen());
    }
    @After public void close() { dao.close(); SQLiteDatabase.deleteDatabase(file); }
    Lead lead() { Lead l=new Lead(); l.setLeadFirstName("Synthetic"); l.setLeadLastName("Offline"); l.setLeadPhoneNumber("5550100123"); return l; }
    @Test public void queueSurvivesReopenAndDoesNotPersistPlaintextPayload() throws Exception {
        Lead l=lead(); assertNull(dao.insertLead(l));
        List<SyncEngine.Operation> ops=dao.syncPending(owner); assertEquals(1,ops.size());
        assertTrue(ops.get(0).body.contains("Synthetic"));
        try(SQLiteDatabase raw=SQLiteDatabase.openDatabase(file.getPath(),null,SQLiteDatabase.OPEN_READONLY);
            android.database.Cursor c=raw.rawQuery("SELECT Payload FROM SyncOutbox",null)) {
            assertTrue(c.moveToFirst()); assertFalse(c.getString(0).contains("Synthetic"));
        }
        dao.close(); dao.open(file.getPath()); assertEquals(ops.get(0).id,dao.syncPending(owner).get(0).id);
    }
    @Test public void outboxFailureRollsBackBusinessWrite() {
        try(SQLiteDatabase raw=SQLiteDatabase.openDatabase(file.getPath(),null,0)) {
            raw.execSQL("CREATE TRIGGER fail_queue BEFORE INSERT ON SyncOutbox BEGIN SELECT RAISE(ABORT,'injected failure'); END");
        }
        assertNotNull(dao.insertLead(lead())); List<Lead> rows=new ArrayList<>(); assertNull(dao.getLeadSequential(rows)); assertTrue(rows.isEmpty());
    }
    @Test public void uploadAcknowledgementCannotDiscardNewerLocalEdit() throws Exception {
        Lead l=lead(); assertNull(dao.insertLead(l)); SyncEngine.Operation op=dao.syncPending(owner).get(0);
        l.setLeadNotes("newer"); assertNull(dao.updateLead(l));
        JSONObject request=new JSONObject(op.body);
        dao.syncAcknowledge(owner,op,new JSONObject().put("status","ok").put("lead_id",9000000000L).put("version",1).toString());
        List<SyncEngine.Operation> next=dao.syncPending(owner); assertEquals(1,next.size()); assertNotEquals(op.id,next.get(0).id);
        JSONObject body=new JSONObject(next.get(0).body); assertEquals(1,body.getLong("base_version")); assertEquals("newer",body.getJSONObject("fields").getString("notes"));
    }
    @Test public void remoteHydrationDoesNotEnqueueAndDeletePropagates() throws Exception {
        String sync=UUID.randomUUID().toString();
        JSONObject row=new JSONObject().put("sync_id",sync).put("lead_id",8000000000L).put("version",2)
            .put("first_name","RemoteSynthetic").put("last_name","Only").put("status",true)
            .put("created_at","2026-11-01T12:00:00.000Z");
        dao.syncApply(owner,new JSONObject().put("leads",new JSONArray().put(row)).put("deleted",new JSONArray()).toString());
        assertTrue(dao.syncPending(owner).isEmpty()); List<Lead> rows=new ArrayList<>(); dao.getLeadSequential(rows); assertEquals(1,rows.size());
        dao.syncApply(owner,new JSONObject().put("leads",new JSONArray()).put("deleted",new JSONArray().put(new JSONObject().put("sync_id",sync).put("version",3))).toString());
        rows.clear();dao.getLeadSequential(rows); assertTrue(rows.isEmpty()); assertTrue(dao.syncPending(owner).isEmpty());
    }
    @Test public void remoteChangeWithLocalEditIsRetainedAsConflict() throws Exception {
        Lead l=lead(); assertNull(dao.insertLead(l)); SyncEngine.Operation op=dao.syncPending(owner).get(0);
        dao.syncAcknowledge(owner,op,"{\"status\":\"conflict\",\"remote\":{\"version\":4}}");
        assertTrue(dao.syncPending(owner).isEmpty()); assertEquals(1,dao.syncConflicts(owner).size());
        List<Lead> rows=new ArrayList<>();dao.getLeadSequential(rows);assertEquals(1,rows.size());
    }
    @Test public void acceptRemoteRestoresLocallyDeletedCustomer() throws Exception {
        Lead l=lead();assertNull(dao.insertLead(l));SyncEngine.Operation op=dao.syncPending(owner).get(0);
        dao.syncAcknowledge(owner,op,"{\"status\":\"ok\",\"lead_id\":42,\"version\":1}");
        String sync=dao.getLeadIdentity(l.getLeadID()).syncId;assertNull(dao.deleteLead(l));op=dao.syncPending(owner).get(0);
        JSONObject remote=new JSONObject().put("sync_id",sync).put("lead_id",42).put("version",2).put("first_name","Accepted").put("created_at","2026-11-01T12:00:00Z");
        dao.syncAcknowledge(owner,op,new JSONObject().put("status","conflict").put("remote",remote).toString());
        dao.syncResolve(owner,op.id,false);List<Lead> rows=new ArrayList<>();dao.getLeadSequential(rows);assertEquals(1,rows.size());assertEquals("Accepted",rows.get(0).getLeadFirstName());assertTrue(dao.syncPending(owner).isEmpty());
    }
    @Test public void keepingLocalDeletionAfterConflictQueuesDeleteAtNewVersion() throws Exception {
        Lead l=lead();assertNull(dao.insertLead(l));SyncEngine.Operation op=dao.syncPending(owner).get(0);
        dao.syncAcknowledge(owner,op,"{\"status\":\"ok\",\"lead_id\":42,\"version\":1}");
        String sync=dao.getLeadIdentity(l.getLeadID()).syncId;assertNull(dao.deleteLead(l));op=dao.syncPending(owner).get(0);
        JSONObject remote=new JSONObject().put("sync_id",sync).put("lead_id",42).put("version",2).put("first_name","Accepted").put("created_at","2026-11-01T12:00:00Z");
        dao.syncAcknowledge(owner,op,new JSONObject().put("status","conflict").put("remote",remote).toString());dao.syncResolve(owner,op.id,true);
        JSONObject next=new JSONObject(dao.syncPending(owner).get(0).body);assertEquals("delete",next.getString("kind"));assertEquals(2,next.getLong("base_version"));
    }
    @Test public void tamperedQueueCannotBeUploadedOrDiscarded() throws Exception {
        assertNull(dao.insertLead(lead()));
        try(SQLiteDatabase raw=SQLiteDatabase.openDatabase(file.getPath(),null,0)) {raw.execSQL("UPDATE SyncOutbox SET Payload='tampered'");}
        try {dao.syncPending(owner);fail();}catch(Exception expected) { }
        try(SQLiteDatabase raw=SQLiteDatabase.openDatabase(file.getPath(),null,0);android.database.Cursor c=raw.rawQuery("SELECT count(*) FROM SyncOutbox",null)) {assertTrue(c.moveToFirst());assertEquals(1,c.getInt(0));}
    }
    @Test public void firstSyncQueuesPreviouslyMappedUnsyncedRows() throws Exception {
        Lead l=lead();assertNull(dao.insertLead(l));String sync=dao.getLeadIdentity(l.getLeadID()).syncId;
        try(SQLiteDatabase raw=SQLiteDatabase.openDatabase(file.getPath(),null,0)) {raw.execSQL("DELETE FROM SyncOutbox");raw.execSQL("DELETE FROM SyncState");}
        List<SyncEngine.Operation> pending=dao.syncPending(owner);assertEquals(1,pending.size());assertEquals(sync,new JSONObject(pending.get(0).body).getString("sync_id"));
    }
    @Test public void damagedAccountPiiCannotBecomeAnUploadPayload() throws Exception {
        Lead l=lead();assertNull(dao.insertLead(l));
        try(SQLiteDatabase raw=SQLiteDatabase.openDatabase(file.getPath(),null,0)) {raw.execSQL("DELETE FROM SyncOutbox");raw.execSQL("DELETE FROM SyncState");raw.execSQL("DELETE FROM LeadIdentities");raw.execSQL("UPDATE Leads SET FirstName='damaged-ciphertext'");}
        try {dao.syncPending(owner);fail("Corrupt customer data must block upload.");}catch(Exception expected) { }
    }
    @Test public void localDeleteRemovesChildrenWithForeignKeysEnabled() throws Exception {
        java.lang.reflect.Field field=DataAccessObject.class.getDeclaredField("db");field.setAccessible(true);((SQLiteDatabase)field.get(dao)).setForeignKeyConstraintsEnabled(true);
        Lead l=lead();assertNull(dao.insertLead(l));
        com.areonedev.autotrack.objects.Task task=new com.areonedev.autotrack.objects.Task(l,"Synthetic child",new Date());assertNull(dao.insertTask(task));
        assertNull(dao.deleteLead(l));List<com.areonedev.autotrack.objects.Task> tasks=new ArrayList<>();assertNull(dao.getTaskSequential(tasks));assertTrue(tasks.isEmpty());
    }
    @Test public void sentQueueIdentityCannotBeRebound() throws Exception {
        Lead l=lead();assertNull(dao.insertLead(l));SyncEngine.Operation op=dao.syncPending(owner).get(0);
        try {dao.bindLeadIdentity(l.getLeadID(),UUID.randomUUID().toString(),99,1);fail();}catch(IllegalStateException expected) { }
        assertEquals(op.body,dao.syncPending(owner).get(0).body);
    }
    @Test public void wrongAccountCannotReadQueue() throws Exception { try { dao.syncPending(UUID.randomUUID().toString());fail(); } catch(IllegalStateException expected) { } }
    @Test public void localDeletionKeepsDurableIdentityAndDeleteOperation() throws Exception {
        Lead l=lead();assertNull(dao.insertLead(l));String sync=dao.getLeadIdentity(l.getLeadID()).syncId;
        assertNull(dao.deleteLead(l)); List<Lead> rows=new ArrayList<>();dao.getLeadSequential(rows);assertTrue(rows.isEmpty());
        List<SyncEngine.Operation> ops=dao.syncPending(owner);assertEquals(1,ops.size());assertEquals("delete",new JSONObject(ops.get(0).body).getString("kind"));
        assertEquals(sync,dao.findLeadIdentity(sync).syncId);
    }
}
