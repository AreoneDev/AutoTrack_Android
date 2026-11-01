package com.areonedev.autotrack.auth;

import androidx.test.core.app.ActivityScenario;
import com.areonedev.autotrack.R;
import com.areonedev.autotrack.presentation.LoginActivity;
import org.junit.Test;
import static org.junit.Assert.*;

public class RegistrationWorkflowTest {
    @Test public void registrationCollectsProfileAndShowsConfirmation() {
        final boolean[] called = {false};
        AccountSession.Store store = new AccountSession.Store() {
            public AccountSession.Session load() { return null; }
            public void save(AccountSession.Session session) { fail("Unconfirmed account must not create a session."); }
            public void clear() { }
        };
        AccountSession.Provider provider = new AccountSession.Provider() {
            public AccountSession.Session signIn(String email, String password) { throw new AssertionError(); }
            public AccountSession.Session refresh(String token) { throw new AssertionError(); }
            public void signOut(String token) { }
            public AccountSession.Session signUp(String email, String password, String first, String last, String phone) {
                assertEquals("synthetic@example.invalid", email); assertEquals("Synthetic", first);
                assertEquals("User", last); assertEquals("5550100", phone); called[0] = true; return null;
            }
        };
        AuthRuntime.useForTests(new AccountSession(provider, store, () -> 1000L), Runnable::run);
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            scenario.onActivity(activity -> {
                ((android.widget.EditText) activity.findViewById(R.id.auth_email)).setText("synthetic@example.invalid");
                activity.findViewById(R.id.auth_sign_in).performClick();
                activity.findViewById(R.id.auth_switch_mode).performClick();
                ((android.widget.EditText) activity.findViewById(R.id.auth_first_name)).setText("Synthetic");
                ((android.widget.EditText) activity.findViewById(R.id.auth_last_name)).setText("User");
                ((android.widget.EditText) activity.findViewById(R.id.auth_phone)).setText("5550100");
                ((android.widget.EditText) activity.findViewById(R.id.auth_password)).setText("synthetic-password");
                activity.findViewById(R.id.auth_sign_in).performClick();
                assertTrue(called[0]);
                assertEquals(activity.getString(R.string.auth_confirmation_required),
                        ((android.widget.TextView) activity.findViewById(R.id.auth_status)).getText().toString());
            });
        } finally { AuthRuntime.useForTests(null, null); }
    }
}
