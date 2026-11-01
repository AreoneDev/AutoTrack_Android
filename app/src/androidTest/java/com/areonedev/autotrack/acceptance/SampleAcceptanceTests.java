package com.areonedev.autotrack.acceptance;

import org.junit.*;
import org.junit.runner.RunWith;

import androidx.test.espresso.Espresso;
import androidx.test.ext.junit.runners.AndroidJUnit4;
//import androidx.test.runner.AndroidJUnit4;
import androidx.test.filters.LargeTest;
//import androidx.test.rule.ActivityTestRule;
import androidx.test.ext.junit.rules.ActivityScenarioRule;

import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.RootMatchers.withDecorView;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;

import static org.hamcrest.Matchers.not;

import com.areonedev.autotrack.application.Main;
import com.areonedev.autotrack.presentation.MainActivity;

@RunWith(AndroidJUnit4.class)
@LargeTest
@Ignore("Splash initializes the customer database; run only after a dedicated test application flavor exists. See LeadWorkflowTest for isolated UI coverage.")
public class SampleAcceptanceTests {
    @Rule
    //public ActivityTestRule<MainActivity> mainActivity = new ActivityTestRule<>(MainActivity.class);
    public ActivityScenarioRule<MainActivity> activityScenarioRule = new ActivityScenarioRule<>(MainActivity.class); //Modern

    @Test
    public void testHomeActivityLaunch() {
        // Basic test to verify the UI launches and the "AutoTrack" title or a specific view is visible
        onView(withText("AutoTrack")).check(matches(isDisplayed()));
    }
}
