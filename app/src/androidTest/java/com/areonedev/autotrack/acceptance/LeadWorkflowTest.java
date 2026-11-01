package com.areonedev.autotrack.acceptance;

import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.areonedev.autotrack.R;
import com.areonedev.autotrack.objects.Lead;
import com.areonedev.autotrack.objects.Vehicle;
import com.areonedev.autotrack.presentation.LeadsActivity;
import com.areonedev.autotrack.presentation.LeadsCreationActivity;
import com.areonedev.autotrack.testing.IsolatedDatabase;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

/** Only launches screens after injecting the isolated DAO; never launches the production splash. */
@RunWith(AndroidJUnit4.class)
public class LeadWorkflowTest {
    @Rule public IsolatedDatabase database = new IsolatedDatabase();
    private List<Lead> leads() {
        List<Lead> result = new ArrayList<>();
        assertNull(database.dao.getLeadSequential(result));
        return result;
    }
    private void enterRequiredFields() {
        onView(withId(R.id.etFirstName)).perform(replaceText("Synthetic"));
        onView(withId(R.id.etLastName)).perform(replaceText("Workflow"));
        onView(withId(R.id.etPhone)).perform(replaceText("4165550100"), closeSoftKeyboard());
    }
    private void rowCount(int count) {
        onView(withId(R.id.recyclerViewLeads)).check((view, error) -> {
            if (error != null) throw error;
            RecyclerView.Adapter<?> adapter = ((RecyclerView) view).getAdapter();
            int customerRows = 0;
            for (int i = 0; i < adapter.getItemCount(); i++)
                if (adapter.getItemViewType(i) == 1) customerRows++;
            assertEquals(count, customerRows);
        });
    }
    @Test public void requiredInputDecisionTableDoesNotWriteInvalidRows() {
        int before = leads().size();
        try (ActivityScenario<LeadsCreationActivity> scenario = ActivityScenario.launch(LeadsCreationActivity.class)) {
            // No names/phone; then first name only; then both names without phone.
            onView(withId(R.id.btnSaveLead)).perform(click());
            assertEquals(before, leads().size());
            onView(withId(R.id.etFirstName)).perform(scrollTo(), replaceText("Synthetic"), closeSoftKeyboard());
            onView(withId(R.id.btnSaveLead)).perform(click());
            assertEquals(before, leads().size());
            onView(withId(R.id.etLastName)).perform(scrollTo(), replaceText("Workflow"), closeSoftKeyboard());
            onView(withId(R.id.btnSaveLead)).perform(click());
            assertEquals(before, leads().size());
            onView(withId(R.id.btnSaveLead)).check(matches(isDisplayed()));
        }
    }
    @Test public void draftSurvivesRecreationAndValidSaveWritesOneCustomer() {
        int before = leads().size();
        try (ActivityScenario<LeadsCreationActivity> scenario = ActivityScenario.launch(LeadsCreationActivity.class)) {
            enterRequiredFields();
            scenario.recreate();
            onView(withId(R.id.etFirstName)).check(matches(withText("Synthetic")));
            onView(withId(R.id.etLastName)).check(matches(withText("Workflow")));
            onView(withId(R.id.etPhone)).check(matches(withText("4165550100")));
            onView(withId(R.id.btnSaveLead)).perform(click());
            assertEquals(before + 1, leads().size());
            Lead saved = leads().stream().filter(l -> l.getLeadName().equals("Synthetic Workflow")).findFirst().get();
            assertEquals("416-555-0100", saved.getLeadPhoneNumber());
        }
    }
    @Test public void addFromListThenSearchAndResetFilters() {
        try (ActivityScenario<LeadsActivity> scenario = ActivityScenario.launch(LeadsActivity.class)) {
            onView(withId(R.id.fab_add_lead)).perform(click());
            enterRequiredFields();
            onView(withId(R.id.btnSaveLead)).perform(click());
            scenario.onActivity(a -> ((SearchView) a.findViewById(R.id.searchView)).setQuery("Synthetic Workflow", false));
            rowCount(1);
            onView(withId(R.id.btnFilterToggle)).perform(click());
            onView(withId(R.id.btnResetFilters)).perform(click());
            rowCount(leads().size());
            scenario.onActivity(a -> ((SearchView) a.findViewById(R.id.searchView)).setQuery("no-such-synthetic-customer", false));
            rowCount(0);
            onView(withId(R.id.empty_state_view)).check(matches(isDisplayed()));
        }
    }
    @Test public void editReturnsToViewMode() {
        Lead lead = new Lead("Synthetic", "Workflow", "4165550100", "test@example.invalid", "Sales", "", "", "ON", "Canada", "", 0, new Vehicle("Test", "Synthetic", "2026", ""), null, "");
        assertNull(database.dao.insertLead(lead));
        try (ActivityScenario<LeadsActivity> scenario = ActivityScenario.launch(LeadsActivity.class)) {
            scenario.onActivity(a -> ((SearchView) a.findViewById(R.id.searchView)).setQuery("Synthetic Workflow", false));
            onView(withText("Synthetic Workflow")).perform(click());
            onView(withId(R.id.action_edit)).perform(click());
            onView(withId(R.id.etDetFirstName)).perform(replaceText("Edited"), closeSoftKeyboard());
            onView(withId(R.id.btnUpdateLead)).perform(click());
            assertEquals("Edited Workflow", database.dao.getLeadRandom(lead).get(0).getLeadName());
            // Business expectation: saving must return to view mode. Known toggle bug must not be hidden.
            onView(withId(R.id.layoutViewMode)).check(matches(isDisplayed()));
        }
    }
    @Test public void cancelThenConfirmDelete() {
        Lead lead = new Lead("Synthetic", "Delete", "4165550100", "test@example.invalid", "Sales", "", "", "ON", "Canada", "", 0, new Vehicle("Test", "Synthetic", "2026", ""), null, "");
        assertNull(database.dao.insertLead(lead));
        try (ActivityScenario<LeadsActivity> scenario = ActivityScenario.launch(LeadsActivity.class)) {
            scenario.onActivity(a -> ((SearchView) a.findViewById(R.id.searchView)).setQuery("Synthetic Delete", false));
            onView(withText("Synthetic Delete")).perform(click());
            onView(withId(R.id.action_edit)).perform(click());
            onView(withId(R.id.btnDeleteLead)).perform(click());
            onView(withText("Cancel")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
            assertEquals(1, database.dao.getLeadRandom(lead).size());
            onView(withId(R.id.btnDeleteLead)).perform(click());
            onView(withText("Delete")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
            assertTrue(database.dao.getLeadRandom(lead).isEmpty());
        }
    }
    @Test public void calendarWeekNavigationAndReturnToLeads() {
        try (ActivityScenario<LeadsActivity> scenario = ActivityScenario.launch(LeadsActivity.class)) {
            onView(withId(R.id.nav_calendar)).perform(click());
            onView(withId(R.id.rvWeekCalendar)).check(matches(isDisplayed()));
            onView(withId(R.id.btnNextWeek)).perform(click());
            onView(withId(R.id.btnPrevWeek)).perform(click());
            onView(withId(R.id.tvAgendaHeader)).check(matches(isDisplayed()));
            onView(withId(R.id.nav_leads)).perform(click());
            onView(withId(R.id.recyclerViewLeads)).check(matches(isDisplayed()));
        }
    }
    @Test public void selectedStatusAndStageFiltersCombineAndReset() {
        Lead active = new Lead("Filter", "Active", "4165550100", null, null, null, null, null, null, null, 0, null, null, null);
        Lead lost = new Lead("Filter", "Lost", "4165550101", null, null, null, null, null, null, null, 0, null, null, null);
        lost.setLeadStatus(false);
        assertNull(database.dao.insertLead(active));
        assertNull(database.dao.insertLead(lost));
        try (ActivityScenario<LeadsActivity> scenario = ActivityScenario.launch(LeadsActivity.class)) {
            scenario.onActivity(a -> ((SearchView) a.findViewById(R.id.searchView)).setQuery("Filter", false));
            rowCount(2);
            onView(withId(R.id.btnFilterToggle)).perform(click());
            onView(withId(R.id.spinnerStatusFilter)).perform(click());
            onData(allOf(is(instanceOf(String.class)), is("Lost"))).perform(click());
            onView(withId(R.id.spinnerStageFilter)).perform(click());
            onData(allOf(is(instanceOf(String.class)), is("New"))).perform(click());
            onView(withId(R.id.btnApplyFilters)).perform(click());
            rowCount(1);
            onView(withText("Filter Lost")).check(matches(isDisplayed()));
            onView(withId(R.id.btnResetFilters)).perform(click());
            rowCount(leads().size());
        }
    }
}
