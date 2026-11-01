package com.areonedev.autotrack.auth;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

public class SecureSessionStoreTest {
    @Test public void isolatedStoreRoundTripsWithoutPlaintextAndRejectsTampering() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String name = "test-session-" + UUID.randomUUID();
        String alias = "test-key-" + UUID.randomUUID();
        try {
            SecureSessionStore store = new SecureSessionStore(context, name, alias);
            store.save(new AccountSession.Session("00000000-0000-0000-0000-000000000001",
                    "synthetic-access", "synthetic-refresh", 2000000000));
            String disk = context.getSharedPreferences(name, Context.MODE_PRIVATE).getString("session", "");
            assertFalse(disk.contains("synthetic"));
            assertEquals("synthetic-refresh", new SecureSessionStore(context, name, alias).load().refreshToken);
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().putBoolean("logout_pending", true).commit();
            try { new SecureSessionStore(context, name, alias).load(); fail("Interrupted sign out must block restore."); }
            catch (AccountSession.LogoutPendingException expected) { }
            store.clear();
            assertNull(store.load());
            store.save(new AccountSession.Session("00000000-0000-0000-0000-000000000001",
                    "synthetic-access", "synthetic-refresh", 2000000000));
            java.security.KeyStore check = java.security.KeyStore.getInstance("AndroidKeyStore");
            check.load(null);
            assertTrue(check.containsAlias(alias));
            store.clear();
            check.load(null);
            assertFalse("Sign out must delete the encryption key.", check.containsAlias(alias));
            store.save(new AccountSession.Session("00000000-0000-0000-0000-000000000001",
                    "synthetic-access", "synthetic-refresh", 2000000000));
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().putString("session", "tampered").commit();
            try { store.load(); fail("Tampered session must not load."); }
            catch (java.io.IOException expected) { }
            store.clear();
            assertNull(store.load());
        } finally {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit();
            java.security.KeyStore keys = java.security.KeyStore.getInstance("AndroidKeyStore");
            keys.load(null); keys.deleteEntry(alias);
        }
    }
}
