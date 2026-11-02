package com.areonedev.autotrack.sync;

import android.content.Context;
import com.areonedev.autotrack.BuildConfig;
import com.areonedev.autotrack.auth.*;
import com.areonedev.autotrack.application.Services;
import com.areonedev.autotrack.persistence.DataAccess;
import java.util.List;
import java.util.concurrent.*;

/** One process-wide worker; lifecycle resumes and local writes request eventual reconciliation. */
public final class SyncRuntime {
    private static final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> scheduled;
    private static volatile int status=com.areonedev.autotrack.R.string.sync_status_idle;
    private static final java.util.Set<Runnable> listeners=new java.util.concurrent.CopyOnWriteArraySet<>();
    private static final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
    private SyncRuntime() { }
    public static int status() { return status; }
    public static void addStatusListener(Runnable listener) { listeners.add(listener); }
    public static void removeStatusListener(Runnable listener) { listeners.remove(listener); }
    static void setStatus(int value) {
        status=value;
        for(Runnable listener:listeners) main.post(() -> {if(listeners.contains(listener)) listener.run();});
    }
    public static void request(Context context) {
        // Never acquire Services while holding this class lock: DAO writes request sync under Services.
        if(context==null || Services.activeAccount()==null || BuildConfig.SUPABASE_URL.isEmpty()
                || BuildConfig.SUPABASE_PUBLISHABLE_KEY.isEmpty()) return;
        synchronized(SyncRuntime.class) {
            if(scheduled!=null && !scheduled.isDone()) {
                if(scheduled.getDelay(TimeUnit.MILLISECONDS)<=0) return;
                scheduled.cancel(false);
            }
            schedule(context.getApplicationContext(),1);
        }
    }
    private static synchronized void schedule(Context context,int seconds) {
        scheduled=worker.schedule(()-> {
            try {
                AccountSession accounts=AuthRuntime.accounts(context);
                DataAccess data;
                synchronized(accounts) {
                    AccountSession.Session session=accounts.restore();
                    if(session==null || !session.userId.equals(Services.activeAccount())) return;
                    data=Services.getDataAccess("AutoTrack");
                }
                setStatus(com.areonedev.autotrack.R.string.sync_status_running);
                new SyncEngine(accounts,new SyncEngine.Local() {
                    public List<SyncEngine.Operation> pending(String owner) throws Exception {return data.syncPending(owner);}
                    public void acknowledge(String owner,SyncEngine.Operation op,String result) throws Exception {data.syncAcknowledge(owner,op,result);}
                    public void apply(String owner,String snapshot) throws Exception {data.syncApply(owner,snapshot);}
                },new SupabaseSyncRemote(new SupabaseSyncRemote.Http(BuildConfig.SUPABASE_URL,BuildConfig.SUPABASE_PUBLISHABLE_KEY))).run();
                setStatus(com.areonedev.autotrack.R.string.sync_status_complete);
            } catch(Exception unavailable) { setStatus(com.areonedev.autotrack.R.string.sync_status_offline); }
            finally {
                boolean active=Services.activeAccount()!=null;
                synchronized(SyncRuntime.class) {
                    scheduled=null;
                    if(active) schedule(context,60);
                }
            }
        },seconds,TimeUnit.SECONDS);
    }
}
