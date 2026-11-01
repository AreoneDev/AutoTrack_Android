package com.areonedev.autotrack.sync;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import com.areonedev.autotrack.application.Services;
import com.areonedev.autotrack.auth.AccountSession;
import com.areonedev.autotrack.objects.Lead;
import com.areonedev.autotrack.persistence.DataAccessObject;
import org.json.*;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

/** Explicit local integration test; requires the ephemeral bridge and adb reverse. No real Auth credentials. */
public class LocalCrossClientSyncTest {
    static final String A="00000000-0000-0000-0000-000000000001",B="00000000-0000-0000-0000-000000000002";
    static String post(String route,String owner,String body) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:18765/"+route).openConnection();
        try {
            c.setConnectTimeout(5000);c.setReadTimeout(10000);c.setRequestMethod("POST");c.setDoOutput(true);
            c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Authorization","Bearer "+owner);
            try(OutputStream out=c.getOutputStream()){out.write(body.getBytes(StandardCharsets.UTF_8));}
            if(c.getResponseCode()!=200) throw new IOException("Synthetic bridge rejected request.");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)bytes.write(b,0,n);}
            return bytes.toString("UTF-8");
        } finally {c.disconnect();}
    }
    @Test public void androidAndWebStyleClientExchangeEditsConflictsAndDeletions() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();Services.initialize(context);
        File dir=new File(context.getCacheDir(),"cross-client-"+UUID.randomUUID());assertTrue(dir.mkdirs());
        File file=new File(dir,AccountSession.databaseName(A));DataAccessObject dao=new DataAccessObject(file.getPath(),false,A);
        AccountSession.Session saved=new AccountSession.Session(A,A,"synthetic",2000);
        AccountSession accounts=new AccountSession(null,new AccountSession.Store(){public AccountSession.Session load(){return saved;}public void save(AccountSession.Session s){}public void clear(){}},()->1000L);
        dao.open(file.getPath());assertTrue(dao.isOpen());
        try {
            SyncEngine.Local local=new SyncEngine.Local(){
                public List<SyncEngine.Operation> pending(String owner)throws Exception{return dao.syncPending(owner);}
                public void acknowledge(String owner,SyncEngine.Operation op,String response)throws Exception{dao.syncAcknowledge(owner,op,response);}
                public void apply(String owner,String snapshot)throws Exception{dao.syncApply(owner,snapshot);}
            };
            SyncEngine engine=new SyncEngine(accounts,local,new SupabaseSyncRemote((route,token,body)->post(route,token,body)));
            Lead lead=new Lead();lead.setLeadFirstName("AndroidSynthetic");lead.setLeadLastName("Bridge");lead.setLeadPhoneNumber("5550100999");
            assertNull(dao.insertLead(lead));String sync=dao.getLeadIdentity(lead.getLeadID()).syncId;engine.run();
            JSONObject web=new JSONObject(post("web",A,"{}"));assertEquals("AndroidSynthetic",web.getJSONArray("leads").getJSONObject(0).getString("first_name"));
            assertEquals(0,new JSONObject(post("web",B,"{}")).getJSONArray("leads").length());
            post("web",A,new JSONObject().put("action","edit").put("sync_id",sync).put("notes","Web edit").toString());engine.run();
            assertEquals("Web edit",dao.getLeadRandom(lead).get(0).getLeadNotes());
            Lead edited=dao.getLeadRandom(lead).get(0);edited.setLeadNotes("Offline edit");assertNull(dao.updateLead(edited));
            post("web",A,new JSONObject().put("action","edit").put("sync_id",sync).put("notes","Concurrent web edit").toString());engine.run();
            List<String> conflicts=dao.syncConflicts(A);assertEquals(1,conflicts.size());assertEquals("Offline edit",dao.getLeadRandom(lead).get(0).getLeadNotes());
            dao.syncResolve(A,new JSONObject(conflicts.get(0)).getString("operation_id"),false);assertEquals("Concurrent web edit",dao.getLeadRandom(lead).get(0).getLeadNotes());
            post("web",A,new JSONObject().put("action","delete").put("sync_id",sync).toString());engine.run();assertTrue(dao.getLeadRandom(lead).isEmpty());
            post("web",A,"{\"action\":\"create\",\"notes\":\"Created on web\"}");engine.run();
            List<Lead> rows=new ArrayList<>();dao.getLeadSequential(rows);assertEquals(1,rows.size());Lead fromWeb=rows.get(0);assertEquals("WebSynthetic",fromWeb.getLeadFirstName());
            assertNull(dao.deleteLead(fromWeb));engine.run();assertEquals(0,new JSONObject(post("web",A,"{}")).getJSONArray("leads").length());
            assertTrue(dao.syncPending(A).isEmpty());assertTrue(dao.syncConflicts(A).isEmpty());
        } finally {dao.close();SQLiteDatabase.deleteDatabase(file);assertTrue(dir.delete());}
    }
}
