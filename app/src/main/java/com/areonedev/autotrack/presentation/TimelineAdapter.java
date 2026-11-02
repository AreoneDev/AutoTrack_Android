package com.areonedev.autotrack.presentation; // Ensure this matches your package structure

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.areonedev.autotrack.R;
import com.areonedev.autotrack.business.ScoringService;
import com.areonedev.autotrack.objects.Task;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class TimelineAdapter extends RecyclerView.Adapter<TimelineAdapter.ViewHolder> {
    private List<Task> tasks;
    private SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    private OnTaskStatusChangedListener statusChangedListener;

    // 1. ADD THIS INTERFACE
    public interface OnTaskClickListener {
        void onTaskClick(Task task);
    }

    // 2. ADD THIS VARIABLE
    private OnTaskClickListener onTaskClickListener;

    // 3. ADD THIS SETTER METHOD
    public void setOnTaskClickListener(OnTaskClickListener listener) {
        this.onTaskClickListener = listener;
    }

    public TimelineAdapter(List<Task> tasks) {
        this.tasks = tasks;
        sortTasksDescending();
    }

    private void sortTasksDescending() {
        if (tasks != null && tasks.size() > 1) {
            tasks.sort((t1, t2) -> {
                if (t1.getDate() == null || t2.getDate() == null) return 0;
                // Descending order: t2 compared to t1
                return t2.getDate().compareTo(t1.getDate());
            });
        }
    }

    public void updateTasks(List<Task> newTasks) {
        this.tasks = newTasks;
        sortTasksDescending();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Ensure you have a layout file named item_timeline.xml
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_timeline, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Task task = tasks.get(position);

        if (task == null || task.getDate() == null) return;

        // 1. Update Date Format to show Time (HH:mm)
        SimpleDateFormat dateTimeFormat = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());

        String displayTitle = task.getTitle();
        int generated = MissionText.resource(displayTitle);
        if (generated != 0 && generated != R.string.mission_high && generated != R.string.mission_standard) {
            displayTitle = holder.itemView.getContext().getString(generated);
        } else {
            String[] canonical = {"Appointment", "Test Drive", "Follow-up", "Other"};
            String[] translated = holder.itemView.getResources().getStringArray(R.array.task_type_labels);
            for (int i = 0; i < canonical.length; i++) {
                if (displayTitle.equals(canonical[i]) || displayTitle.startsWith(canonical[i] + ":")) {
                    displayTitle = translated[i] + displayTitle.substring(canonical[i].length());
                    break;
                }
            }
        }
        holder.tvTitle.setText(displayTitle);
        holder.tvDate.setText(dateTimeFormat.format(task.getDate()));
        holder.ivCheck.setContentDescription(holder.itemView.getContext().getString(
                task.isCompleted() ? R.string.a11y_task_incomplete : R.string.a11y_task_complete, displayTitle));
        holder.ivCheck.setFocusable(true);

        // 2. Identify High-Intent Tasks (Appointments/Test Drives)
        String titleLower = task.getTitle().toLowerCase();
        boolean isHighIntent = titleLower.contains("appointment") || titleLower.contains("test drive");

        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        Calendar taskDate = Calendar.getInstance();
        taskDate.setTime(task.getDate());

        // 3. Apply Visual States
        if (task.isCompleted()) {
            // COMPLETED: Gray out
            holder.tvTitle.setTextColor(com.google.android.material.color.MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurfaceVariant));
            holder.tvDate.setTextColor(com.google.android.material.color.MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurfaceVariant));
            holder.ivCheck.setImageResource(R.drawable.ic_check_circle_gray);
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
        } else if (taskDate.before(today)) {
            // OVERDUE: Red
            holder.tvTitle.setTextColor(com.google.android.material.color.MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnErrorContainer));
            holder.tvTitle.setText(holder.itemView.getContext().getString(R.string.overdue, displayTitle));
            holder.tvDate.setTextColor(com.google.android.material.color.MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnErrorContainer));
            holder.ivCheck.setImageResource(R.drawable.ic_radio_button_unchecked);
            holder.itemView.setBackgroundColor(com.google.android.material.color.MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorErrorContainer));
        } else {
            // PENDING: Standard or High-Intent Highlight
            holder.tvTitle.setTextColor(com.google.android.material.color.MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurface));
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);

            if (isHighIntent) {
                // Highlight Appointments/Test Drives in Blue or Purple to show they are special
                holder.tvTitle.setTextColor(com.google.android.material.color.MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorPrimary));
                holder.tvDate.setTextColor(com.google.android.material.color.MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorPrimary));
            } else if (task.getTitle().contains("URGENT")) {
                holder.tvDate.setTextColor(com.google.android.material.color.MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorError));
            } else {
                holder.tvDate.setTextColor(com.google.android.material.color.MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorPrimary)); // Green
            }

            holder.ivCheck.setImageResource(R.drawable.ic_radio_button_unchecked);
            holder.ivCheck.setColorFilter(com.google.android.material.color.MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorPrimary));
        }

        // 4. CLICK LOGIC: Toggle completion and notify activity to recalculate score
        // ACTION A: Clicking the Checkbox Icon toggles completion status
        holder.ivCheck.setOnClickListener(v -> {
            task.setCompleted(!task.isCompleted());
            notifyItemChanged(position);

            if (onTaskStatusChangedListener != null) {
                onTaskStatusChangedListener.onTaskStatusChanged(task);
            }
        });

        // ACTION B: Clicking the whole card opens the Edit/Delete options
        holder.itemView.setOnClickListener(v -> {
            if (onTaskClickListener != null) {
                onTaskClickListener.onTaskClick(task);
            }
        });
    }

    @Override
    public int getItemCount() {
        return tasks != null ? tasks.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDate;
        ImageView ivCheck;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            // Ensure these IDs match your item_timeline.xml
            tvTitle = itemView.findViewById(R.id.tvTimelineTitle);
            tvDate = itemView.findViewById(R.id.tvTimelineDate);
            ivCheck = itemView.findViewById(R.id.ivTimelineStatus);
        }
    }

    // Add this interface to handle database updates from the Adapter
    public interface OnTaskStatusChangedListener {
        void onTaskStatusChanged(Task task);
    }

    private OnTaskStatusChangedListener onTaskStatusChangedListener;

    public void setOnTaskStatusChangedListener(OnTaskStatusChangedListener listener) {
        this.onTaskStatusChangedListener = listener;
    }
}
