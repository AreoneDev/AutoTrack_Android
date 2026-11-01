package com.areonedev.autotrack.auth;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class SupabaseAuthProviderTest {
    @Test public void signupCarriesSyntheticProfileFields() throws Exception {
        SupabaseAuthProvider provider = new SupabaseAuthProvider((path, token, body) -> {
            assertEquals("Ada", body.get("data.first_name"));
            assertEquals("Example", body.get("data.last_name"));
            assertEquals("5550100", body.get("data.phone"));
            return new SupabaseAuthProvider.Response(200, Collections.emptyMap());
        }, () -> 1000L);
        assertNull(provider.signUp("qa@example.invalid", "synthetic", "Ada", "Example", "5550100"));
    }
    @Test public void registrationWithoutSessionRequiresEmailConfirmation() throws Exception {
        SupabaseAuthProvider provider = new SupabaseAuthProvider((path, token, body) -> {
            assertEquals("/signup", path);
            assertEquals("new@example.invalid", body.get("email"));
            return new SupabaseAuthProvider.Response(200, Collections.emptyMap());
        }, () -> 1000L);
        assertNull(provider.signUp("new@example.invalid", "synthetic-password"));
    }
    @Test public void registrationWithSessionReturnsAuthenticatedAccount() throws Exception {
        SupabaseAuthProvider provider = new SupabaseAuthProvider((path, token, body) -> {
            Map<String, Object> result = new HashMap<>();
            result.put("user", Collections.singletonMap("id", "00000000-0000-0000-0000-000000000001"));
            result.put("access_token", "synthetic-access"); result.put("refresh_token", "synthetic-refresh");
            result.put("expires_in", 3600);
            return new SupabaseAuthProvider.Response(200, result);
        }, () -> 1000L);
        assertNotNull(provider.signUp("new@example.invalid", "synthetic-password"));
    }
    @Test public void passwordGrantAndRefreshUseExpectedPayloads() throws Exception {
        List<String> paths = new ArrayList<>();
        List<Map<String, String>> bodies = new ArrayList<>();
        SupabaseAuthProvider provider = new SupabaseAuthProvider((path, bearer, body) -> {
            paths.add(path); bodies.add(body);
            Map<String, Object> response = new HashMap<>();
            response.put("user", Collections.singletonMap("id", "00000000-0000-0000-0000-000000000001"));
            response.put("access_token", "synthetic-access");
            response.put("refresh_token", "synthetic-refresh");
            response.put("expires_in", 3600);
            return new SupabaseAuthProvider.Response(200, response);
        }, () -> 1000L);
        assertEquals(4600, provider.signIn("qa@example.invalid", "synthetic").expiresAt);
        provider.refresh("synthetic-refresh");
        assertEquals("/token?grant_type=password", paths.get(0));
        assertEquals("qa@example.invalid", bodies.get(0).get("email"));
        assertEquals("/token?grant_type=refresh_token", paths.get(1));
        assertEquals("synthetic-refresh", bodies.get(1).get("refresh_token"));
        assertFalse(bodies.get(1).containsKey("password"));
    }
    @Test public void logoutRevokesOnlyCurrentSession() throws Exception {
        new SupabaseAuthProvider((path, token, body) -> {
            assertEquals("/logout?scope=local", path);
            assertEquals("synthetic-access", token);
            return new SupabaseAuthProvider.Response(204, Collections.emptyMap());
        }, () -> 1000L).signOut("synthetic-access");
    }
    @Test public void refreshDistinguishesRejectionFromRetryableOutage() throws Exception {
        for (int status : new int[]{400, 401, 403, 422, 429, 500}) {
            SupabaseAuthProvider provider = new SupabaseAuthProvider((p, t, b) ->
                new SupabaseAuthProvider.Response(status, Collections.emptyMap()), () -> 1000L);
            try { provider.refresh("synthetic-refresh"); fail(); }
            catch (AccountSession.RejectedSessionException rejected) { assertTrue(status < 429); }
            catch (java.io.IOException retryable) { assertTrue(status >= 429); }
        }
    }
    @Test public void incompleteSuccessNeverCreatesSession() throws Exception {
        SupabaseAuthProvider provider = new SupabaseAuthProvider((p,t,b) ->
                new SupabaseAuthProvider.Response(200, Collections.emptyMap()), () -> 1000L);
        try { provider.signIn("qa@example.invalid", "synthetic"); fail(); }
        catch (java.io.IOException expected) { }
    }
}
