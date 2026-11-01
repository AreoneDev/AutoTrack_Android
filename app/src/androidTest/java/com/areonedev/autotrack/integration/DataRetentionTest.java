package com.areonedev.autotrack.integration;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.areonedev.autotrack.objects.Lead;
import com.areonedev.autotrack.testing.IsolatedDatabase;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DataRetentionTest {
    @Rule public IsolatedDatabase database = new IsolatedDatabase();
    @Test public void deletedCustomersDoNotReappearAfterReopen() {
        List<Lead> leads = new ArrayList<>();
        assertNull(database.dao.getLeadSequential(leads));
        for (Lead lead : leads) assertNull(database.dao.deleteLead(lead));
        leads.clear();
        assertNull(database.dao.getLeadSequential(leads));
        assertTrue(leads.isEmpty());
        database.dao.close();
        database.dao.open(database.file.getAbsolutePath());
        assertNull(database.dao.getLeadSequential(leads));
        assertTrue("Deleted customers must not reappear on reopen", leads.isEmpty());
    }
}
