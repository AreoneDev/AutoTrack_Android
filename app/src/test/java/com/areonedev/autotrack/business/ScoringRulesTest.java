package com.areonedev.autotrack.business;

import com.areonedev.autotrack.application.Services;
import com.areonedev.autotrack.objects.Lead;
import com.areonedev.autotrack.persistence.DataAccessStub;
import org.junit.*;
import java.util.Calendar;
import java.util.TimeZone;
import static org.junit.Assert.*;

/** R-SCORE: stage/engagement decision table and silence boundary values. */
public class ScoringRulesTest {
    private TimeZone previous;
    private ScoringService service;
    @Before public void setUp() {
        previous = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        Services.closeDataAccess();
        Services.createDataAccess(new DataAccessStub());
        service = new ScoringService();
    }
    @After public void tearDown() {
        Services.closeDataAccess();
        TimeZone.setDefault(previous);
    }
    private Lead lead(String stage, String notes, int days) {
        Lead lead = new Lead();
        lead.setLeadStage(stage);
        lead.setLeadNotes(notes);
        Calendar created = Calendar.getInstance();
        created.add(Calendar.DATE, -days);
        lead.setLeadCreatedAt(created.getTime());
        return lead;
    }
    @Test public void stageAndEngagementDecisionTable() {
        String[] stages = {null, "NEW", "CONTACTED", "VISITED", "TEST_DRIVE", "NEGOTIATION", "CLOSED", "unknown", "visited"};
        double[] totals = {0, 40, 50, 80, 110, 160, 0, 10, 80};
        for (int i = 0; i < stages.length; i++) {
            assertEquals("stage=" + stages[i], totals[i], service.calculateScore(lead(stages[i], null, 0)), 0.0);
            assertEquals("keyword stage=" + stages[i], totals[i] + 20, service.calculateScore(lead(stages[i], "HOT ready urgent", 0)), 0.0);
        }
    }
    @Test public void silenceThreeFourSevenEightDayBoundaries() {
        int[] days = {0, 3, 4, 7, 8};
        double[] scores = {40, 40, 55, 55, 70};
        for (int i = 0; i < days.length; i++)
            assertEquals("days=" + days[i], scores[i], service.calculateScore(lead("NEW", "", days[i])), 0.0);
    }
    @Test public void recentInteractionResetsSilenceWeight() {
        Lead lead = lead("NEW", "", 8);
        assertEquals(70, service.calculateScore(lead), 0.0);
        lead.setLastInteractionDate(Calendar.getInstance().getTime());
        assertEquals(40, service.calculateScore(lead), 0.0);
    }
}
