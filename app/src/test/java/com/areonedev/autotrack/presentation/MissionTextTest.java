package com.areonedev.autotrack.presentation;

import com.areonedev.autotrack.R;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class MissionTextTest {
    @Test public void recognizesGeneratedMissionButPreservesUnknownText() {
        assertEquals(R.string.mission_urgent, MissionText.resource("🚨 URGENT: Lead replied. Respond within 48h!"));
        assertEquals(R.string.mission_high, MissionText.resource("🔥 High Priority: Nurture VISITED (Score: 110)"));
        assertEquals(0, MissionText.resource("A customer-written note"));
    }
}
