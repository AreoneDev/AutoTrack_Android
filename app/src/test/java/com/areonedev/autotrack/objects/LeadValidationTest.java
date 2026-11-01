package com.areonedev.autotrack.objects;

import org.junit.Test;
import static org.junit.Assert.*;

public class LeadValidationTest {
    private Lead create(String first, String last) {
        return new Lead(first, last, null, null, null, null, null, null, null, null, 0, null, null, null);
    }
    @Test public void missingNamesEquivalencePartitions() {
        String[] empty = {null, "", " \t "};
        for (String first : empty) for (String last : empty) {
            assertThrows(IllegalArgumentException.class, () -> create(first, last));
        }
    }
    @Test public void namePresenceDecisionTable() {
        assertEquals("Alice", create(" Alice ", null).getLeadName());
        assertEquals("王", create(null, " 王 ").getLeadName());
        assertEquals("Alice 王", create(" Alice ", " 王 ").getLeadName());
    }
    @Test public void nameEditTransitions() {
        Lead lead = create("Alice", "王");
        lead.setLeadName("  John Quincy Adams  ");
        assertEquals("John", lead.getLeadFirstName());
        assertEquals("Quincy Adams", lead.getLeadLastName());
        lead.setLeadName("Cher");
        assertEquals("", lead.getLeadLastName());
        lead.setLeadName(null);
        assertEquals("", lead.getLeadName());
    }
}
