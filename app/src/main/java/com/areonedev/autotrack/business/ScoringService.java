package com.areonedev.autotrack.business;

import com.areonedev.autotrack.objects.Lead;
import com.areonedev.autotrack.objects.Task;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.Collections;
import java.util.TimeZone;

public class ScoringService {
    protected static final int THRESHOLD = 100;
    private final int[] SILENT_MILESTONES = {3, 8, 15, 30, 90, 180, 365};
    private AccessTasks accessTasks = new AccessTasks();

    public double calculateScore(Lead lead) {
        double score = 0;
        // 1️⃣ Stage Weight (Linear)
        score += getStageWeight(lead.getLeadStage());
        // 2️⃣ Follow-up urgency
        score += getTimeWeight(lead);
        // 3️⃣ Engagement weight (Now uses the passed leadTasks)
        score += getEngagementWeight(lead);

        return score;
    }

    public List<Task> getFullTimeline(Lead lead) {
        List<Task> timeline = new ArrayList<>();
        if (lead == null || lead.getLeadCreatedAt() == null) return timeline;

        accessTasks.getTasksByLead(timeline, lead);

        // Determine Pivot Date: If lead replied, reset timeline to that date.
        Date pivotDate = (lead.getLastInteractionDate() != null && "LEAD".equals(lead.getLastInteractionBy()))
                ? lead.getLastInteractionDate()
                : lead.getLeadCreatedAt();

        Calendar pivotCal = Calendar.getInstance();
        pivotCal.setTime(pivotDate);
        resetTime(pivotCal);

        Calendar todayCal = Calendar.getInstance();
        resetTime(todayCal);

        // 1. Handle Day 1 Gratitude (Always from Creation)
        String gratitudeTitle = "🙏 Gratitude: Thank You & Info Swap";
        if (!containsTask(timeline, gratitudeTitle)) {
            Calendar day1Cal = Calendar.getInstance();
            day1Cal.setTime(lead.getLeadCreatedAt());
            day1Cal.add(Calendar.DAY_OF_YEAR, 1);
            resetTime(day1Cal);

            if (!day1Cal.after(todayCal)) {
                Task t = new Task(lead, gratitudeTitle, day1Cal.getTime());
                t.setCompleted(true);
                accessTasks.insertTask(t); // PERSIST
                timeline.add(t);
            }
        }

        // 2. Handle 48h Urgency if Lead Replied
        String urgentTitle = "🚨 URGENT: Lead replied. Respond within 48h!";
        if ("LEAD".equals(lead.getLastInteractionBy()) && !containsTask(timeline, urgentTitle)) {
            Task urgent = new Task(lead, urgentTitle, todayCal.getTime());
            long daysFromPivot = getDaysDiff(pivotCal, todayCal);
            urgent.setCompleted(daysFromPivot > 2);
            accessTasks.insertTask(urgent); // PERSIST
            timeline.add(urgent);
        }

        // 3. Silent Milestones from Pivot
        for (int milestone : SILENT_MILESTONES) {
            String milestoneTitle = getMissionNameByDay(milestone);
            if (!containsTask(timeline, milestoneTitle)) {
                Calendar mCal = (Calendar) pivotCal.clone();
                mCal.add(Calendar.DAY_OF_YEAR, milestone);
                resetTime(mCal);

                if (!mCal.after(todayCal)) {
                    Task t = new Task(lead, milestoneTitle, mCal.getTime());
                    t.setCompleted(mCal.before(todayCal));
                    accessTasks.insertTask(t); // PERSIST
                    timeline.add(t);
                }
            }
        }

        // 4. Sort by Date (Past to Present)
        Collections.sort(timeline, (t1, t2) -> t1.getDate().compareTo(t2.getDate()));

        return timeline;
    }

    public String getScientificMission(Lead lead, Date targetDate) {
        if (lead == null || lead.getLeadCreatedAt() == null) return null;

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
        String targetDateStr = sdf.format(targetDate);

        // Check for Manual Tasks in the database for this specific date
        List<Task> leadTasks = new ArrayList<>();
        accessTasks.getTasksByLead(leadTasks, lead); // Fetch tasks for this lead

        if (!leadTasks.isEmpty()) {
            for (Task task : leadTasks) {
                if (task.getDate() != null) {
                    String taskDateStr = sdf.format(task.getDate());
                    if (targetDateStr.equals(taskDateStr)) {
                        // If a manual task exists (e.g., "Test Drive"), return its title
                        return task.getTitle();
                    }
                }
            }
        }

        Calendar targetCal = Calendar.getInstance();
        targetCal.setTime(targetDate);
        resetTime(targetCal);

        // Fixed Day 1 Logic
        Calendar createdCal = Calendar.getInstance();
        createdCal.setTime(lead.getLeadCreatedAt());
        resetTime(createdCal);
        if (getDaysDiff(createdCal, targetCal) == 1) {
            return "🙏 Gratitude: Thank You & Info Swap";
        }

        // Dynamic Pivot Logic
        Date pivotDate = (lead.getLastInteractionDate() != null && "LEAD".equals(lead.getLastInteractionBy()))
                ? lead.getLastInteractionDate()
                : lead.getLeadCreatedAt();
        Calendar pivotCal = Calendar.getInstance();
        pivotCal.setTime(pivotDate);
        resetTime(pivotCal);

        long daysFromPivot = getDaysDiff(pivotCal, targetCal);

        // 48h Response Rule
        if ("LEAD".equals(lead.getLastInteractionBy()) && daysFromPivot >= 0 && daysFromPivot <= 2) {
            return "🚨 URGENT: Lead replied. Respond within 48h!";
        }

        // Silent Timeline
        for (int milestone : SILENT_MILESTONES) {
            if (daysFromPivot == milestone) {
                return getMissionNameByDay(milestone);
            }
        }

        // Score-based Overrides (e.g. Negotiation)
        double score = calculateScore(lead);
        if (score >= THRESHOLD) {
            return "🔥 High Priority: Nurture " + lead.getLeadStage() + " (Score: " + (int) score + ")";
        }

        return "Standard Follow-up: " + lead.getLeadStage();
    }

    private String getMissionNameByDay(int day) {
        switch (day) {
            case 3:
                return "💡 New Ideas: Follow up thoughts";
            case 8:
                return "📈 Market Update: Inventory/Trade-in";
            case 15:
                return "🎥 Resource: Hidden feature video";
            case 30:
                return "🔍 Checking In: Specific specs";
            case 90:
                return "❄️ Seasonal: Service specials";
            case 180:
                return "🤝 Relationship: High-level check-in";
            case 365:
                return "🎂 Anniversary: Yearly check-in";
            default:
                return "Follow up";
        }
    }

    private double getStageWeight(String stage) {
        if (stage == null) return 0;
        switch (stage.toUpperCase()) {
            case "NEW":
                return 40;
            case "CONTACTED":
                return 50;
            case "VISITED":
                return 60;
            case "TEST_DRIVE":
                return 70;
            case "NEGOTIATION":
                return 100;
            case "CLOSED":
                return 0;
            default:
                return 10;
        }
    }

    private double getTimeWeight(Lead lead) {
        // Use Pivot Date to calculate how "stale" the lead is
        Date pivotDate = (lead.getLastInteractionDate() != null) ? lead.getLastInteractionDate() : lead.getLeadCreatedAt();
        Calendar pivotCal = Calendar.getInstance();
        pivotCal.setTime(pivotDate);
        resetTime(pivotCal);

        Calendar today = Calendar.getInstance();
        resetTime(today);

        long daysSilent = getDaysDiff(pivotCal, today);
        if (daysSilent > 7) return 30; // Overdue/Stale
        if (daysSilent > 3) return 15; // Getting cold
        return 0;
    }

    private double getEngagementWeight(Lead lead) {
        double engagementScore = 0;

        // Since we have no Task List, we derive engagement from the Lead's current status
        String stage = lead.getLeadStage() != null ? lead.getLeadStage().toUpperCase() : "";

        // If they have reached high-intent stages, they get an engagement bonus
        // This replaces the "validVisitCount" logic
        if (stage.equals("VISITED")) {
            engagementScore += 20;
        } else if (stage.equals("TEST_DRIVE")) {
            engagementScore += 40;
        } else if (stage.equals("NEGOTIATION")) {
            engagementScore += 60;
        }

        // Sentiment fallback: Check notes for "hot" keywords
        if (lead.getLeadNotes() != null) {
            String notes = lead.getLeadNotes().toLowerCase();
            if (notes.contains("hot") || notes.contains("ready") || notes.contains("urgent")) {
                engagementScore += 20;
            }
        }

        return engagementScore;
    }

    private long getDaysDiff(Calendar start, Calendar end) {
        return TimeUnit.MILLISECONDS.toDays(calendarDateMillis(end) - calendarDateMillis(start));
    }

    private long calendarDateMillis(Calendar date) {
        // Compare local date fields in UTC so DST cannot shorten or lengthen a day.
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH));
        return utc.getTimeInMillis();
    }

    private void resetTime(Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    private boolean containsTask(List<Task> list, String title) {
        for (Task t : list) {
            if (t.getTitle().equals(title)) return true;
        }
        return false;
    }

}
