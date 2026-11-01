package com.areonedev.autotrack.auth;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import com.areonedev.autotrack.BuildConfig;
import com.areonedev.autotrack.application.Services;
import com.areonedev.autotrack.presentation.LoginActivity;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Process-wide authentication boundary. Authentication requests run off the UI thread. */
public final class AuthRuntime {
    private static AccountSession accounts;
    private static Executor worker = Executors.newSingleThreadExecutor();
    private AuthRuntime() { }
    public static synchronized AccountSession accounts(Context context) {
        if (accounts == null) {
            accounts = new AccountSession(new SupabaseAuthProvider(new SupabaseHttpTransport(
                    BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY), () -> System.currentTimeMillis() / 1000),
                    new SecureSessionStore(context.getApplicationContext(), "auth_session", "AutoTrackAuthSession"),
                    () -> System.currentTimeMillis() / 1000);
        }
        return accounts;
    }
    public static synchronized void execute(Runnable action) { worker.execute(action); }
    static synchronized void useForTests(AccountSession replacement, Executor executor) {
        accounts = replacement;
        worker = executor == null ? Executors.newSingleThreadExecutor() : executor;
    }
    public static synchronized boolean hasAccount() {
        AccountSession.Session session = accounts == null ? null : accounts.current();
        return session != null && session.expiresAt > System.currentTimeMillis() / 1000
                && session.userId.equals(Services.activeAccount());
    }
    public static boolean requireAccount(Activity activity) {
        if (hasAccount()) return true;
        Services.closeDataAccess();
        activity.startActivity(new Intent(activity, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        activity.finish();
        return false;
    }
}
