package com.areonedev.autotrack.presentation;

import com.areonedev.autotrack.R;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class MissionStageTest {
    @Test public void mapsCanonicalAndUnknownStagesToLocalizedResources() {
        assertEquals(R.string.mission_initial, MissionStage.resource("New"));
        assertEquals(R.string.mission_drive, MissionStage.resource("Test Drive"));
        assertEquals(R.string.mission_follow_up, MissionStage.resource("Custom"));
    }
}
