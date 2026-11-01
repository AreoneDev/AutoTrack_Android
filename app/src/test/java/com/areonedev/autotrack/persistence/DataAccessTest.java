package com.areonedev.autotrack.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.util.Log;

import org.junit.Before;
import org.junit.runner.RunWith;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;

import com.areonedev.autotrack.application.Main;
import com.areonedev.autotrack.application.Services;
import com.areonedev.autotrack.objects.Lead;
import com.areonedev.autotrack.objects.Vehicle;

//import junit.framework.TestCase;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DataAccessTest{
    private DataAccess dataAccess;
    private static final String dbName = Main.dbName;
    private static final String TAG = "DataAccessTest\t";

    // 3. Change constructor to empty
    public DataAccessTest() {}

    @Before
    public void setUp() {

        // Use the following statements to run with the stub database:
        stub();
        // or switch to the real database:
        //real();
        // Note the increase in test execution time.
    }

    private void real() {
        Log.d(TAG, "\nStarting Persistence test DataAccess (using REAL)");

        // 1. Get the correct Android internal path
        // This is required because Android apps cannot write to random folders.
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String dbPath = context.getDatabasePath(dbName).getAbsolutePath();

        Log.d(TAG, "Opening database at: " + dbPath);

        // 2. Initialize the DataAccessObject with the absolute path
        dataAccess = new DataAccessObject(dbName);
        dataAccess.open(dbPath);

        Services.closeDataAccess();
        Services.createDataAccess(dataAccess);
        dataAccess = Services.getDataAccess(dbName);
    }

    private void stub() {
        Services.closeDataAccess();
        Log.d(TAG, "\nStarting Persistence test DataAccess (using STUB)");
        dataAccess = new DataAccessStub(dbName);

        // Injection: Tell Services to use this stub
        Services.createDataAccess(dataAccess);

        // Retrieve it back to ensure we are using the singleton instance
        dataAccess = Services.getDataAccess(dbName);
    }

    @org.junit.After
    public void tearDown() {
        Services.closeDataAccess();
        Log.d(TAG, "Finished Persistence test DataAccess (using STUB)");
    }

    public static void dataAccessTest(DataAccess dataAccess) {
        List<Lead> leads = new ArrayList<>();

        // 1. Test Retrieval
        assertEquals(0, leads.size()); // This now uses static import from org.junit.Assert
        String result = dataAccess.getLeadSequential(leads);
        assertNull("Database should return null on success", result);
        assertEquals(3, leads.size());
        int initialSize = leads.size();

        // 2. Test Insertion
        // Note: Ensure your Lead constructor matches these parameters
        Vehicle testVehicle = new Vehicle("Volkswagen", "Atlas", "2024","Peak Edition");
        Date now = new Date();

        Lead testLead = com.areonedev.autotrack.testing.LeadFixtures.create("Test", "Lead", "555-0199", "test@example.com", "Sales",
                "123 Test St", "Winnipeg", "MB", "Canada", "R3C 1A1",
                50000.0, testVehicle, null, "NEW", now, "Test Note", now);dataAccess.insertLead(testLead);
        dataAccess.getLeadSequential(leads);
        assertEquals(4, leads.size());

        // 3. Verify Insertion
        leads.clear();
        assertEquals(0, leads.size());
        dataAccess.getLeadSequential(leads);
        assertEquals("List size should increase by 1", initialSize + 1, leads.size());

//        DataAccessTest dataAccessTest = new DataAccessTest("");
//        dataAccessTest.dataAccess=dataAccess;
//        dataAccessTest.testGetLeadSequential();
    }

    @Test
    public void testGetLeadSequential() {
        System.out.println(TAG + "Running test getLeadSequential: Verify Lead Retrieval");

        List<Lead> leads = new ArrayList<>();
        dataAccess.getLeadSequential(leads);
        assertNotNull("Leads list should not be null", leads);
        assertEquals(3, leads.size());

        assertEquals("Alice Chen", leads.getFirst().getLeadName());
    }

    @Test
    public void testRandom() {
        System.out.println(TAG + "Running test getLeadRandom: Verify Lead Search functionality");
        Date now = new Date();
        List<Lead> leads = new ArrayList<>();
        dataAccess.getLeadSequential(leads);
        // Verify that we can at least access the list
        assertEquals(leads.size(), 3);

        // 1. NEGATIVE TEST: Search for a lead that does NOT exist
        Lead testLead = com.areonedev.autotrack.testing.LeadFixtures.create("Non", "Existence", "000", null, null, null, null, null, null, null, 0, null, null, "NEW", now, "", now);
        ArrayList<Lead> results = dataAccess.getLeadRandom(testLead);
        assertEquals("Should find 0 leads for non-existent criteria", 0, results.size());

        testLead = new Lead();
        results = dataAccess.getLeadRandom(testLead);
        assertEquals(0, results.size());

        // 2. Negative Test: Same name, but phone# is different
        testLead = com.areonedev.autotrack.testing.LeadFixtures.create("Alice", "Chen", "555-1100", null, null, null, null, null, null, null, 0, null, null, "NEW", now, "", now);
        results = dataAccess.getLeadRandom(testLead);
        assertEquals("The leads list should be empty", 0, results.size());

        // 3. POSITIVE TEST: Search for a lead that does exist
        testLead = com.areonedev.autotrack.testing.LeadFixtures.create("Alice", "Chen", "204-555-8123", null, null, null, null, null, null, null, 0, null, null, "NEW", now, "", now);
        results = dataAccess.getLeadRandom(testLead);
        assertEquals("The leads list should not be empty", 0, results.size());
        Lead l2 = leads.get(0);
        results = dataAccess.getLeadRandom(l2);
        assertEquals("The leads list should not be empty", 1, results.size());
        //compareLeads(l2, testLead);
    }

    private void compareLeads(Lead expected, Lead actual) {
        System.out.println("--- Comparing Leads ---");
        System.out.println("Field      | Expected (Test) | Actual (DB)");
        System.out.println("-----------|-----------------|-----------");
        System.out.printf("ID         | %-15d | %-15d %s\n", expected.getLeadID(), actual.getLeadID(), (expected.getLeadID() == actual.getLeadID() ? "" : "[DIFF]"));
        System.out.printf("Name       | %-15s | %-15s %s\n", expected.getLeadName(), actual.getLeadName(), (expected.getLeadName().equals(actual.getLeadName()) ? "" : "[DIFF]"));
        System.out.printf("Phone      | %-15s | %-15s %s\n", expected.getLeadPhoneNumber(), actual.getLeadPhoneNumber(), (expected.getLeadPhoneNumber().equals(actual.getLeadPhoneNumber()) ? "" : "[DIFF]"));
        System.out.println("-----------------------");
    }

    @Test
    public void testInsert() {
        System.out.println(TAG + "Running test insertLead: Verify Lead Insertion");
        ArrayList<Lead> leads = new ArrayList<>();
        dataAccess.getLeadSequential(leads);

        /* Insert Unique */
        Vehicle v = new Vehicle("Volkswagen", "Jetta", "2024","Trendline");
        Date now = new Date();
        Lead testLead = com.areonedev.autotrack.testing.LeadFixtures.create("New", "User", "555-9999", "new@test.com", "Sales",
                "Street", "City", "Prov", "Country", "Postal",
                30000.0, v, null, "NEW", now, "Note", now);
        ArrayList<Lead> results = dataAccess.getLeadRandom(testLead);
        assertEquals(0, results.size());
        assertEquals(3, leads.size());
        dataAccess.insertLead(testLead); //after insertion
        results = dataAccess.getLeadRandom(testLead);
        assertEquals(1, results.size());
        dataAccess.getLeadSequential(leads);
        assertEquals(4, leads.size());
        assertEquals("Alice Chen", leads.getFirst().getLeadName());
        assertEquals("New User", leads.getLast().getLeadName());

        // Identity-based duplicate: reinserting the persisted object must fail.
        assertEquals("Duplicate lead.", dataAccess.insertLead(testLead));
        dataAccess.getLeadSequential(leads);
        assertEquals(4, leads.size());
        assertEquals(1, dataAccess.getLeadRandom(testLead).size());

    }

    @Test
    public void testUpdateLead() {
        System.out.println(TAG + "Running test updateLead: Verify Lead Update");

        /* HappyUpdate, valid lead update */
        // 1. Arrange: Get an existing lead from the stub (e.g., Alice Chen)
        List<Lead> leads = new ArrayList<>();
        dataAccess.getLeadSequential(leads);
        Lead oldLead = leads.get(0);
        long oldID = oldLead.getLeadID();
        String oldName = oldLead.getLeadName();
        // 2. Act: Modify the lead's information
        // We keep the same ID but change the name and phone
        oldLead.setLeadName("Alice Updated");
        oldLead.setLeadPhoneNumber("999-9999");
        String result = dataAccess.updateLead(oldLead);//update process...
        assertNull("Lead should be modified successfully",result);
        dataAccess.getLeadSequential(leads);
        Lead updatedLead = leads.get(0);
        ArrayList<Lead> results = dataAccess.getLeadRandom(updatedLead);
        // 4. Assertions
        assertEquals("Should still find exactly 1 lead", 1, results.size());
        assertEquals("ID must remain the same", oldID, updatedLead.getLeadID());
        assertEquals("Name should now be 'Alice Updated'", "Alice Updated", updatedLead.getLeadName());
        assertEquals("Phone should now be '999-9999'", "999-9999", updatedLead.getLeadPhoneNumber());

        /* Update a Non-existent lead */
        // Since leadID is immutable and auto-incrementing, a new Lead will have a unique ID not in the DB
        Date now = new Date();
        Lead ghost = com.areonedev.autotrack.testing.LeadFixtures.create("Ghost", "User", "000", null, null, null, null, null, null, null, 0, null, null, "NEW", now, "", now);
        String ghostResult = dataAccess.updateLead(ghost);
        assertEquals("Updating a non-existent lead should return an error message", "Lead not found.",ghostResult);
        /* Partial Update: Change only one field */
        // Reset Alice to a valid state but change only the phone
        oldLead.setLeadName("Alice Chen");
        oldLead.setLeadPhoneNumber("555-9999");
        double originalBudget = oldLead.getLeadBudget();
        dataAccess.updateLead(oldLead);
        dataAccess.getLeadSequential(leads);
        updatedLead = leads.get(0);
        results = dataAccess.getLeadRandom(updatedLead);
        assertEquals("Should still find exactly 1 lead", 1, results.size());
        // Re-fetch to verify
        leads.clear();
        dataAccess.getLeadSequential(leads);
        Lead partialCheck = leads.get(0);
        assertEquals("555-9999", partialCheck.getLeadPhoneNumber());
        assertEquals("Budget should remain unchanged", originalBudget, partialCheck.getLeadBudget(), 0.0);

        System.out.println(TAG + "Update verified for ID: " +updatedLead.getLeadID() + " New Name: " + updatedLead.getLeadName()+", Old Name: "+oldName);
    }

    @Test
    public void testDeleteLead() {
        System.out.println(TAG + "Running test updateLead: Verify Lead Deletion");

        // 1. Happy Path: Delete an existing lead
        ArrayList<Lead> leads = new ArrayList<>();
        dataAccess.getLeadSequential(leads);
        int initialSize = leads.size();
        // Pick a target (e.g., the first lead, Alice)
        Lead testLead = leads.get(0);
        long testLeadLeadID = testLead.getLeadID();
        // Act: Delete the lead
        String result = dataAccess.deleteLead(testLead);
        assertNull("Delete should return null on success", result);
        // 2. Verification: Check that the size decreased
        leads.clear();
        dataAccess.getLeadSequential(leads);
        assertEquals("Database size should decrease by 1", initialSize - 1, leads.size());
        // 3. Verification: Ensure the lead is no longer searchable
        ArrayList<Lead> searchResults = dataAccess.getLeadRandom(testLead);
        assertEquals("Deleted lead should not be found in search results", 0, searchResults.size());
        // 4. Edge Case: Delete a lead that does not exist (Ghost Lead)
        // This lead has a new ID that was never inserted into the Stub
        Date now = new Date();
        Lead ghost = com.areonedev.autotrack.testing.LeadFixtures.create("Ghost", "User", "000", null, null, null, null, null, null, null, 0, null, null, "NEW", now, "", now);
        String ghostResult = dataAccess.deleteLead(ghost);
        assertEquals("Deleting a non-existent lead should return an error message", "Lead not found.", ghostResult);

        /* Double Delete, delete the same lead twice */
        // Try deleting the same target again
        String doubleDeleteResult = dataAccess.deleteLead(testLead);
        assertEquals("Deleting an already deleted lead should return an error", "Lead not found.", doubleDeleteResult);

        // 6. Integrity Check: Ensure other data is still there
        leads.clear();
        dataAccess.getLeadSequential(leads);
        assertEquals("The list should not be empty after one deletion", false,leads.isEmpty());

        System.out.println(TAG + "Delete verified for ID: " + testLeadLeadID + ". Remaining leads: " + leads.size());
    }
}
