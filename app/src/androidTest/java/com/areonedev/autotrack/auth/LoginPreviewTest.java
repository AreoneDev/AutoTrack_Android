package com.areonedev.autotrack.auth;

import android.view.WindowManager;
import androidx.test.core.app.ActivityScenario;
import com.areonedev.autotrack.presentation.LoginActivity;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class LoginPreviewTest {
    @Test public void debugLoginCanBeDisplayedInDevicePreview() {
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            scenario.onActivity(activity -> assertEquals(0,
                    activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE));
        }
    }
}
