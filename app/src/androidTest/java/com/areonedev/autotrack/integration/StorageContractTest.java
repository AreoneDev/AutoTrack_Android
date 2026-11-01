package com.areonedev.autotrack.integration;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.areonedev.autotrack.objects.Lead;
import com.areonedev.autotrack.objects.Task;
import com.areonedev.autotrack.persistence.CryptoManager;
import com.areonedev.autotrack.testing.IsolatedDatabase;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class StorageContractTest {
    @Rule public IsolatedDatabase database = new IsolatedDatabase();
    private Lead lead() {
        return new Lead("合成 Alice", "测试", "4165550100", "synthetic@example.invalid", "Sales",
                "", "Toronto", "ON", "Canada", "", 12500.5, null, null, "Synthetic only");
    }
    @Test public void leadCrudAndReopenPreserveIdentityAndUnicode() {
        Lead first = lead(), second = lead();
        assertNull(database.dao.insertLead(first));
        assertNull(database.dao.insertLead(second));
        assertTrue(first.getLeadID() > 0);
        assertNotEquals(first.getLeadID(), second.getLeadID());
        first.setLeadNotes("Updated 合成");
        first.setLeadStatus(false);
        assertNull(database.dao.updateLead(first));
        database.dao.close();
        database.dao.open(database.file.getAbsolutePath());
        List<Lead> found = database.dao.getLeadRandom(first);
        assertEquals(1, found.size());
        Lead restored = found.get(0);
        assertEquals(first.getLeadID(), restored.getLeadID());
        assertEquals(first.getLeadName(), restored.getLeadName());
        assertEquals(first.getLeadPhoneNumber(), restored.getLeadPhoneNumber());
        assertEquals(first.getLeadEmail(), restored.getLeadEmail());
        assertEquals(12500.5, restored.getLeadBudget(), 0.0);
        assertEquals("Updated 合成", restored.getLeadNotes());
        assertFalse(restored.getLeadStatus());
        assertNull(database.dao.deleteLead(first));
        assertTrue(database.dao.getLeadRandom(first).isEmpty());
        assertNotNull(database.dao.deleteLead(first));
        assertNotNull(database.dao.updateLead(first));
        assertEquals(1, database.dao.getLeadRandom(second).size());
    }
    @Test public void piiColumnsContainAuthenticatedCiphertext() {
        Lead lead = lead();
        assertNull(database.dao.insertLead(lead));
        try (SQLiteDatabase raw = SQLiteDatabase.openDatabase(database.file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
             Cursor cursor = raw.rawQuery("SELECT FirstName,LastName,PhoneNumber,Email FROM Leads WHERE LeadID=?", new String[]{"" + lead.getLeadID()})) {
            assertTrue(cursor.moveToFirst());
            String[] plain = {lead.getLeadFirstName(), lead.getLeadLastName(), lead.getLeadPhoneNumber(), lead.getLeadEmail()};
            CryptoManager crypto = new CryptoManager();
            for (int i = 0; i < plain.length; i++) {
                String stored = cursor.getString(i);
                assertNotEquals("PII column " + i, plain[i], stored);
                assertTrue(Base64.decode(stored, Base64.DEFAULT).length >= 28);
                assertEquals(plain[i], crypto.decrypt(stored));
            }
        }
    }
    @Test public void taskCompletionTransitionsSurviveReopenAndDeletion() {
        Lead lead = lead();
        assertNull(database.dao.insertLead(lead));
        Task later = new Task(lead, "Synthetic later", new Date(2000));
        Task earlier = new Task(lead, "Synthetic earlier", new Date(1000));
        assertNull(database.dao.insertTask(later));
        assertNull(database.dao.insertTask(earlier));
        later.setCompleted(true);
        assertNull(database.dao.updateTask(later));
        database.dao.close();
        database.dao.open(database.file.getAbsolutePath());
        List<Task> tasks = new ArrayList<>();
        assertNull(database.dao.getTaskSequential(tasks));
        assertEquals(2, tasks.size());
        assertEquals(earlier.getEventID(), tasks.get(0).getEventID());
        assertTrue(tasks.get(1).isCompleted());
        later.setCompleted(false);
        assertNull(database.dao.updateTask(later));
        tasks.clear();
        assertNull(database.dao.getTaskSequential(tasks));
        assertFalse(tasks.get(1).isCompleted());
        assertNull(database.dao.deleteTask(later));
        assertNotNull(database.dao.deleteTask(later));
    }
}
