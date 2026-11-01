package com.areonedev.autotrack.auth;

import org.junit.Test;
import static org.junit.Assert.*;

public class LoginServiceTest {
    @Test public void successfulLoginPersistsSession() throws Exception {
        FakeStore store = new FakeStore();
        LoginService service = new LoginService((email, password) -> new LoginService.Session("user-1", "access", "refresh"), store);
        service.login("person@example.com", "password");
        assertEquals("user-1", store.saved.userId);
        assertEquals("refresh", store.saved.refreshToken);
    }

    @Test public void failedLoginDoesNotReplaceSession() throws Exception {
        FakeStore store = new FakeStore();
        store.saved = new LoginService.Session("existing", "old", "old-refresh");
        LoginService service = new LoginService((email, password) -> { throw new Exception("Invalid credentials"); }, store);
        try { service.login("person@example.com", "wrong"); fail("Expected login failure"); }
        catch (Exception expected) { assertEquals("Invalid credentials", expected.getMessage()); }
        assertEquals("existing", store.saved.userId);
    }

    @Test public void logoutRemovesStoredSession() throws Exception {
        FakeStore store = new FakeStore();
        store.saved = new LoginService.Session("user-1", "access", "refresh");
        new LoginService((email, password) -> null, store).logout();
        assertNull(store.saved);
    }

    private static class FakeStore implements LoginService.SessionStore {
        LoginService.Session saved;
        public void save(LoginService.Session session) { saved = session; }
        public void clear() { saved = null; }
    }
}
