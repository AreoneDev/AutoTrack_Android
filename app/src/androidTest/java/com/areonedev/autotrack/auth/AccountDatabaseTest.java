package com.areonedev.autotrack.auth;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import com.areonedev.autotrack.application.Services;
import com.areonedev.autotrack.persistence.DataAccess;
import com.areonedev.autotrack.objects.Lead;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class AccountDatabaseTest {
    @Test public void switchingAccountsSeparatesRowsAndInvalidatesOldHandles() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String a = UUID.randomUUID().toString(), b = UUID.randomUUID().toString();
        try {
            Services.initialize(context);
            Services.openAccount(a);
            DataAccess old = Services.getDataAccess("AutoTrack");
            try { old.open(context.getDatabasePath("AutoTrack.db").getAbsolutePath());
                fail("Account handles cannot open another database."); }
            catch (IllegalStateException expected) { }
            Lead synthetic = new Lead();
            synthetic.setLeadFirstName("Synthetic"); synthetic.setLeadLastName("Isolation");
            assertNull(old.insertLead(synthetic));
            Services.openAccount(b);
            List<Lead> rows = new ArrayList<>();
            assertNull(Services.getDataAccess("AutoTrack").getLeadSequential(rows));
            assertTrue(rows.isEmpty());
            try { old.getLeadSequential(rows); fail("Old handle must be rejected."); }
            catch (IllegalStateException expected) { }
            Services.openAccount(a);
            rows.clear(); Services.getDataAccess("AutoTrack").getLeadSequential(rows);
            assertEquals(1, rows.size());
        } finally {
            Services.closeDataAccess();
            context.deleteDatabase(AccountSession.databaseName(a));
            context.deleteDatabase(AccountSession.databaseName(b));
        }
    }
}
