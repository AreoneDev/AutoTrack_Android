package com.areonedev.autotrack.auth;

/** Supplies an authenticated synthetic identity without contacting a server. */
public final class SyntheticAccount {
    public static void install(String userId) throws Exception {
        AccountSession.Session session = new AccountSession.Session(userId, "synthetic-access", "synthetic-refresh", Long.MAX_VALUE);
        AccountSession.Store store = new AccountSession.Store() {
            public AccountSession.Session load() { return session; }
            public void save(AccountSession.Session value) { }
            public void clear() { }
        };
        AccountSession accounts = new AccountSession(new AccountSession.Provider() {
            public AccountSession.Session signIn(String email, String password) { throw new AssertionError("Unexpected network operation."); }
            public AccountSession.Session refresh(String token) { throw new AssertionError("Unexpected network operation."); }
            public void signOut(String token) { }
        }, store, () -> 1000L);
        accounts.restore();
        AuthRuntime.useForTests(accounts, Runnable::run);
    }
    public static void reset() { AuthRuntime.useForTests(null, null); }
}
