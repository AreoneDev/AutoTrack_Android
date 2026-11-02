package com.areonedev.autotrack.auth;

import java.io.IOException;
import java.util.*;
import java.util.function.LongSupplier;

/** Auth REST adapter; errors never include response bodies or credentials. */
public final class SupabaseAuthProvider implements AccountSession.Provider {
    public interface Transport {
        Response post(String path, String bearer, Map<String, String> body) throws IOException;
        default Response get(String path, String bearer) throws IOException { throw new UnsupportedOperationException(); }
        default Response put(String path, String bearer, Map<String, String> body) throws IOException { throw new UnsupportedOperationException(); }
    }
    public static final class Response {
        public final int status;
        public final Map<String, Object> body;
        public Response(int status, Map<String, Object> body) { this.status = status; this.body = body; }
    }
    private final Transport transport;
    private final LongSupplier clock;
    public SupabaseAuthProvider(Transport transport, LongSupplier clock) {
        this.transport = transport; this.clock = clock;
    }
    public AccountSession.Session signIn(String email, String password) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("email", email.trim()); body.put("password", password);
        return session(transport.post("/token?grant_type=password", null, body));
    }
    public AccountSession.Session refresh(String token) throws Exception {
        return session(transport.post("/token?grant_type=refresh_token", null,
                Collections.singletonMap("refresh_token", token)));
    }
    public AccountSession.Session signUp(String email, String password) throws Exception {
        return signUp(email, password, "", "", "");
    }
    public AccountSession.Session signUp(String email, String password, String first, String last, String phone) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("email", email.trim()); body.put("password", password);
        body.put("data.first_name", first.trim()); body.put("data.last_name", last.trim()); body.put("data.phone", phone.trim());
        Response response = transport.post("/signup", null, body);
        check(response);
        if (!response.body.containsKey("access_token") && !response.body.containsKey("refresh_token")) return null;
        return session(response);
    }
    public void signOut(String token) throws Exception {
        check(transport.post("/logout?scope=local", token, Collections.emptyMap()));
    }
    public AccountSession.Profile profile(String token) throws Exception { return profileResponse(transport.get("/user", token)); }
    public AccountSession.Profile updateProfile(String token, String first, String last, String phone) throws Exception {
        Map<String, String> fields = new HashMap<>();
        fields.put("data.first_name", first.trim()); fields.put("data.last_name", last.trim()); fields.put("data.phone", phone.trim());
        return profileResponse(transport.put("/user", token, fields));
    }
    private AccountSession.Profile profileResponse(Response response) throws Exception {
        check(response);
        try {
            Map<?, ?> metadata = response.body.get("user_metadata") instanceof Map ? (Map<?, ?>) response.body.get("user_metadata") : Collections.emptyMap();
            return new AccountSession.Profile((String) response.body.get("id"), value(metadata, "first_name"), value(metadata, "last_name"), value(metadata, "phone"), value(response.body, "email"));
        } catch (RuntimeException malformed) { throw new IOException("Invalid account response."); }
    }
    private static String value(Map<?, ?> map, String key) { Object value = map.get(key); return value instanceof String ? (String) value : ""; }
    private static void check(Response response) throws Exception {
        if (response.status >= 200 && response.status < 300) return;
        if (response.status == 400 || response.status == 401 || response.status == 403 || response.status == 422)
            throw new AccountSession.RejectedSessionException();
        throw new IOException("Unable to reach the sign-in service. Please try again.");
    }
    private AccountSession.Session session(Response response) throws Exception {
        check(response);
        try {
            Map<?, ?> user = (Map<?, ?>) response.body.get("user");
            long expiresAt = response.body.containsKey("expires_at")
                    ? ((Number) response.body.get("expires_at")).longValue()
                    : clock.getAsLong() + ((Number) response.body.get("expires_in")).longValue();
            return new AccountSession.Session((String) user.get("id"),
                    (String) response.body.get("access_token"), (String) response.body.get("refresh_token"), expiresAt);
        } catch (RuntimeException malformed) { throw new IOException("Invalid sign-in response."); }
    }
}
