package com.areonedev.autotrack.sync;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.areonedev.autotrack.application.Services;
import com.areonedev.autotrack.auth.*;
import com.areonedev.autotrack.objects.Lead;
import com.areonedev.autotrack.persistence.DataAccess;
import com.areonedev.autotrack.presentation.LeadsActivity;
import org.json.JSONObject;
import org.junit.Test;
import java.util.UUID;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static org.junit.Assert.*;

public class SyncConflictWorkflowTest {
    @Test public void userCanReviewIdentifiedConflictAndAcceptServer() throws Exception {
        String owner=UUID.randomUUID().toString();
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        try {
            Services.initialize(context);Services.openAccount(owner);SyntheticAccount.install(owner);DataAccess data=Services.getDataAccess("AutoTrack");
            Lead lead=new Lead();lead.setLeadFirstName("Synthetic");lead.setLeadLastName("Conflict");lead.setLeadNotes("My note");assertNull(data.insertLead(lead));
            SyncEngine.Operation op=data.syncPending(owner).get(0);String sync=data.getLeadIdentity(lead.getLeadID()).syncId;
            JSONObject remote=new JSONObject().put("lead_id",42).put("version",2).put("sync_id",sync).put("first_name","Synthetic").put("last_name","Conflict").put("notes","Server note").put("created_at","2026-11-01T12:00:00Z");
            data.syncAcknowledge(owner,op,new JSONObject().put("status","conflict").put("remote",remote).toString());
            try(ActivityScenario<LeadsActivity> scenario=ActivityScenario.launch(LeadsActivity.class)) {
                onView(withText("Synchronization")).perform(click());
                onView(withText("Review conflict: Synthetic Conflict")).check(matches(isDisplayed())).perform(click());
                onView(withText("Accept server")).perform(click());
                assertTrue(data.syncConflicts(owner).isEmpty());assertEquals("Server note",data.getLeadRandom(lead).get(0).getLeadNotes());
                androidx.test.espresso.Espresso.pressBack();
            }
        } finally {Services.closeDataAccess();SyntheticAccount.reset();context.deleteDatabase(AccountSession.databaseName(owner));}
    }
}
