package com.areonedev.autotrack.auth;

import android.content.Intent;
import android.widget.EditText;
import android.widget.ProgressBar;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.areonedev.autotrack.R;
import com.areonedev.autotrack.presentation.LoginActivity;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;
import org.junit.After;
import org.junit.Test;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LoginSessionLoadingTest {
    private final Queue<Runnable> pending = new ArrayDeque<>();

    @After public void resetRuntime() {
        AuthRuntime.useForTests(null, null);
    }

    @Test public void sessionCheckHidesCredentialsUntilNoSavedSessionIsFound() {
        useStore(null);
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            assertChecking();
            scenario.onActivity(activity -> pending.remove().run());
            onView(withId(R.id.auth_email)).check(matches(isDisplayed()));
            onView(withId(R.id.auth_sign_in)).check(matches(isEnabled()));
            onView(isAssignableFrom(ProgressBar.class)).check(matches(not(isDisplayed())));
            scenario.onActivity(activity -> activity.finish());
        }
    }

    @Test public void failedSessionCheckRestoresFormAndShowsError() {
        useStore(new IOException("Synthetic session read failure"));
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            assertChecking();
            scenario.onActivity(activity -> pending.remove().run());
            onView(withId(R.id.auth_email)).check(matches(isDisplayed()));
            onView(withId(R.id.auth_sign_in)).check(matches(isEnabled()));
            onView(withId(R.id.auth_status)).check(matches(withText(R.string.auth_restore_failed)));
            onView(isAssignableFrom(ProgressBar.class)).check(matches(not(isDisplayed())));
            scenario.onActivity(activity -> activity.finish());
        }
    }

    @Test public void signedOutLaunchShowsFormWithoutCheckingSession() {
        useStore(null);
        Intent intent = new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra("signed_out", true);
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> assertTrue(activity.getIntent().getBooleanExtra("signed_out", false)));
            onView(withId(R.id.auth_email)).check(matches(isDisplayed()));
            onView(withId(R.id.auth_sign_in)).check(matches(isEnabled()));
            onView(isAssignableFrom(ProgressBar.class)).check(matches(not(isDisplayed())));
            assertTrue(pending.isEmpty());
        }
    }

    @Test public void loginCheckHidesCredentialsAndRestoresThemAfterRejection() {
        AccountSession.Provider provider = new AccountSession.Provider() {
            public AccountSession.Session signIn(String email, String password) throws Exception {
                throw new AccountSession.RejectedSessionException();
            }
            public AccountSession.Session refresh(String token) { throw new AssertionError(); }
            public void signOut(String token) { throw new AssertionError(); }
        };
        AuthRuntime.useForTests(new AccountSession(provider, store(null), () -> 1000L), pending::add);
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            scenario.onActivity(activity -> {
                pending.remove().run();
                ((EditText) activity.findViewById(R.id.auth_email)).setText("synthetic@example.invalid");
                activity.findViewById(R.id.auth_sign_in).performClick();
                ((EditText) activity.findViewById(R.id.auth_password)).setText("synthetic-password");
                activity.findViewById(R.id.auth_sign_in).performClick();
            });
            assertChecking();
            scenario.onActivity(activity -> pending.remove().run());
            onView(withId(R.id.auth_email)).check(matches(isDisplayed()));
            onView(withId(R.id.auth_email)).check(matches(withText("synthetic@example.invalid")));
            onView(withId(R.id.auth_password)).check(matches(isDisplayed()));
            onView(withId(R.id.auth_password)).check(matches(withText("")));
            onView(withId(R.id.auth_sign_in)).check(matches(isEnabled()));
            onView(withId(R.id.auth_status)).check(matches(withText(R.string.auth_credentials_rejected)));
            onView(isAssignableFrom(ProgressBar.class)).check(matches(not(isDisplayed())));
        }
    }

    @Test public void pendingLogoutReplacesLoadingWithRetryAndReturnsToForm() {
        useStore(new AccountSession.LogoutPendingException());
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            assertChecking();
            scenario.onActivity(activity -> pending.remove().run());
            onView(withId(R.id.auth_retry_logout)).check(matches(isDisplayed()));
            onView(withId(R.id.auth_retry_logout)).check(matches(isEnabled()));
            onView(withId(R.id.auth_status)).check(matches(withText(R.string.auth_logout_pending)));
            onView(isAssignableFrom(ProgressBar.class)).check(matches(not(isDisplayed())));
            onView(withId(R.id.auth_sign_in)).check(matches(not(isEnabled())));
            onView(withId(R.id.auth_retry_logout)).perform(click());
            scenario.onActivity(activity -> pending.remove().run());
            onView(withId(R.id.auth_retry_logout)).check(matches(not(isDisplayed())));
            onView(withId(R.id.auth_email)).check(matches(isDisplayed()));
            onView(withId(R.id.auth_sign_in)).check(matches(isEnabled()));
        }
    }

    private void assertChecking() {
        onView(withId(R.id.auth_email)).check(matches(not(isDisplayed())));
        onView(withId(R.id.auth_password)).check(matches(not(isDisplayed())));
        onView(withId(R.id.auth_sign_in)).check(matches(not(isDisplayed())));
        onView(withId(R.id.auth_switch_mode)).check(matches(not(isDisplayed())));
        onView(allOf(withText(R.string.auth_checking), isDisplayed())).check(matches(isDisplayed()));
        onView(isAssignableFrom(ProgressBar.class)).check(matches(isDisplayed()));
        assertFalse(pending.isEmpty());
    }

    private AccountSession.Store store(Exception readFailure) {
        return new AccountSession.Store() {
            public AccountSession.Session load() throws Exception {
                if (readFailure != null) throw readFailure;
                return null;
            }
            public void save(AccountSession.Session session) { }
            public void clear() { }
        };
    }

    private void useStore(Exception readFailure) {
        AccountSession.Provider provider = new AccountSession.Provider() {
            public AccountSession.Session signIn(String email, String password) { throw new AssertionError(); }
            public AccountSession.Session refresh(String token) { throw new AssertionError(); }
            public void signOut(String token) { throw new AssertionError(); }
        };
        AuthRuntime.useForTests(new AccountSession(provider, store(readFailure), () -> 1000L), pending::add);
    }
}
