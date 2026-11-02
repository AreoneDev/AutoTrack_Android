package com.areonedev.autotrack.presentation;

import com.areonedev.autotrack.R;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.areonedev.autotrack.business.ScoringService;
import com.areonedev.autotrack.objects.Lead;

import java.util.Date;
import java.util.Calendar;
import java.util.List;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.ViewHolder> {
    private List<Lead> leads;
    private ScoringService scoringService;
    private Date selectedDate;
    private final int THRESHOLD = 100;
    private View emptyView;

    public TaskAdapter(List<Lead> leads,ScoringService scoringService,View emptyView,Date selectedDate) {
        this.leads = leads;
        this.scoringService = scoringService;
        this.emptyView = emptyView;
        this.selectedDate = selectedDate;
        toggleEmptyState(); // Check visibility immediately on creation
    }

    private void toggleEmptyState() {
        if (emptyView != null) {
            if (getItemCount() == 0) {
                emptyView.setVisibility(View.VISIBLE);
            } else {
                emptyView.setVisibility(View.GONE);
            }
        }
    }

    public void updateData(List<Lead> newLeads,Date newSelectedDate) {
        this.leads = newLeads;
        this.selectedDate = newSelectedDate;
        notifyDataSetChanged();
        toggleEmptyState();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Using a simple list item layout - you can create a custom one if needed
        View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_2, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Lead lead = leads.get(position);

        // Calculate the Score and Mission for this specific lead
        double score = scoringService.calculateScore(lead);
        String missionDesc = scoringService.getScientificMission(lead,selectedDate);

        // Fallback if the mission returns null (though your filter should prevent this)
        missionDesc = MissionText.display(holder.itemView.getContext(), missionDesc, lead.getLeadStage(), (int) score);

        String missionTitle = holder.itemView.getContext().getString(MissionStage.resource(lead.getLeadStage()));
        // Line 1: Name + Mission Title (e.g., "John Doe | 🏎️ Test Drive")
        int stagePosition = FilterValues.stagePosition(lead.getLeadStage());
        String[] stageLabels = holder.itemView.getResources().getStringArray(R.array.filter_stages);
        String stageLabel = stagePosition >= 0 ? stageLabels[stagePosition] : lead.getLeadStage();
        holder.tvName.setText(String.format("%s %s | %s", lead.getLeadFirstName(), lead.getLeadLastName(), stageLabel));

        // Line 2: Mission Description + Stage (Scientific Context)
        String performanceText = holder.itemView.getContext().getString(R.string.mission_summary, missionDesc, missionTitle);
        holder.tvStage.setText(performanceText);

        // 1. Get "Today" at midnight
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        // 2. Get "Selected Date" at midnight
        Calendar selectedCal = Calendar.getInstance();
        selectedCal.setTime(selectedDate);
        selectedCal.set(Calendar.HOUR_OF_DAY, 0);
        selectedCal.set(Calendar.MINUTE, 0);
        selectedCal.set(Calendar.SECOND, 0);
        selectedCal.set(Calendar.MILLISECOND, 0);

        // 3. Determine if this is a past task
        boolean isPastTask = selectedCal.before(today);
        // Logic for Completed vs. Priority
        boolean isCompleted = "CLOSED".equalsIgnoreCase(lead.getLeadStage()); // Example condition

        if (isCompleted||isPastTask) {
            holder.tvName.setTextColor(com.google.android.material.color.MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurfaceVariant));
            holder.tvStage.setTextColor(Color.LTGRAY);
            holder.itemView.setAlpha(0.6f); // Make the whole row look faded
            holder.tvName.setTypeface(null, Typeface.NORMAL);
        } else {
            holder.itemView.setAlpha(1.0f);
            if (score >= THRESHOLD) {
                holder.tvName.setTextColor(com.google.android.material.color.MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorError));
                holder.tvName.setTypeface(null, Typeface.BOLD);
            } else {
                holder.tvName.setTextColor(com.google.android.material.color.MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurface));
                holder.tvName.setTypeface(null, Typeface.NORMAL);
            }
            holder.tvStage.setTextColor(com.google.android.material.color.MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurfaceVariant));
        }

        holder.itemView.setOnClickListener(v -> {
            // Import android.content.Intent
            Intent intent = new Intent(v.getContext(), LeadDetailsActivity.class);
            // Ensure Lead implements Serializable or Parcelable
            intent.putExtra("SELECTED_LEAD", lead);
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return (leads != null) ? leads.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvStage;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            // Using standard Android IDs for simple_list_item_2
            tvName = itemView.findViewById(android.R.id.text1);
            tvStage = itemView.findViewById(android.R.id.text2);
        }
    }
}
