package com.areonedev.autotrack.acceptance;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.util.TypedValue;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.ColorUtils;
import androidx.core.os.LocaleListCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.areonedev.autotrack.R;
import com.areonedev.autotrack.objects.Lead;
import com.areonedev.autotrack.presentation.*;
import com.areonedev.autotrack.testing.IsolatedDatabase;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;

/** Captures every private page in each supported locale and appearance. */
public class PageAppearanceAuditTest {
    @Rule public IsolatedDatabase database = new IsolatedDatabase();
    @Test public void englishLight() throws Exception { audit("en", false); }
    @Test public void englishDark() throws Exception { audit("en", true); }
    @Test public void frenchLight() throws Exception { audit("fr", false); }
    @Test public void frenchDark() throws Exception { audit("fr", true); }
    @Test public void chineseLight() throws Exception { audit("zh-CN", false); }
    @Test public void chineseDark() throws Exception { audit("zh-CN", true); }

    private void audit(String language, boolean dark) throws Exception {
        try (ActivityScenario<LeadsActivity> scenario = ActivityScenario.launch(LeadsActivity.class)) {
            scenario.onActivity(activity -> AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language)));
            onView(withId(R.id.header_title)).check(matches(withText(R.string.leads_title)));
            com.areonedev.autotrack.testing.UiTestState.awaitResumed(LeadsActivity.class, language.startsWith("zh") ? "zh" : language, 0);
            scenario.onActivity(activity -> AppCompatDelegate.setDefaultNightMode(dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO));
            onView(withId(R.id.header_title)).check(matches(isDisplayed()));
            com.areonedev.autotrack.testing.UiTestState.awaitResumed(LeadsActivity.class, language.startsWith("zh") ? "zh" : language,
                    dark ? android.content.res.Configuration.UI_MODE_NIGHT_YES : android.content.res.Configuration.UI_MODE_NIGHT_NO);
        }
        Class<?>[] pages = {LeadsActivity.class, LeadsCreationActivity.class, CalendarActivity.class,
                NotificationsActivity.class, AccountSettingsActivity.class, SyncActivity.class, LeadDetailsActivity.class};
        Lead lead = new Lead("Synthetic", "Unicode 测试 Élodie", "4165550100", "audit@example.invalid", "Sales", "", "", "ON", "Canada", "", 0, null, null, "");
        assertNull(database.dao.insertLead(lead));
        for (Class<?> page : pages) {
            Intent intent = new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), page);
            if (page == LeadDetailsActivity.class) intent.putExtra("SELECTED_LEAD", lead);
            try (ActivityScenario<Activity> scenario = ActivityScenario.launch(intent)) {
                scenario.onActivity(activity -> {
                    assertEquals(language.startsWith("zh") ? "zh" : language,
                            activity.getResources().getConfiguration().getLocales().get(0).getLanguage());
                    int mode = activity.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
                    assertEquals(dark ? android.content.res.Configuration.UI_MODE_NIGHT_YES : android.content.res.Configuration.UI_MODE_NIGHT_NO, mode);
                    TypedValue surface = new TypedValue(), text = new TypedValue();
                    activity.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurface, surface, true);
                    activity.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, text, true);
                    assertTrue("Body text theme contrast must be at least 4.5:1", ColorUtils.calculateContrast(text.data, surface.data) >= 4.5);
                    if (page == LeadsCreationActivity.class || page == LeadDetailsActivity.class) {
                        androidx.appcompat.widget.Toolbar toolbar = activity.findViewById(R.id.toolbar);
                        int background = ((android.graphics.drawable.ColorDrawable) toolbar.getBackground()).getColor();
                        boolean foundTitle = false;
                        for (int i = 0; i < toolbar.getChildCount(); i++) {
                            if (toolbar.getChildAt(i) instanceof android.widget.TextView) {
                                android.widget.TextView title = (android.widget.TextView) toolbar.getChildAt(i);
                                if (title.getText().toString().contentEquals(toolbar.getTitle())) {
                                    foundTitle = true;
                                    assertTrue("Toolbar title contrast must be at least 3:1", ColorUtils.calculateContrast(title.getCurrentTextColor(), background) >= 3);
                                }
                            }
                        }
                        assertTrue("Toolbar title must be rendered", foundTitle);
                    }
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                Bitmap screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
                assertNotNull(screenshot);
                File file = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),
                        "audit-" + language + "-" + (dark ? "dark" : "light") + "-" + page.getSimpleName() + ".png");
                try (FileOutputStream stream = new FileOutputStream(file)) {
                    assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream));
                } finally { screenshot.recycle(); }
            }
        }
    }
}
