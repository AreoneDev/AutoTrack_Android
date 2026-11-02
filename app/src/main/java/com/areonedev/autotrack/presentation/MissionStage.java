package com.areonedev.autotrack.presentation;

import com.areonedev.autotrack.R;
import java.util.Locale;

/** Selects presentation text while retaining canonical stage values. */
final class MissionStage {
    private MissionStage() { }
    static int resource(String value) {
        String stage = value == null ? "NEW" : value.toUpperCase(Locale.ROOT);
        switch (stage) {
            case "NEW": return R.string.mission_initial;
            case "DISCOVERY": return R.string.mission_discovery;
            case "TEST DRIVE": return R.string.mission_drive;
            case "NEGOTIATION": return R.string.mission_negotiation;
            default: return R.string.mission_follow_up;
        }
    }
}
