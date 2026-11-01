package com.areonedev.autotrack.business;

import com.areonedev.autotrack.application.Services;
import com.areonedev.autotrack.objects.Lead;
import com.areonedev.autotrack.persistence.DataAccessStub;
import org.junit.*;
import java.util.*;
import static org.junit.Assert.*;

/** R-DATE: next calendar day must remain day one across Toronto's spring DST change. */
public class MissionCalendarTest {
    private TimeZone previous;
    @Before public void setUp() {
        previous = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/Toronto"));
        Services.closeDataAccess();
        Services.createDataAccess(new DataAccessStub());
    }
    @After public void tearDown() {
        Services.closeDataAccess();
        TimeZone.setDefault(previous);
    }
    private Date date(int day) {
        Calendar c = Calendar.getInstance();
        c.clear(); c.set(2026, Calendar.MARCH, day, 12, 0);
        return c.getTime();
    }
    @Test public void gratitudeOnNextCalendarDayAcrossSpringDst() {
        Lead lead = new Lead();
        lead.setLeadCreatedAt(date(8));
        assertEquals("🙏 Gratitude: Thank You & Info Swap", new ScoringService().getScientificMission(lead, date(9)));
    }
}
