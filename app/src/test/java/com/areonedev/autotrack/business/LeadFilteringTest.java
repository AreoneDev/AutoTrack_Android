package com.areonedev.autotrack.business;

import com.areonedev.autotrack.application.Services;
import com.areonedev.autotrack.objects.Lead;
import com.areonedev.autotrack.persistence.DataAccessStub;
import org.junit.*;
import java.util.*;
import static org.junit.Assert.*;

public class LeadFilteringTest {
    private AccessLeads access;
    @Before public void setUp() {
        Services.closeDataAccess();
        Services.createDataAccess(new DataAccessStub());
        access = new AccessLeads();
    }
    @After public void tearDown() { Services.closeDataAccess(); }
    private List<Lead> filter(String query, String status, String stage, String division, String year, String make, String model) {
        return access.getLeadsFiltered(query, status, stage, division, year, make, model);
    }
    @Test public void blankSearchAndPlaceholdersIncludeAllInDescendingIdOrder() {
        List<Lead> all = filter(" ", "All Status", "All Stages", "All Divisions", "Year", "Make", "Model");
        assertEquals(3, all.size());
        assertTrue(all.get(0).getLeadID() > all.get(1).getLeadID());
        assertTrue(all.get(1).getLeadID() > all.get(2).getLeadID());
    }
    @Test public void namePhoneAndNoMatchPartitions() {
        assertEquals("Alice Chen", filter("  aLiCe  ", null, null, null, null, null, null).get(0).getLeadName());
        assertEquals(1, filter("8123", null, null, null, null, null, null).size());
        assertTrue(filter("absent-synthetic", null, null, null, null, null, null).isEmpty());
    }
    @Test public void filtersUseConjunctionAndStatusTransitions() {
        assertEquals(1, filter("Alice", "Active", "NEW", "Sales", "2024", "Volkswagen", "Jetta").size());
        assertTrue(filter("Alice", "Active", "VISITED", "Sales", "2024", "Volkswagen", "Jetta").isEmpty());
        Lead alice = filter("Alice", null, null, null, null, null, null).get(0);
        alice.setLeadStatus(false);
        assertNull(access.updateLead(alice));
        assertTrue(filter("Alice", "Active", null, null, null, null, null).isEmpty());
        assertEquals(1, filter("Alice", "Lost", null, null, null, null, null).size());
    }
    @Test public void absentVehicleExcludedOnlyWhenCarFilterSelected() {
        Lead noCar = new Lead("Synthetic", "NoCar", "", "", "", "", "", "", "", "", 0, null, null, "");
        assertNull(access.insertLead(noCar));
        assertEquals(1, filter("NoCar", null, null, null, null, null, null).size());
        assertTrue(filter("NoCar", null, null, null, "2024", null, null).isEmpty());
    }
}
