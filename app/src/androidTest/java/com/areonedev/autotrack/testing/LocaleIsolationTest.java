package com.areonedev.autotrack.testing;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.test.core.app.ActivityScenario;
import com.areonedev.autotrack.presentation.LeadsActivity;
import com.areonedev.autotrack.R;
import org.junit.Test;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;

/** Reproduces the boundary between two tests in the same instrumentation process. */
public class LocaleIsolationTest {
    @Test public void nextFixtureRestoresEnglishAfterLastActivityCloses() throws Throwable {
        IsolatedDatabase first = new IsolatedDatabase();
        first.before();
        try {
            try (ActivityScenario<LeadsActivity> scenario = ActivityScenario.launch(LeadsActivity.class)) {
                scenario.onActivity(activity -> AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-CN")));
                UiTestState.awaitResumed(LeadsActivity.class, "zh", 0);
                onView(withId(R.id.header_title)).check(matches(withText("所有客户")));
            }
        } finally { first.after(); }
        IsolatedDatabase second = new IsolatedDatabase();
        second.before();
        try (ActivityScenario<LeadsActivity> scenario = ActivityScenario.launch(LeadsActivity.class)) {
            onView(withId(R.id.header_title)).check(matches(withText("ALL MY LEADS")));
        } finally { second.after(); }
    }
}
