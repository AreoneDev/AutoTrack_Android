package com.areonedev.autotrack.integration;

import com.areonedev.autotrack.business.AccessLeads;
import com.areonedev.autotrack.objects.Vehicle;
import com.areonedev.autotrack.persistence.DataAccessObject;
import com.areonedev.autotrack.persistence.DataAccess;
import com.areonedev.autotrack.objects.Lead;
import com.areonedev.autotrack.application.*;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class BusinessPersistenceSeamTest {
    private AccessLeads al;
    private static final String dbName = Main.dbName;
    private static final String TAG = "BusinessPersistenceSeamTest";

    @org.junit.Rule public com.areonedev.autotrack.testing.IsolatedDatabase database = new com.areonedev.autotrack.testing.IsolatedDatabase();

    @Before public void setUp() {
        al = new AccessLeads();
    }

    @Test
    public void testInsertLeadSeam() {
        List<Lead> leads = new ArrayList<>();
        al.getLeads(leads);
        int initialSize = leads.size();

        // Create dates using java.util.Date
        Date now = new Date();
        Vehicle vehicle = new Vehicle("Volkswagen", "Tiguan", "2024","Comfrtline");

        // Refactored to 17-parameter constructor
        Lead newLead = com.areonedev.autotrack.testing.LeadFixtures.create("Integration", "User", "555-0000", "int@test.com", "Sales",
                "123 Test St", "Winnipeg", "MB", "Canada", "R3C 1A1",
                45000.0, vehicle, null, "NEW", now, "Integration test note", now);

        // Verify initial state
        Assert.assertEquals("New lead should have ID 0 before insertion", 0, newLead.getLeadID());

        // Perform the Seam Insert (Business -> Persistence -> SQLite)
        String result = al.insertLead(newLead);
        Assert.assertNull("Insert should return null on success, but got: " + result, result);

        // Verify ID Assignment (The DB should have updated the object)
        Assert.assertTrue("Lead ID should be > 0 after SQLite insertion", newLead.getLeadID() > 0);

        // Verify Retrieval
        List<Lead> updatedLeads = new ArrayList<>();
        al.getLeads(updatedLeads);
        Assert.assertEquals("Database size should have increased by 1", initialSize + 1, updatedLeads.size());

        Log.d(TAG, "Successfully inserted lead with assigned ID: " + newLead.getLeadID());
    }

    @Test
    public void testUpdateLeadSeam() {
        List<Lead> leads = new ArrayList<>();

        // 1. Insert a lead to update
        Date now = new Date();
        // 1. Insert a lead to update
        Lead lead = com.areonedev.autotrack.testing.LeadFixtures.create("Original", "Name", "123", null, null, null, null, null, null, null, 0, null, null, "NEW", now, "", now);
        al.insertLead(lead);
        long id = lead.getLeadID();

        // 2. Modify the lead
        lead.setLeadFirstName("Updated");
        lead.setLeadLastName("Name");
        String result = al.updateLead(lead);
        Assert.assertNull("Update should return null on success", result);

        // 3. Verify the change in the DB
        al.getLeads(leads);
        Lead found = null;
        for (Lead l : leads) {
            if (l.getLeadID() == id) found = l;
        }

        Assert.assertNotNull("Updated lead should exist in DB", found);
        Assert.assertEquals("First name should be updated in DB", "Updated", found.getLeadFirstName());
    }

    @Test
    public void testDeleteLeadSeam() {
        List<Lead> leads = new ArrayList<>();

        // 1. Insert a lead to delete
        Date now = new Date();

        Lead lead = com.areonedev.autotrack.testing.LeadFixtures.create("Delete", "Me", "999", null, null, null, null, null, null, null, 0, null, null, "NEW", now, "", now);
        al.insertLead(lead);
        al.getLeads(leads);
        int sizeAfterInsert = leads.size();

        // 2. Delete the lead
        String result = al.deleteLead(lead);
        Assert.assertNull("Delete should return null on success", result);

        // 3. Verify it is gone
        leads.clear();
        al.getLeads(leads);
        Assert.assertEquals("Size should decrease by 1", sizeAfterInsert - 1, leads.size());

        for (Lead l : leads) {
            Assert.assertNotEquals("Deleted ID should not exist", lead.getLeadID(), l.getLeadID());
        }
    }

    @Test
    public void testGetLeads() {
        List<Lead> leads = new ArrayList<>();

        // 1. Test empty (since we delete DB in @Before, it should be empty or have default data)
        al.getLeads(leads);
        int startSize = leads.size();

        // 2. Add multiple leads
        Date now = new Date();
        Vehicle vehicle = new Vehicle("Volkswagen", "Tiguan", "2024","Comfortline");

        // Refactored to 17-parameter constructor
        al.insertLead(com.areonedev.autotrack.testing.LeadFixtures.create("Integration", "User", "555-0000", "int@test.com", "Sales",
                "123 Test St", "Winnipeg", "MB", "Canada", "R3C 1A1",
                45000.0, vehicle, null, "NEW", now, "Integration test note", now));
        al.insertLead(com.areonedev.autotrack.testing.LeadFixtures.create("Integration", "User", "555-0000", "int@test.com", "Sales",
                "123 Test St", "Winnipeg", "MB", "Canada", "R3C 1A1",
                45000.0, vehicle, null, "NEW", now, "Integration test note", now));

        // 3. Verify count
        leads.clear();
        al.getLeads(leads);
        Assert.assertEquals("Should have 2 more leads than start", startSize + 2, leads.size());
    }

    @Test
    public void testGetSequential() {
        // 1. First call should return the first lead
        Lead first = al.getSequential();
        Assert.assertNotNull("First sequential lead should not be null", first);
        Assert.assertEquals("Darren", first.getLeadFirstName());

        // 2. Second call should return the second lead
        Lead second = al.getSequential();
        Assert.assertNotNull("Second sequential lead should not be null", second);
        Assert.assertEquals("Darryl", second.getLeadFirstName());

        // 3. Third call should return null (end of list)
        Lead third = al.getSequential();
        Assert.assertNotNull("Third call should not be null", third);
        Assert.assertEquals("Jamie", third.getLeadFirstName());

        al.getSequential();
        al.getSequential();
        al.getSequential();
        // 4. Forth call should return null (end of list)
        Lead seventh = al.getSequential();

        Assert.assertNull("Last+1 call should be null as list is exhausted", seventh);

        // 5. Fifth call should trigger a reload and return the first lead again
        Lead restart = al.getSequential();
        Assert.assertNotNull("Should restart and return first lead", restart);
        Assert.assertEquals("Darren", restart.getLeadFirstName());
    }

    @Test
    public void testGetLeadByName_Phone() {
        String testFirst = "Search";
        String testLast = "Target";
        String testPhone = "999-888-7777";
        Date now = new Date();

        // 1. Insert a specific lead to find
        Lead searchTarget = com.areonedev.autotrack.testing.LeadFixtures.create(testFirst, testLast, testPhone, null, null, null, null, null, null, null, 0, null, null, "NEW", now, "", now);
        al.insertLead(searchTarget);

        // 2. Test successful search
        Lead found = al.getLeadByName_Phone(testFirst + " " + testLast, testPhone);
        Assert.assertNotNull("Should find the lead by combined name and phone", found);
        Assert.assertEquals(testPhone, found.getLeadPhoneNumber());

        // 3. Test failed search (wrong phone)
        Lead notFound = al.getLeadByName_Phone(testFirst + " " + testLast + " User", "000-000-0000");
        Assert.assertNull("Should return null for non-existent phone number", notFound);

        // 4. Test failed search (empty inputs)
        Lead emptySearch = al.getLeadByName_Phone("", "");
        Assert.assertNull("Should return null for empty search criteria", emptySearch);
    }

    @Test
    public void testGetRandom() {
        // 1. Insert a lead and get its real database ID
        Date now = new Date();
        Lead target = com.areonedev.autotrack.testing.LeadFixtures.create("Random", "Target", "555", null, null, null, null, null, null, null, 0, null, null, "NEW", now, "", now);
        al.insertLead(target);
        long realID = target.getLeadID();
        Assert.assertTrue("ID should be valid", realID > 0);

        // 2. Test retrieval by ID
        Lead found = al.getRandom(realID);
        Assert.assertNotNull("Should find lead with ID: " + realID, found);
        Assert.assertEquals("Random", found.getLeadFirstName());
        Assert.assertEquals(realID, found.getLeadID());

        // 3. Test invalid ID
        Lead invalid = al.getRandom(-1);
        Assert.assertNull("Should return null for negative ID", invalid);

        // 4. Test non-existent ID
        Lead nonExistent = al.getRandom(99999);
        Assert.assertNull("Should return null for ID not in database", nonExistent);
    }
}
