package com.areonedev.autotrack.integration;

import com.areonedev.autotrack.application.Main;
import com.areonedev.autotrack.application.Services;
import com.areonedev.autotrack.business.AccessLeads;
import com.areonedev.autotrack.objects.Lead;
import com.areonedev.autotrack.objects.Vehicle;
import com.areonedev.autotrack.persistence.DataAccessStub;

import junit.framework.TestCase;

import org.junit.Assert;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class BusinessPersistenceSeamTest extends TestCase {

    private static final String TAG = "BusinessPersistenceSeamTest, ";
    private AccessLeads al;
    public BusinessPersistenceSeamTest(String arg0)
    {
        super(arg0);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        Services.createDataAccess(new DataAccessStub());
        al = new AccessLeads(); // Initialize the business controller
    }

    // Runs AFTER every test method
    @Override
    public void tearDown() throws Exception {
        Services.closeDataAccess(); // Reset the Stub/Database
        super.tearDown();
    }

    public void testGetLeadsSeam() {
        System.out.println(TAG + " Running testGetLeadsSeam.");

        List<Lead> leads = new ArrayList<>();
        al.getLeads(leads);
        assertEquals(3, leads.size());

        System.out.println(TAG+" Finished testGetLeadsSeam.");
    }

    public void testGetLeadByName_Phone(){
        // 1. Positive Test: Search for an existing lead (Alice Chen)
// Note: Alice Chen is part of the default Stub data
        Lead found = al.getLeadByName_Phone("Alice Chen", "204-555-8123");
        assertEquals("Should find Alice Chen in the database", "Alice Chen",found.getLeadName());
        assertEquals("Alice Chen", found.getLeadName());
        assertEquals("204-555-8123", found.getLeadPhoneNumber());
        assertTrue("Existing lead should have a valid ID", found.getLeadID() > 0);

// 2. Negative Test: Correct name, but wrong phone number
        Lead notFound = al.getLeadByName_Phone("Alice Chen", "000-000-0000");
        assertNull("Should not find a lead with a mismatched phone number", notFound);

// 3. Negative Test: Lead that does not exist at all
        Lead ghost = al.getLeadByName_Phone("Non Existent", "123-456-7890");
        assertNull("Should return null for non-existent leads", ghost);

// 4. Validation Test: Empty or Null parameters
        assertNull("Should return null for empty name", al.getLeadByName_Phone("", "204-555-8123"));
        assertNull("Should return null for null phone", al.getLeadByName_Phone("Alice Chen", null));

// 5. Global Creation Count Check
// Every time we called getLeadByName_Phone, a 'new Lead()' criteria object was created.

        System.out.println(TAG + " Finished testGetLeadByName_Phone.");
    }

    public void testGetSequential(){
        System.out.println(TAG + " Running testGetSequential.");
        Lead l1 = al.getSequential();
        assertEquals("Alice",l1.getLeadFirstName());

        // Second call -> Brian
        Lead l2 = al.getSequential();
        assertEquals("Brian Miller", l2.getLeadName());

        // Third call -> Sophia
        Lead l3 = al.getSequential();
        assertEquals("Sophia Martinez", l3.getLeadName());

        // Fourth call -> Already hit the end of the list, so should return null
        Lead l4 = al.getSequential();
        assertNull(l4);

        System.out.println(TAG+" Finished testGetSequential.");
    }

    public void testGetRandom(){
        System.out.println(TAG+" Running testGetRandom.");

        Lead lead = al.getRandom(1);
        assertEquals("Alice Chen",lead.getLeadName());

        lead = al.getRandom(0);
        assertNull("Zero IDs should return null", lead);

        // Test another invalid ID (Negative)
        lead = al.getRandom(-5);
        assertNull("Negative IDs should return null", lead);
    }

    public void testInsertLeadSeam() {
        System.out.println(TAG + " Running testInsertLeadSeam.");
        List<Lead> leads = new ArrayList<>();
        al.getLeads(leads);
        int initialSize = leads.size();

        Vehicle interest = new Vehicle("Volkswagen", "Tiguan", "2024","Comfortline");
        Date now = new Date();
        Lead newLead = com.areonedev.autotrack.testing.LeadFixtures.create(
                "Integration", "User", "555-0000", "int@test.com", "New Cars",
                "123 Test St", "Winnipeg", "MB", "Canada", "R3C 1A1",
                45000.0, interest, null, "NEW", now, "Integration test note", now
        );
        assertEquals(0,newLead.getLeadID());
        String result = al.insertLead(newLead);
        assertEquals(4,newLead.getLeadID());
        al.getLeads(leads);
        assertNull("Insert should return null on success", result);
        Assert.assertEquals("Database size should increase by 1", initialSize + 1, leads.size());

        System.out.println(TAG+" Finished testInsertLeadSeam.");
    }

    public void testUpdateLeadSeam() {
        System.out.println(TAG + " Running testUpdateLeadSeam.");
        List<Lead> leads = new ArrayList<>();
        al.getLeads(leads);
        Lead target = leads.get(0);
        target.setLeadName("Alice Updated");

        String result = al.updateLead(target);
        assertNull("Update should return null on success", result);

        // Verify the change reached the persistence layer
        Assert.assertEquals("Alice Updated", leads.get(0).getLeadName());

        System.out.println(TAG+" Finished testUpdateLeadSeam.");
    }

    public void testDeleteLeadSeam() {
        System.out.println(TAG + " Running testDeleteLeadSeam.");
        List<Lead> leads = new ArrayList<>();
        al.getLeads(leads);
        int initialSize = leads.size();
        Lead target = leads.get(0);

        String result = al.deleteLead(target);
        al.getLeads(leads);
        assertNull("Delete should return null on success", result);
        Assert.assertEquals("Database size should decrease", initialSize - 1, leads.size());

        System.out.println(TAG+" Finished testDeleteLeadSeam.");
    }
}
