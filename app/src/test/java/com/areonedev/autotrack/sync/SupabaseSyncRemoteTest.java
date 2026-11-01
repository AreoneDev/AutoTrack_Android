package com.areonedev.autotrack.sync;
import com.areonedev.autotrack.auth.AccountSession;
import org.junit.Test;
import static org.junit.Assert.*;
public class SupabaseSyncRemoteTest {
    final AccountSession.Session session=new AccountSession.Session("00000000-0000-0000-0000-000000000001","synthetic","synthetic",2000);
    @Test public void sendsExactFrozenOperationToRpc() throws Exception {
        SupabaseSyncRemote remote=new SupabaseSyncRemote((path,token,body)-> {
            assertEquals("autotrack_sync_lead",path);assertEquals("synthetic",token);
            assertEquals("{\"p_operation\":{\"operation_id\":\"stable\"}}",body);return "receipt";
        });
        assertEquals("receipt",remote.push(session,new SyncEngine.Operation("stable","{\"operation_id\":\"stable\"}")));
    }
    @Test public void snapshotUsesAtomicSnapshotRpc() throws Exception {
        assertEquals("snapshot",new SupabaseSyncRemote((path,token,body)-> {
            assertEquals("autotrack_sync_snapshot",path);assertEquals("{}",body);return "snapshot";
        }).snapshot(session));
    }
}
