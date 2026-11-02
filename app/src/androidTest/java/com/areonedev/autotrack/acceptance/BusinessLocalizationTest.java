package com.areonedev.autotrack.acceptance;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Bitmap;
import java.io.File;
import java.io.FileOutputStream;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.areonedev.autotrack.R;
import com.areonedev.autotrack.presentation.LeadsActivity;
import com.areonedev.autotrack.testing.IsolatedDatabase;
import org.junit.Rule;
import org.junit.Test;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

public class BusinessLocalizationTest {
    @Rule public IsolatedDatabase database = new IsolatedDatabase();

    @Test public void englishAndChineseLeadsRemainNavigableAtCurrentDisplaySize() throws Exception {
        try (ActivityScenario<LeadsActivity> scenario = ActivityScenario.launch(LeadsActivity.class)) {
            scenario.onActivity(activity -> AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")));
            scenario.recreate();
            onView(withId(R.id.header_title)).check(matches(withText("ALL MY LEADS")));
            onView(withId(R.id.nav_account)).check(matches(isDisplayed()));
            scenario.onActivity(activity -> AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-CN")));
            scenario.recreate();
            onView(withId(R.id.header_title)).check(matches(withText("所有客户")));
            onView(withId(R.id.nav_account)).check(matches(isDisplayed()));
            Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            File output = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null), "business-chinese-narrow.png");
            try (FileOutputStream stream = new FileOutputStream(output)) {
                assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream));
            } finally { screenshot.recycle(); }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()));
        }
    }

    @Test public void leadsShowsFrenchLabelsAndDarkBackground() throws Exception {
        try (ActivityScenario<LeadsActivity> scenario = ActivityScenario.launch(LeadsActivity.class)) {
            scenario.onActivity(activity -> {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fr"));
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.recreate();
            Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            File output = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null), "business-french-dark.png");
            try (FileOutputStream stream = new FileOutputStream(output)) {
                assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream));
            } finally { screenshot.recycle(); }
            onView(withId(R.id.header_title)).check(matches(withText("TOUS MES CLIENTS")));
            onView(withId(R.id.nav_account)).check(matches(isDisplayed()));
            onView(withText("Aujourd’hui")).check(matches(isDisplayed()));
            scenario.onActivity(activity -> {
                ColorDrawable background = (ColorDrawable) activity.findViewById(R.id.title_container).getBackground();
                assertTrue("Dark header should use a dark surface", Color.red(background.getColor()) < 128);
                if (activity.getResources().getConfiguration().screenWidthDp < 360 || activity.getResources().getConfiguration().fontScale >= 1.3f) {
                    com.google.android.material.bottomnavigation.BottomNavigationView navigation = activity.findViewById(R.id.bottom_navigation);
                    assertEquals(com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_SELECTED, navigation.getLabelVisibilityMode());
                    android.widget.TextView name = activity.findViewById(R.id.text_lead_name);
                    assertNotNull(name);
                    assertTrue("Customer names should wrap on narrow screens", name.getMaxLines() >= 2);
                }
            });
            onView(withId(R.id.nav_calendar)).perform(click());
            onView(withId(R.id.rvWeekCalendar)).check(matches(isDisplayed()));
            onView(withId(R.id.rvWeekCalendar)).check((view, error) -> {
                if (error != null) throw error;
                androidx.recyclerview.widget.RecyclerView days = (androidx.recyclerview.widget.RecyclerView) view;
                assertTrue(days.getChildCount() > 0);
                for (int i = 0; i < days.getChildCount(); i++) {
                    android.widget.TextView day = days.getChildAt(i).findViewById(R.id.tvDayName);
                    assertNotEquals("Calendar day labels must remain visible in dark mode", Color.BLACK, day.getCurrentTextColor());
                    if (view.getResources().getConfiguration().screenWidthDp < 360 || view.getResources().getConfiguration().fontScale >= 1.3f) {
                        String label = day.getText().toString();
                        assertEquals("Compact calendar day labels must fit on one line", 1, label.codePointCount(0, label.length()));
                    }
                }
            });
            Bitmap calendarScreen = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            File calendarOutput = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null), "calendar-french-dark-narrow.png");
            try (FileOutputStream stream = new FileOutputStream(calendarOutput)) {
                assertTrue(calendarScreen.compress(Bitmap.CompressFormat.PNG, 100, stream));
            } finally { calendarScreen.recycle(); }
            onView(withId(R.id.nav_account)).perform(click());
            onView(withId(R.id.account_identity)).check(matches(isDisplayed()));
            Bitmap accountScreen = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            File accountOutput = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null), "account-french-dark-narrow.png");
            try (FileOutputStream stream = new FileOutputStream(accountOutput)) {
                assertTrue(accountScreen.compress(Bitmap.CompressFormat.PNG, 100, stream));
            } finally { accountScreen.recycle(); }
            onView(withId(R.id.sync_review)).perform(scrollTo(), click());
            onView(withText("Synchronisation indisponible. Vos modifications en attente sont conservées.")).check(matches(isDisplayed()));
            onView(withText("Synchroniser et actualiser")).check(matches(isDisplayed()));
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            });
        }
    }
}
