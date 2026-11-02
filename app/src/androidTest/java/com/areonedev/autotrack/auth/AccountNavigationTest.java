package com.areonedev.autotrack.auth;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.provider.Settings;
import com.areonedev.autotrack.R;
import com.areonedev.autotrack.application.Services;
import com.areonedev.autotrack.presentation.LeadsActivity;
import com.areonedev.autotrack.presentation.AccountSettingsActivity;
import com.areonedev.autotrack.presentation.CalendarActivity;
import com.areonedev.autotrack.presentation.NotificationsActivity;
import com.areonedev.autotrack.presentation.SyncActivity;
import com.areonedev.autotrack.testing.IsolatedDatabase;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

public class AccountNavigationTest {
    @Rule public IsolatedDatabase database = new IsolatedDatabase();

    @Test public void accountEntryShowsCurrentIdentityAndBackRestoresLeads() {
        try (ActivityScenario<LeadsActivity> scenario = ActivityScenario.launch(LeadsActivity.class)) {
            onView(withId(R.id.auth_sign_out)).check(doesNotExist());
            onView(withId(R.id.nav_account)).perform(click());
            onView(withText(Services.activeAccount())).check(matches(isDisplayed()));
            onView(withText("Signed in")).check(matches(isDisplayed()));
            pressBack();
            onView(withId(R.id.header_title)).check(matches(isDisplayed()));
            onView(withId(R.id.nav_leads)).check(matches(isSelected()));
        }
    }

    @Test public void everyTabCanOpenAccountAndRetainsSelectionAfterBackAndRecreation() throws Exception {
        try (ActivityScenario<LeadsActivity> scenario = ActivityScenario.launch(LeadsActivity.class)) {
            capture("leads");
            int[] tabs = {R.id.nav_leads, R.id.nav_calendar, R.id.nav_notifications};
            for (int tab : tabs) {
                onView(withId(tab)).perform(click());
                onView(withId(tab)).check(matches(isSelected()));
                onView(withId(R.id.nav_account)).perform(click());
                onView(withId(R.id.nav_account)).check(matches(isSelected()));
                onView(withId(R.id.nav_account)).perform(click());
                assertResumed(AccountSettingsActivity.class);
                pressBack();
                onView(withId(tab)).check(matches(isSelected()));
            }
            onView(withId(R.id.nav_account)).perform(click());
            recreateResumed();
            onView(withId(R.id.nav_account)).check(matches(isSelected()));
            onView(withText(Services.activeAccount())).check(matches(isDisplayed()));
            capture("account");
            for (int tab : tabs) {
                onView(withId(tab)).perform(click());
                onView(withId(tab)).check(matches(isSelected()));
                onView(withId(R.id.nav_account)).perform(click());
                onView(withId(R.id.nav_account)).check(matches(isSelected()));
            }
        }
    }

    @Test public void settingsOpenExistingSyncAndAndroidNotificationSettings() {
        try (ActivityScenario<AccountSettingsActivity> scenario = ActivityScenario.launch(AccountSettingsActivity.class)) {
            onView(withId(R.id.sync_review)).perform(scrollTo(), click());
            assertResumed(SyncActivity.class);
            pressBack();
            onView(withId(R.id.nav_account)).check(matches(isSelected()));
            Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
            android.content.IntentFilter filter = new android.content.IntentFilter(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(filter, new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true);
            try {
                onView(withId(R.id.account_notifications)).perform(scrollTo(), click());
                assertEquals(1, monitor.getHits());
            } finally { instrumentation.removeMonitor(monitor); }
        }
    }

    @Test public void profileAndSettingsExposeEditableProfileAndChoices() {
        try (ActivityScenario<AccountSettingsActivity> scenario = ActivityScenario.launch(AccountSettingsActivity.class)) {
            onView(withId(R.id.account_profile)).check(matches(withText(org.hamcrest.Matchers.containsString("ada@example.invalid"))));
            onView(withId(R.id.account_edit)).perform(scrollTo(), click());
            onView(withText("Ada")).check(matches(isDisplayed()));
            onView(withText("Cancel")).perform(click());
            onView(withId(R.id.account_appearance)).perform(scrollTo(), click());
            onView(withText("Dark")).check(matches(isDisplayed()));
            onView(withText("Light")).perform(click());
            onView(withId(R.id.account_language)).perform(scrollTo(), click());
            onView(withText("Français")).check(matches(isDisplayed()));
            onView(withText("简体中文")).check(matches(isDisplayed()));
            pressBack();
        }
    }

    @Test public void changingLanguageUpdatesAccountLabels() {
        try (ActivityScenario<AccountSettingsActivity> scenario = ActivityScenario.launch(AccountSettingsActivity.class)) {
            onView(withId(R.id.account_language)).perform(scrollTo(), click());
            onView(withText("简体中文")).perform(click());
            onView(withId(R.id.account_language)).check(matches(withText("语言")));
            onView(withId(R.id.account_language)).perform(scrollTo(), click());
            onView(withText("Français")).perform(click());
            onView(withId(R.id.account_language)).check(matches(withText("Langue")));
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.getEmptyLocaleList()));
        }
    }
    @Test public void editingProfileUpdatesDisplayedValues() {
        try (ActivityScenario<AccountSettingsActivity> scenario = ActivityScenario.launch(AccountSettingsActivity.class)) {
            onView(withId(R.id.account_edit)).perform(scrollTo(), click());
            onView(withHint(R.string.auth_first_name)).perform(replaceText("Grace"), closeSoftKeyboard());
            onView(withHint(R.string.auth_phone)).perform(replaceText("5550199"), closeSoftKeyboard());
            onView(withText(R.string.account_save)).perform(click());
            onView(withId(R.id.account_profile)).check(matches(withText(org.hamcrest.Matchers.containsString("Grace"))));
            onView(withId(R.id.account_profile)).check(matches(withText(org.hamcrest.Matchers.containsString("5550199"))));
        }
    }
    @Test public void darkThemeKeepsProfileAndNavigationVisible() throws Exception {
        try (ActivityScenario<AccountSettingsActivity> scenario = ActivityScenario.launch(AccountSettingsActivity.class)) {
            onView(withId(R.id.account_appearance)).perform(scrollTo(), click());
            onView(withText(R.string.account_dark)).perform(click());
            onView(withId(R.id.account_profile)).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withId(R.id.nav_account)).check(matches(isDisplayed()));
            capture("account-dark");
        } finally {
            InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences("appearance", 0).edit().clear().commit();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM));
        }
    }

    @Test public void signOutClearsSessionAndAccountDataAccess() throws Exception { signOut(false, false); }
    @Test public void remoteSignOutFailureStillLocksAccountAndShowsFeedback() throws Exception { signOut(true, false); }
    @Test public void localSignOutFailureRequiresRetryBeforeLogin() throws Exception { signOut(false, true); }

    private void signOut(boolean remoteFailure, boolean localFailure) throws Exception {
        String owner = Services.activeAccount();
        boolean[] clearFails = {localFailure};
        AccountSession.Session identity = new AccountSession.Session(owner, "private-access", "private-refresh", Long.MAX_VALUE);
        AccountSession.Store store = new AccountSession.Store() {
            AccountSession.Session saved = identity;
            public AccountSession.Session load() { return saved; }
            public void save(AccountSession.Session value) { saved = value; }
            public void clear() throws Exception {
                if (clearFails[0]) throw new IOException("Synthetic local clear failure");
                saved = null;
            }
        };
        int[] signOuts = {0};
        AccountSession.Provider provider = new AccountSession.Provider() {
            public AccountSession.Session signIn(String email, String password) { throw new AssertionError(); }
            public AccountSession.Session refresh(String token) { throw new AssertionError(); }
            public void signOut(String token) throws Exception {
                assertEquals("private-access", token);
                signOuts[0]++;
                if (remoteFailure) throw new IOException("Synthetic remote failure");
            }
        };
        AccountSession accounts = new AccountSession(provider, store, () -> 1000L);
        accounts.restore();
        AuthRuntime.useForTests(accounts, Runnable::run);
        try (ActivityScenario<LeadsActivity> scenario = ActivityScenario.launch(LeadsActivity.class)) {
            onView(withId(R.id.nav_account)).perform(click());
            onView(withText("private-access")).check(doesNotExist());
            onView(withText("private-refresh")).check(doesNotExist());
            onView(withId(R.id.auth_sign_out)).perform(scrollTo(), click());
            assertNull(Services.activeAccount());
            assertNull(accounts.current());
            assertFalse(AuthRuntime.hasAccount());
            assertThrows(IllegalStateException.class, () -> Services.getDataAccess("AutoTrack"));
            if (localFailure) {
                onView(withId(R.id.auth_status)).check(matches(withText(R.string.auth_logout_pending)));
                assertEquals(0, signOuts[0]);
                clearFails[0] = false;
                onView(withId(R.id.auth_retry_logout)).perform(click());
            } else if (remoteFailure) {
                onView(withId(R.id.auth_status)).check(matches(withText(R.string.auth_remote_logout_failed)));
            }
            onView(withId(R.id.auth_sign_in)).check(matches(isDisplayed()));
            onView(withId(R.id.auth_sign_in)).check(matches(isEnabled()));
            assertEquals(1, signOuts[0]);
            assertNull(store.load());
            assertFalse(accounts.isLogoutPending());
            androidx.test.espresso.Espresso.pressBackUnconditionally();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                    assertFalse(activity instanceof LeadsActivity || activity instanceof AccountSettingsActivity);
                }
            });
        }
    }

    @Test public void missingSessionRoutesAccountEntryToLogin() {
        AuthRuntime.useForTests(new AccountSession(null, new AccountSession.Store() {
            public AccountSession.Session load() { return null; }
            public void save(AccountSession.Session value) { }
            public void clear() { }
        }, () -> 1000L), Runnable::run);
        try (ActivityScenario<AccountSettingsActivity> scenario = ActivityScenario.launch(AccountSettingsActivity.class)) {
            onView(withId(R.id.auth_sign_in)).check(matches(isDisplayed()));
            onView(withId(R.id.account_identity)).check(doesNotExist());
            assertNull(Services.activeAccount());
        }
    }

    private void assertResumed(Class<?> expected) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            java.util.Collection<Activity> resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED);
            assertEquals(1, resumed.size());
            assertEquals(expected, resumed.iterator().next().getClass());
        });
    }

    private void recreateResumed() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).iterator().next().recreate());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void capture(String name) throws Exception {
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File file = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null), name + ".png");
        try (FileOutputStream output = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally { bitmap.recycle(); }
    }
}
