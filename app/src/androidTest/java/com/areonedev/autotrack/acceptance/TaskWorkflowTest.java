package com.areonedev.autotrack.acceptance;

import android.content.Intent;
import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.areonedev.autotrack.R;
import com.areonedev.autotrack.objects.Lead;
import com.areonedev.autotrack.objects.Task;
import com.areonedev.autotrack.presentation.LeadDetailsActivity;
import com.areonedev.autotrack.testing.IsolatedDatabase;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TaskWorkflowTest {
    @Rule public IsolatedDatabase database = new IsolatedDatabase();
    private Lead lead;
    private ActivityScenario<LeadDetailsActivity> launch() {
        lead = new Lead("Synthetic", "Tasks", "4165550100", "test@example.invalid", "Sales", "", "", "ON", "Canada", "", 0, null, null, "");
        assertNull(database.dao.insertLead(lead));
        Intent intent = new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), LeadDetailsActivity.class);
        intent.putExtra("SELECTED_LEAD", lead);
        return ActivityScenario.launch(intent);
    }
    private List<Task> tasks() { return database.dao.getTaskRandom(new Task(lead, "", new Date())); }
    private void chooseTaskTypeAndDefaultDateTime(String type) {
        onView(withText(type)).inRoot(isDialog()).perform(click());
        // Confirm the visible default date and time through real dialog buttons.
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
    }
    private void dialogText(String value) {
        onView(isAssignableFrom(EditText.class)).inRoot(isDialog()).perform(replaceText(value), closeSoftKeyboard());
    }
    @Test public void createCompleteReopenEditAndDeleteTask() {
        try (ActivityScenario<LeadDetailsActivity> scenario = launch()) {
            assertTrue(tasks().isEmpty());
            onView(withId(R.id.btnAddTask)).perform(scrollTo(), click());
            chooseTaskTypeAndDefaultDateTime("Appointment");
            dialogText("Synthetic task");
            onView(withText("Confirm & Save")).inRoot(isDialog()).perform(click());
            assertEquals(1, tasks().size());
            Task saved = tasks().get(0);
            assertEquals("Appointment: Synthetic task", saved.getTitle());
            assertFalse(saved.isCompleted());
            onView(withId(R.id.ivTimelineStatus)).perform(scrollTo(), click());
            assertTrue(tasks().get(0).isCompleted());
            scenario.recreate();
            assertTrue(tasks().get(0).isCompleted());
            onView(withId(R.id.ivTimelineStatus)).perform(scrollTo(), click());
            assertFalse(tasks().get(0).isCompleted());
            onView(withText(containsString("Synthetic task"))).perform(scrollTo(), click());
            onView(withText("Edit Task")).inRoot(isDialog()).perform(click());
            chooseTaskTypeAndDefaultDateTime("Follow-up");
            dialogText("Synthetic edited");
            onView(withText("Save Changes")).inRoot(isDialog()).perform(click());
            assertEquals(saved.getEventID(), tasks().get(0).getEventID());
            assertEquals("Follow-up: Synthetic edited", tasks().get(0).getTitle());
            onView(withText(containsString("Synthetic edited"))).perform(scrollTo(), click());
            onView(withText("Delete Task")).inRoot(isDialog()).perform(click());
            onView(withText("Delete")).inRoot(isDialog()).perform(click());
            assertTrue(tasks().isEmpty());
        }
    }
    @Test public void cancelTaskDetailsDoesNotPersist() {
        try (ActivityScenario<LeadDetailsActivity> scenario = launch()) {
            int before = tasks().size();
            onView(withId(R.id.btnAddTask)).perform(scrollTo(), click());
            chooseTaskTypeAndDefaultDateTime("Other");
            dialogText("Cancelled synthetic task");
            onView(withText("Cancel")).inRoot(isDialog()).perform(click());
            assertEquals(before, tasks().size());
        }
    }
}
