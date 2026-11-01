package com.areonedev.autotrack.auth;

import java.util.Objects;

public final class LoginService {
    public interface AuthProvider {
        Session signIn(String email, String password) throws Exception;
    }

    public interface SessionStore {
        void save(Session session) throws Exception;
        void clear() throws Exception;
    }

    public static final class Session {
        public final String userId;
        public final String accessToken;
        public final String refreshToken;

        public Session(String userId, String accessToken, String refreshToken) {
            this.userId = Objects.requireNonNull(userId);
            this.accessToken = Objects.requireNonNull(accessToken);
            this.refreshToken = Objects.requireNonNull(refreshToken);
        }
    }

    private final AuthProvider provider;
    private final SessionStore store;

    public LoginService(AuthProvider provider, SessionStore store) {
        this.provider = Objects.requireNonNull(provider);
        this.store = Objects.requireNonNull(store);
    }

    public void login(String email, String password) throws Exception {
        Session session = Objects.requireNonNull(provider.signIn(email, password));
        store.save(session);
    }

    public void logout() throws Exception {
        store.clear();
    }
}
