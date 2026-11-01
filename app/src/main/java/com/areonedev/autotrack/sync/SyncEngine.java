package com.areonedev.autotrack.sync;

import com.areonedev.autotrack.auth.AccountSession;
import java.util.List;

/** Network work never holds the session lock; each local commit rechecks its generation. */
public final class SyncEngine {
    public static final class Operation {
        public final String id, body;
        public Operation(String id,String body) { this.id=id; this.body=body; }
    }
    public interface Local {
        List<Operation> pending(String owner) throws Exception;
        void acknowledge(String owner,Operation op,String result) throws Exception;
        void apply(String owner,String snapshot) throws Exception;
    }
    public interface Remote {
        String push(AccountSession.Session session,Operation op) throws Exception;
        String snapshot(AccountSession.Session session) throws Exception;
    }
    private final AccountSession accounts;
    private final Local local;
    private final Remote remote;
    public SyncEngine(AccountSession accounts,Local local,Remote remote) { this.accounts=accounts;this.local=local;this.remote=remote; }
    public synchronized void run() throws Exception {
        AccountSession.Session session; long generation; List<Operation> pending;
        synchronized(accounts) {
            session=accounts.restore(); if(session==null) return;
            generation=accounts.generation();pending=local.pending(session.userId);
        }
        for(Operation op:pending) {
            synchronized(accounts) { if(!accounts.isCurrent(session.userId,generation)) return; }
            String result=remote.push(session,op);
            synchronized(accounts) {
                if(!accounts.isCurrent(session.userId,generation)) return;
                local.acknowledge(session.userId,op,result);
            }
        }
        synchronized(accounts) { if(!accounts.isCurrent(session.userId,generation)) return; }
        String snapshot=remote.snapshot(session);
        synchronized(accounts) {
            if(accounts.isCurrent(session.userId,generation)) local.apply(session.userId,snapshot);
        }
    }
}
