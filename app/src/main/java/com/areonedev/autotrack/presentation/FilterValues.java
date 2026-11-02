package com.areonedev.autotrack.presentation;

/** Maps translated spinner positions to the stable values used by the business layer. */
final class FilterValues {
    private static final String[] STATUS = {"All Status", "Active", "Lost"};
    private static final String[] STAGES = {"All Stages", "New", "Contacted", "Visited", "Test Drive", "Negotiation"};
    private static final String[] DIVISIONS = {"All Divisions", "New Car", "Used Car"};
    private FilterValues() { }
    static String status(int index) { return value(STATUS, index); }
    static String stage(int index) { return value(STAGES, index); }
    static int stagePosition(String value) {
        for (int i = 1; i < STAGES.length; i++) if (STAGES[i].equalsIgnoreCase(value)) return i;
        return -1;
    }
    static String division(int index) { return value(DIVISIONS, index); }
    private static String value(String[] values, int index) { return values[index >= 0 && index < values.length ? index : 0]; }
}
