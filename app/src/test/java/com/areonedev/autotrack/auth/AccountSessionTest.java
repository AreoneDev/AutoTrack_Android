package com.areonedev.autotrack.auth;

import org.junit.Test;
import static org.junit.Assert.*;

public class AccountSessionTest {
    private static final String A = "00000000-0000-0000-0000-000000000001";
    private static final String B = "00000000-0000-0000-0000-000000000002";
    private final MemoryStore store = new MemoryStore();
    private final FakeProvider provider = new FakeProvider();
    private final AccountSession accounts = new AccountSession(provider, store, () -> 1000L);

    @Test public void coldStartRestoresUnexpiredSession() throws Exception {
        store.value = session(A, 2000);
        assertEquals(A, accounts.restore().userId);
        assertEquals(0, provider.refreshes);
    }
    @Test public void expiredSessionPersistsRotatedTokens() throws Exception {
        store.value = session(A, 900);
        assertEquals("rotated", accounts.restore().refreshToken);
        assertEquals("rotated", store.value.refreshToken);
        assertEquals(1, provider.refreshes);
    }
    @Test public void transientRefreshFailureRetainsDiskSessionButLocksData() throws Exception {
        store.value = session(A, 900);
        provider.failure = new java.io.IOException("offline");
        try { accounts.restore(); fail(); } catch (java.io.IOException expected) { }
        assertNotNull(store.value);
        assertNull(accounts.current());
    }
    @Test public void rejectedRefreshClearsSession() throws Exception {
        store.value = session(A, 900);
        provider.failure = new AccountSession.RejectedSessionException();
        try { accounts.restore(); fail(); } catch (AccountSession.RejectedSessionException expected) { }
        assertNull(store.value);
        assertNull(accounts.current());
    }
    @Test public void failedSecureWriteNeverUnlocksAccount() throws Exception {
        store.failSave = true;
        try { accounts.login("synthetic@example.invalid", "synthetic"); fail(); }
        catch (java.io.IOException expected) { }
        assertNull(accounts.current());
    }
    @Test public void accountSwitchInvalidatesOldWorkAndSeparatesDatabaseNames() throws Exception {
        accounts.login("synthetic@example.invalid", "synthetic");
        long oldGeneration = accounts.generation();
        provider.user = B;
        accounts.login("second@example.invalid", "synthetic");
        assertEquals(B, accounts.current().userId);
        assertFalse(accounts.isCurrent(A, oldGeneration));
        assertNotEquals(AccountSession.databaseName(A), AccountSession.databaseName(B));
        assertFalse(AccountSession.databaseName(A).equals("AutoTrack.db"));
    }
    @Test public void logoutLocksMemoryEvenWhenRevocationFails() throws Exception {
        accounts.login("synthetic@example.invalid", "synthetic");
        provider.failure = new java.io.IOException("offline");
        try { accounts.logout(); fail(); } catch (java.io.IOException expected) { }
        assertNull(accounts.current());
        assertNull(store.value);
    }
    @Test public void arbitraryUserIdsCannotEscapeDatabaseDirectory() {
        try { AccountSession.databaseName("../../AutoTrack"); fail(); }
        catch (IllegalArgumentException expected) { }
    }
    @Test public void interruptedLogoutAfterRestartRequiresRetry() throws Exception {
        store.pendingOnLoad = true;
        try { accounts.restore(); fail(); } catch (AccountSession.LogoutPendingException expected) { }
        assertTrue(accounts.isLogoutPending());
        try { accounts.login("test@example.invalid", "synthetic"); fail(); } catch (IllegalStateException expected) { }
        accounts.logout();
        assertFalse(accounts.isLogoutPending());
    }
    @Test public void failedClearRequiresRetryBeforeRestoreOrLogin() throws Exception {
        accounts.login("synthetic@example.invalid", "synthetic");
        store.failClear = true;
        try { accounts.logout(); fail(); } catch (java.io.IOException expected) { }
        assertTrue(accounts.isLogoutPending());
        assertNull(accounts.current());
        try { accounts.restore(); fail(); } catch (IllegalStateException expected) { }
        try { accounts.login("synthetic@example.invalid", "synthetic"); fail(); } catch (IllegalStateException expected) { }
        store.failClear = false;
        accounts.logout();
        assertFalse(accounts.isLogoutPending());
        assertNull(store.value);
        assertEquals(1, provider.signouts);
    }
    @Test public void refreshCannotChangeAccountIdentity() throws Exception {
        store.value = session(A, 900);
        provider.user = B;
        try { accounts.restore(); fail(); } catch (AccountSession.RejectedSessionException expected) { }
        assertNull(accounts.current());
        assertNull(store.value);
    }
    @Test public void alreadyExpiredLoginResponseCannotUnlockData() throws Exception {
        provider.expiry = 900;
        try { accounts.login("synthetic@example.invalid", "synthetic"); fail(); }
        catch (AccountSession.RejectedSessionException expected) { }
        assertNull(accounts.current());
        assertNull(store.value);
    }
    @Test public void expiredRefreshResponseCannotUnlockData() throws Exception {
        store.value = session(A, 900);
        provider.expiry = 900;
        try { accounts.restore(); fail(); }
        catch (AccountSession.RejectedSessionException expected) { }
        assertNull(accounts.current());
        assertNull(store.value);
    }
    @Test public void concurrentRestoresRotateRefreshTokenOnlyOnce() throws Exception {
        store.value = session(A, 900);
        java.util.concurrent.ExecutorService workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Future<AccountSession.Session> first = workers.submit(() -> accounts.restore());
            java.util.concurrent.Future<AccountSession.Session> second = workers.submit(() -> accounts.restore());
            assertEquals("rotated", first.get(5, java.util.concurrent.TimeUnit.SECONDS).refreshToken);
            assertEquals("rotated", second.get(5, java.util.concurrent.TimeUnit.SECONDS).refreshToken);
            assertEquals(1, provider.refreshes);
        } finally { workers.shutdownNow(); }
    }
    private static AccountSession.Session session(String user, long expiry) {
        return new AccountSession.Session(user, "synthetic-access", "synthetic-refresh", expiry);
    }
    private static class MemoryStore implements AccountSession.Store {
        AccountSession.Session value;
        boolean failSave;
        boolean failClear;
        boolean pendingOnLoad;
        public AccountSession.Session load() throws Exception { if (pendingOnLoad) throw new AccountSession.LogoutPendingException(); return value; }
        public void save(AccountSession.Session session) throws Exception {
            if (failSave) throw new java.io.IOException("secure storage unavailable");
            value = session;
        }
        public void clear() throws Exception { if (failClear) throw new java.io.IOException("clear failed"); value = null; }
    }
    private static class FakeProvider implements AccountSession.Provider {
        int refreshes;
        int signouts;
        long expiry = 2000;
        String user = A;
        Exception failure;
        public AccountSession.Session signIn(String email, String password) { return session(user, expiry); }
        public AccountSession.Session refresh(String token) throws Exception {
            refreshes++;
            if (failure != null) throw failure;
            return new AccountSession.Session(user, "new-access", "rotated", expiry);
        }
        public void signOut(String token) throws Exception { signouts++; if (failure != null) throw failure; }
    }
}
