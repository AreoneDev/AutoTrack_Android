package com.areonedev.autotrack.auth;

import java.util.UUID;
import java.util.function.LongSupplier;

/** Serializes session rotation and identifies work belonging to an account generation. */
public final class AccountSession {
    public interface Provider {
        Session signIn(String email, String password) throws Exception;
        default Session signUp(String email, String password) throws Exception { throw new UnsupportedOperationException(); }
        default Session signUp(String email, String password, String first, String last, String phone) throws Exception { return signUp(email, password); }
        Session refresh(String refreshToken) throws Exception;
        void signOut(String accessToken) throws Exception;
        default Profile profile(String accessToken) throws Exception { throw new UnsupportedOperationException(); }
        default Profile updateProfile(String accessToken, String first, String last, String phone) throws Exception { throw new UnsupportedOperationException(); }
    }
    public interface Store {
        Session load() throws Exception;
        void save(Session session) throws Exception;
        void clear() throws Exception;
    }
    public static final class RejectedSessionException extends Exception {
        public RejectedSessionException() { super("Please sign in again."); }
    }
    public static final class LogoutPendingException extends java.io.IOException {
        public LogoutPendingException() { super("Finish signing out first."); }
    }
    public static final class Session {
        public final String userId, accessToken, refreshToken;
        public final long expiresAt;
        public Session(String userId, String accessToken, String refreshToken, long expiresAt) {
            databaseName(userId);
            if (accessToken == null || accessToken.isEmpty() || refreshToken == null
                    || refreshToken.isEmpty() || expiresAt <= 0) throw new IllegalArgumentException("Invalid session.");
            this.userId = userId;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresAt = expiresAt;
        }
    }
    public static final class Profile {
        public final String userId, firstName, lastName, phone, email;
        public Profile(String userId, String firstName, String lastName, String phone, String email) {
            databaseName(userId);
            this.userId = userId; this.firstName = firstName; this.lastName = lastName; this.phone = phone; this.email = email;
        }
    }
    private final Provider provider;
    private final Store store;
    private final LongSupplier clock;
    private Session current;
    private long generation;
    private boolean logoutPending;
    private Session logoutSession;
    public AccountSession(Provider provider, Store store, LongSupplier clock) {
        this.provider = provider;
        this.store = store;
        this.clock = clock;
    }
    public synchronized Session current() { return current; }
    public synchronized boolean isLogoutPending() { return logoutPending; }
    public synchronized long generation() { return generation; }
    public Profile profile() throws Exception {
        Session session; long ticket;
        synchronized (this) { session = current; ticket = generation; }
        if (session == null || session.expiresAt <= clock.getAsLong()) throw new RejectedSessionException();
        Profile result = provider.profile(session.accessToken);
        synchronized (this) {
            if (!isCurrent(session.userId, ticket) || !session.userId.equals(result.userId)) throw new RejectedSessionException();
        }
        return result;
    }
    public Profile updateProfile(String first, String last, String phone) throws Exception {
        if (first.trim().isEmpty() || last.trim().isEmpty() || phone.trim().isEmpty()) throw new IllegalArgumentException();
        Session session; long ticket;
        synchronized (this) { session = current; ticket = generation; }
        if (session == null || session.expiresAt <= clock.getAsLong()) throw new RejectedSessionException();
        Profile result = provider.updateProfile(session.accessToken, first, last, phone);
        synchronized (this) {
            if (!isCurrent(session.userId, ticket) || !session.userId.equals(result.userId)) throw new RejectedSessionException();
        }
        return result;
    }
    public synchronized boolean isCurrent(String user, long ticket) {
        return current != null && current.userId.equals(user) && ticket == generation;
    }
    public synchronized Session login(String email, String password) throws Exception {
        if (logoutPending) throw new IllegalStateException("Finish signing out first.");
        lock();
        Session session = provider.signIn(email, password);
        if (session.expiresAt <= clock.getAsLong()) throw new RejectedSessionException();
        store.save(session);
        current = session;
        return session;
    }
    public synchronized Session register(String email, String password) throws Exception {
        return register(email, password, "", "", "");
    }
    public synchronized Session register(String email, String password, String first, String last, String phone) throws Exception {
        if (logoutPending) throw new IllegalStateException("Finish signing out first.");
        lock();
        Session session = provider.signUp(email, password, first, last, phone);
        if (session != null) {
            if (session.expiresAt <= clock.getAsLong()) throw new RejectedSessionException();
            store.save(session);
            current = session;
        }
        return session;
    }
    public synchronized Session restore() throws Exception {
        if (logoutPending) throw new IllegalStateException("Finish signing out first.");
        if (current != null && current.expiresAt > clock.getAsLong() + 60) return current;
        lock();
        Session saved;
        try { saved = store.load(); }
        catch (LogoutPendingException pending) { logoutPending = true; throw pending; }
        if (saved == null) return null;
        try {
            if (saved.expiresAt <= clock.getAsLong() + 60) {
                Session refreshed = provider.refresh(saved.refreshToken);
                if (!refreshed.userId.equals(saved.userId) || refreshed.expiresAt <= clock.getAsLong())
                    throw new RejectedSessionException();
                store.save(refreshed);
                saved = refreshed;
            }
            current = saved;
            return saved;
        } catch (RejectedSessionException rejected) {
            store.clear();
            throw rejected;
        }
    }
    public synchronized void logout() throws Exception {
        if (!logoutPending) logoutSession = current;
        lock();
        logoutPending = true;
        store.clear();
        logoutPending = false;
        Session previous = logoutSession;
        logoutSession = null;
        if (previous != null) provider.signOut(previous.accessToken);
    }
    private void lock() { current = null; generation++; }
    public static String databaseName(String userId) {
        String canonical = UUID.fromString(userId).toString();
        if (!canonical.equals(userId)) throw new IllegalArgumentException("Invalid account identifier.");
        return "account-" + canonical + ".db";
    }
}
