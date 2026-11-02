package com.areonedev.autotrack.presentation;

import org.junit.Test;
import static org.junit.Assert.*;

public class FilterValuesTest {
    @Test public void selectedPositionsMapToStableBusinessValues() {
        assertEquals("All Status", FilterValues.status(0));
        assertEquals("Lost", FilterValues.status(2));
        assertEquals("Test Drive", FilterValues.stage(4));
        assertEquals("Used Car", FilterValues.division(2));
        assertEquals("All Status", FilterValues.status(-1));
        assertEquals(1, FilterValues.stagePosition("New"));
        assertEquals(-1, FilterValues.stagePosition("Custom"));
    }
}
