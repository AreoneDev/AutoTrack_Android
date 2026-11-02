package com.areonedev.autotrack.testing;

import android.app.Activity;
import android.app.LocaleManager;
import android.os.Build;
import android.os.LocaleList;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import java.util.ArrayList;

/** Restores process-wide UI state without depending on a live AppCompat delegate. */
public final class UiTestState {
    private UiTestState() { }
    public static void awaitResumed(Class<? extends Activity> expected, String language, int nightMode) {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        long deadline = android.os.SystemClock.uptimeMillis() + 15000;
        do {
            boolean[] ready = {false};
            instrumentation.runOnMainSync(() -> {
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                    android.content.res.Configuration config = activity.getResources().getConfiguration();
                    ready[0] |= expected.isInstance(activity) && activity.hasWindowFocus()
                            && !activity.isChangingConfigurations()
                            && config.getLocales().get(0).getLanguage().equals(language)
                            && (nightMode == 0 || (config.uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == nightMode);
                }
            });
            if (ready[0]) { instrumentation.waitForIdleSync(); return; }
            android.os.SystemClock.sleep(20);
        } while (android.os.SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Expected focused, resumed " + expected.getSimpleName() + " in " + language);
    }
    public static void reset() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.runOnMainSync(() -> {
            for (Stage stage : new Stage[]{Stage.RESUMED, Stage.PAUSED, Stage.STOPPED, Stage.STARTED, Stage.CREATED}) {
                for (Activity activity : new ArrayList<>(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage))) {
                    activity.finish();
                }
            }
        });
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> {
            if (Build.VERSION.SDK_INT >= 33) {
                instrumentation.getTargetContext().getSystemService(LocaleManager.class)
                        .setApplicationLocales(LocaleList.forLanguageTags("en"));
            } else {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"));
            }
            instrumentation.getTargetContext().getSharedPreferences("appearance", 0).edit().clear().commit();
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        });
        long deadline = android.os.SystemClock.uptimeMillis() + 5000;
        do {
            instrumentation.waitForIdleSync();
            if (instrumentation.getTargetContext().getResources().getConfiguration().getLocales().get(0).getLanguage().equals("en")) return;
            android.os.SystemClock.sleep(20);
        } while (android.os.SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("English configuration was not applied before the next test");
    }
}
