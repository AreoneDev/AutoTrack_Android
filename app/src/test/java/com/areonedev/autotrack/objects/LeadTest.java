package com.areonedev.autotrack.objects;

import junit.framework.TestCase;
import org.junit.Assert;
import java.util.Date;

public class LeadTest extends TestCase {

    private static final String TAG = "LeadTest";

    public LeadTest(String arg0){
        super(arg0);
    }

    // Helper to create a dummy vehicle for testing
    private Vehicle getTestVehicle() {
        return new Vehicle("Toyota", "RAV4", "2024","Comfortline");
    }

    // 1. Test basic data storage and retrieval
    public void testLeadConstructor_Getters(){
        System.out.println(TAG + " Running testLeadConstructorAndGetters.");

        Vehicle interest = new Vehicle("VW", "Atlas", "2024","Comfortline");
        Date now = new Date();

        // Updated to 17-parameter constructor
        Lead lead = com.areonedev.autotrack.testing.LeadFixtures.create("Alice", "Chen", "204-555-8123", "alice@test.com", "Sales",
                "123 St", "Winnipeg", "MB", "Canada", "R3C 1A1",
                50000.0, interest, null, "NEW", now, "Wants a blue one", now);

        assertNotNull("Lead object should be created", lead);
        assertEquals("Alice", lead.getLeadFirstName());
        assertEquals("Chen", lead.getLeadLastName());
        assertEquals("Alice Chen", lead.getLeadName());
        assertEquals(50000.0, lead.getLeadBudget());

        // Check Vehicle object inside Lead
        assertNotNull(lead.getLeadVehicleInterest());
        assertEquals("Atlas", lead.getLeadVehicleInterest().getModel());
        assertEquals("Wants a blue one", lead.getLeadNotes());

        System.out.println(TAG+ " test testLeadConstructor_Getters finished.");
    }

    // 2. Test that IDs are unique (Fixing the logic bug in original test)
    public void testLeadIDGeneration() {
        System.out.println(TAG + " Running testLeadIDGeneration.");
        Date now = new Date();

        Lead lead1 = com.areonedev.autotrack.testing.LeadFixtures.create("Alice", "Chen", "111", null, null, null, null, null, null, null, 0, null, null, "NEW", now, "", now);
        Lead lead2 = com.areonedev.autotrack.testing.LeadFixtures.create("Bob", "Smith", "222", null, null, null, null, null, null, null, 0, null, null, "NEW", now, "", now);

        // IDs should NOT be the same
        assertFalse("Each lead should have a unique ID", lead1.getLeadID() == lead2.getLeadID());

        System.out.println(TAG+ " test testLeadGeneration finished.");
    }

    // 3. Test the logic that splits a full name into First and Last
    public void testLeadNameSplitting() {
        System.out.println(TAG + " Running testLeadNameSplitting.");

        // Using the Empty Constructor
        Lead lead = new Lead();

        // Standard case
        lead.setLeadName("John Wick");
        assertEquals("John", lead.getLeadFirstName());
        assertEquals("Wick", lead.getLeadLastName());

        // Edge case: Single name
        lead.setLeadName("Cher");
        assertEquals("Cher", lead.getLeadFirstName());
        assertTrue("Last name should be empty for single names",
                lead.getLeadLastName() == null || lead.getLeadLastName().isEmpty());

        System.out.println(TAG+ " test testLeadNameSplitting finished.");
    }

    // 4. Test Equality logic
    public void testLeadEquality() {
        System.out.println(TAG + " Running testLeadEquality.");
        Date now = new Date();
        Vehicle v = getTestVehicle();

        Lead lead1 = com.areonedev.autotrack.testing.LeadFixtures.create("Alice", "Chen", "111", null, null, null, null, null, null, null, 0, v, null, "NEW", now, "", now);

        // A lead should be equal to itself
        assertTrue("Lead should be equal to itself", lead1.equals(lead1));

        // Test against null and other types
        assertFalse("Lead should not equal null", lead1.equals(null));
        assertFalse("Lead should not equal a different object type", lead1.equals("Some String"));

        System.out.println(TAG+ " test testLeadEquality finished.");
    }

    // 5. Test Null Handling
    public void testLeadNullSafety() {
        System.out.println(TAG + " Running testLeadNullSafety.");
        Date now = new Date();

        // Case A: Verify that providing NO names throws an exception (if your constructor logic does this)
        try {
            com.areonedev.autotrack.testing.LeadFixtures.create(null, null, null, null, null, null, null, null, null, null, 0, null, null, null, now, null, now);
            fail("Constructor should have thrown IllegalArgumentException for missing both names");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }

        // Case B: Verify that providing ONLY First Name is valid
        Lead leadOnlyFirst = com.areonedev.autotrack.testing.LeadFixtures.create("Alice", null, null, null, null, null, null, null, null, null, 0, null, null, "NEW", now, null, now);
        assertEquals("Alice", leadOnlyFirst.getLeadFirstName());
        assertEquals("", leadOnlyFirst.getLeadLastName());

        System.out.println(TAG+ " test testLeadNullSafety finished.");
    }
}
