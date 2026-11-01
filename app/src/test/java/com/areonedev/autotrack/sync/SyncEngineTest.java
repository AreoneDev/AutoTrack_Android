package com.areonedev.autotrack.sync;

import com.areonedev.autotrack.auth.AccountSession;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class SyncEngineTest {
    static final String A = "00000000-0000-0000-0000-000000000001";
    static final String B = "00000000-0000-0000-0000-000000000002";
    final Memory local = new Memory();
    final AccountSession accounts = new AccountSession(new AccountSession.Provider() {
        public AccountSession.Session signIn(String email,String password) { return session(email); }
        public AccountSession.Session refresh(String token) { return session(A); }
        public void signOut(String token) { }
    }, new AccountSession.Store() {
        public AccountSession.Session load() { return session(A); }
        public void save(AccountSession.Session s) { }
        public void clear() { }
    }, () -> 1000L);
    static AccountSession.Session session(String owner) { return new AccountSession.Session(owner,"synthetic","synthetic",2000); }
    @Test public void transientFailureLeavesOperationForIdenticalReplay() throws Exception {
        accounts.restore();
        SyncEngine.Remote remote = new SyncEngine.Remote() {
            public String push(AccountSession.Session s, SyncEngine.Operation op) throws Exception { throw new java.io.IOException("offline"); }
            public String snapshot(AccountSession.Session s) { fail(); return ""; }
        };
        try { new SyncEngine(accounts,local,remote).run(); fail(); } catch(java.io.IOException expected) { }
        assertEquals(1,local.pending.size()); assertEquals(0,local.acks);
    }
    @Test public void uploadThenDownloadConvergesAndAcknowledgesExactOperation() throws Exception {
        accounts.restore();
        new SyncEngine(accounts,local,new SyncEngine.Remote() {
            public String push(AccountSession.Session s,SyncEngine.Operation op) { assertEquals("op-1",op.id); return "ack"; }
            public String snapshot(AccountSession.Session s) { return "snapshot"; }
        }).run();
        assertEquals(1,local.acks); assertEquals("snapshot",local.applied); assertTrue(local.pending.isEmpty());
    }
    @Test public void accountSwitchDuringRequestCannotAcknowledgeOrDownload() throws Exception {
        accounts.restore();
        new SyncEngine(accounts,local,new SyncEngine.Remote() {
            public String push(AccountSession.Session s,SyncEngine.Operation op) throws Exception { accounts.login(B,"synthetic"); return "ack"; }
            public String snapshot(AccountSession.Session s) { fail(); return ""; }
        }).run();
        assertEquals(0,local.acks); assertEquals(1,local.pending.size()); assertNull(local.applied);
    }
    @Test public void accountSwitchDuringSnapshotCannotApplyIt() throws Exception {
        local.pending.clear(); accounts.restore();
        new SyncEngine(accounts,local,new SyncEngine.Remote() {
            public String push(AccountSession.Session s,SyncEngine.Operation op) { fail(); return ""; }
            public String snapshot(AccountSession.Session s) throws Exception { accounts.login(B,"synthetic"); return "old-account"; }
        }).run();
        assertNull(local.applied);
    }
    @Test public void signedOutRunDoesNotReadLocalData() throws Exception {
        AccountSession empty = new AccountSession(null,new AccountSession.Store() {
            public AccountSession.Session load() { return null; }
            public void save(AccountSession.Session s) { }
            public void clear() { }
        }, () -> 1000L);
        new SyncEngine(empty,local,null).run(); assertEquals(0,local.reads);
    }
    static final class Memory implements SyncEngine.Local {
        int acks,reads; String applied;
        List<SyncEngine.Operation> pending = new ArrayList<>(Arrays.asList(new SyncEngine.Operation("op-1","{}")));
        public List<SyncEngine.Operation> pending(String owner) { assertEquals(A,owner); reads++; return new ArrayList<>(pending); }
        public void acknowledge(String owner,SyncEngine.Operation op,String result) { assertEquals(A,owner); assertEquals("ack",result); pending.remove(op); acks++; }
        public void apply(String owner,String snapshot) { assertEquals(A,owner); applied=snapshot; }
    }
}
