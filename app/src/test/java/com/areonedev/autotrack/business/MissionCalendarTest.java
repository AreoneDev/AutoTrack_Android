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
        return date(2026, Calendar.MARCH, day);
    }
    private Date date(int year, int month, int day) {
        Calendar c = Calendar.getInstance();
        c.clear(); c.set(year, month, day, 12, 0);
        return c.getTime();
    }
    @Test public void gratitudeOnNextCalendarDayAcrossSpringDst() {
        Lead lead = new Lead();
        lead.setLeadCreatedAt(date(8));
        assertEquals("🙏 Gratitude: Thank You & Info Swap", new ScoringService().getScientificMission(lead, date(9)));
    }

    @Test public void gratitudeAcrossFallDstAndCalendarBoundaries() {
        Date[][] pairs = {
                {date(2026, Calendar.NOVEMBER, 1), date(2026, Calendar.NOVEMBER, 2)},
                {date(2026, Calendar.DECEMBER, 31), date(2027, Calendar.JANUARY, 1)},
                {date(2028, Calendar.FEBRUARY, 28), date(2028, Calendar.FEBRUARY, 29)},
                {date(2028, Calendar.FEBRUARY, 29), date(2028, Calendar.MARCH, 1)}
        };
        for (Date[] pair : pairs) {
            Lead lead = new Lead();
            lead.setLeadCreatedAt(pair[0]);
            assertEquals("target=" + pair[1], "🙏 Gratitude: Thank You & Info Swap",
                    new ScoringService().getScientificMission(lead, pair[1]));
        }
    }

    @Test public void silentMilestonesAcrossSpringDst() {
        Lead lead = new Lead();
        lead.setLeadCreatedAt(date(6));
        ScoringService service = new ScoringService();
        assertEquals("💡 New Ideas: Follow up thoughts", service.getScientificMission(lead, date(9)));
        assertEquals("📈 Market Update: Inventory/Trade-in", service.getScientificMission(lead, date(14)));
    }

    @Test public void replyUrgencyUsesCalendarDaysAndExcludesDatesBeforeReply() {
        Lead lead = new Lead();
        lead.setLeadCreatedAt(date(1));
        lead.setLeadStage("NEW");
        lead.setLastInteractionBy("LEAD");
        lead.setLastInteractionDate(date(8));
        ScoringService service = new ScoringService();
        assertFalse(service.getScientificMission(lead, date(7)).contains("URGENT"));
        for (int day = 8; day <= 10; day++) {
            assertEquals("🚨 URGENT: Lead replied. Respond within 48h!",
                    service.getScientificMission(lead, date(day)));
        }
        assertEquals("💡 New Ideas: Follow up thoughts", service.getScientificMission(lead, date(11)));
    }
}
