package com.areonedev.autotrack.persistence;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.areonedev.autotrack.objects.Lead;
import com.areonedev.autotrack.testing.IsolatedDatabase;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import static org.junit.Assert.*;

/** Existing CRUD scenarios retained; real DAO expectations and isolated synthetic database. */
@RunWith(AndroidJUnit4.class)
public class DataAccessTest {
    @Rule public IsolatedDatabase database = new IsolatedDatabase();
    private Lead synthetic() {
        return new Lead("Synthetic", "Customer", "4165550100", "test@example.invalid", "Sales",
                "", "", "ON", "Canada", "", 100, null, null, "Test only");
    }
    public static void dataAccessTest(DataAccess dao) {
        List<Lead> before = new ArrayList<>();
        assertNull(dao.getLeadSequential(before));
        Lead lead = new Lead("Synthetic", "Shared", "4165550199", null, null, null, null, null, null, null, 0, null, null, null);
        assertNull(dao.insertLead(lead));
        List<Lead> after = new ArrayList<>();
        assertNull(dao.getLeadSequential(after));
        assertEquals(before.size() + 1, after.size());
        assertTrue(lead.getLeadID() > 0);
        assertEquals(1, dao.getLeadRandom(lead).size());
    }
    @Test public void testGetLeadSequential() {
        List<Lead> result = new ArrayList<>();
        assertNull(database.dao.getLeadSequential(result));
        int size = result.size();
        assertTrue("Fresh DAO seeds demo leads", size > 0);
        assertNull(database.dao.getLeadSequential(result));
        assertEquals("Retrieval replaces result instead of appending", size, result.size());
    }
    @Test public void testRandom() {
        Lead lead = synthetic();
        lead.setLeadID(Long.MAX_VALUE);
        assertTrue(database.dao.getLeadRandom(lead).isEmpty());
        assertNull(database.dao.insertLead(lead));
        assertEquals(1, database.dao.getLeadRandom(lead).size());
    }
    @Test public void testInsert() { dataAccessTest(database.dao); }
    @Test public void testUpdateLead() {
        Lead lead = synthetic();
        assertNull(database.dao.insertLead(lead));
        long id = lead.getLeadID();
        lead.setLeadPhoneNumber("4165550101");
        assertNull(database.dao.updateLead(lead));
        Lead actual = database.dao.getLeadRandom(lead).get(0);
        assertEquals(id, actual.getLeadID());
        assertEquals("4165550101", actual.getLeadPhoneNumber());
        assertEquals(100, actual.getLeadBudget(), 0.0);
        lead.setLeadID(Long.MAX_VALUE);
        assertNotNull(database.dao.updateLead(lead));
    }
    @Test public void testDeleteLead() {
        Lead lead = synthetic();
        assertNull(database.dao.insertLead(lead));
        List<Lead> before = new ArrayList<>();
        assertNull(database.dao.getLeadSequential(before));
        assertNull(database.dao.deleteLead(lead));
        assertTrue(database.dao.getLeadRandom(lead).isEmpty());
        assertNotNull(database.dao.deleteLead(lead));
        List<Lead> after = new ArrayList<>();
        assertNull(database.dao.getLeadSequential(after));
        assertEquals(before.size() - 1, after.size());
    }
}
