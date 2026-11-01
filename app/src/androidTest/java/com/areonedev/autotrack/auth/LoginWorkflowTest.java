package com.areonedev.autotrack.auth;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.areonedev.autotrack.R;
import com.areonedev.autotrack.application.Services;
import com.areonedev.autotrack.presentation.LoginActivity;
import org.junit.Test;
import java.util.UUID;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static org.junit.Assert.*;

public class LoginWorkflowTest {
    @Test public void passwordLoginOpensOnlySyntheticAccountDatabase() throws Exception {
        String user = UUID.randomUUID().toString();
        AccountSession.Store store = new AccountSession.Store() {
            AccountSession.Session session;
            public AccountSession.Session load() { return session; }
            public void save(AccountSession.Session value) { session = value; }
            public void clear() { session = null; }
        };
        AccountSession.Provider provider = new AccountSession.Provider() {
            public AccountSession.Session signIn(String email, String password) {
                assertEquals("synthetic@example.invalid", email);
                return new AccountSession.Session(user, "synthetic-access", "synthetic-refresh", Long.MAX_VALUE);
            }
            public AccountSession.Session refresh(String token) { throw new AssertionError(); }
            public void signOut(String token) { }
        };
        AuthRuntime.useForTests(new AccountSession(provider, store, () -> 1000L), Runnable::run);
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.auth_email)).perform(typeText("synthetic@example.invalid"), closeSoftKeyboard());
            onView(withId(R.id.auth_sign_in)).perform(click());
            onView(withId(R.id.auth_password)).perform(typeText("synthetic"), closeSoftKeyboard());
            onView(withId(R.id.auth_sign_in)).perform(click());
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertEquals(user, Services.activeAccount());
            onView(withId(R.id.auth_sign_out)).perform(click());
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertNull(Services.activeAccount());
            onView(withId(R.id.auth_sign_in)).check(matches(isDisplayed()));
        } finally {
            Services.closeDataAccess();
            InstrumentationRegistry.getInstrumentation().getTargetContext().deleteDatabase(AccountSession.databaseName(user));
            AuthRuntime.useForTests(null, null);
        }
    }
}
