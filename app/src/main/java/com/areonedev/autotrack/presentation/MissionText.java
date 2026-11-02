package com.areonedev.autotrack.presentation;

import android.content.Context;
import com.areonedev.autotrack.R;

/** Localizes recognized generated missions without changing stored or user-authored text. */
final class MissionText {
    private MissionText() { }
    static int resource(String value) {
        if (value == null) return R.string.mission_routine;
        if (value.startsWith("🔥 High Priority: Nurture ")) return R.string.mission_high;
        if (value.startsWith("Standard Follow-up: ")) return R.string.mission_standard;
        switch (value) {
            case "🙏 Gratitude: Thank You & Info Swap": return R.string.mission_gratitude;
            case "🚨 URGENT: Lead replied. Respond within 48h!": return R.string.mission_urgent;
            case "💡 New Ideas: Follow up thoughts": return R.string.mission_ideas;
            case "📈 Market Update: Inventory/Trade-in": return R.string.mission_market;
            case "🎥 Resource: Hidden feature video": return R.string.mission_resource;
            case "🔍 Checking In: Specific specs": return R.string.mission_specs;
            case "❄️ Seasonal: Service specials": return R.string.mission_seasonal;
            case "🤝 Relationship: High-level check-in": return R.string.mission_relationship;
            case "🎂 Anniversary: Yearly check-in": return R.string.mission_anniversary;
            case "Follow up": return R.string.mission_follow_up_plain;
            default: return 0;
        }
    }
    static String display(Context context, String value, String stage, int score) {
        int resource = resource(value);
        if (resource == 0) return value;
        int position = FilterValues.stagePosition(stage);
        String[] stages = context.getResources().getStringArray(R.array.filter_stages);
        String label = position >= 0 && position < stages.length ? stages[position] : stage == null ? "" : stage;
        if (resource == R.string.mission_high) return context.getString(resource, label, score);
        if (resource == R.string.mission_standard) return context.getString(resource, label);
        return context.getString(resource);
    }
}
