package com.areonedev.autotrack.presentation;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.areonedev.autotrack.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WeekAdapter extends RecyclerView.Adapter<WeekAdapter.ViewHolder> {
    private final OnDateClickListener listener;
    private final List<Date> days;
   // private final Calendar currentCal; // Added this field
    private final Date selectedDate;
    private final Date todayDate;
    public interface OnDateClickListener {
        void onDateClick(Date date);
    }

    // Update constructor to accept two parameters
    public WeekAdapter(List<Date> days, Date selectedDate, Date todayDate, OnDateClickListener listener) {
        this.days = days;
        this.selectedDate = selectedDate;
        this.todayDate = todayDate;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_day, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Date date = days.get(position);
        SimpleDateFormat dayNameFormat = new SimpleDateFormat("EEE", Locale.getDefault());
        SimpleDateFormat dayNumFormat = new SimpleDateFormat("d", Locale.getDefault());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());

        String dayName = dayNameFormat.format(date);
        holder.tvDayName.setContentDescription(dayName);
        android.content.res.Configuration configuration = holder.itemView.getResources().getConfiguration();
        if ((configuration.screenWidthDp < 360 || configuration.fontScale >= 1.3f) && !dayName.isEmpty()) {
            int count = dayName.codePointCount(0, dayName.length());
            int index = Locale.getDefault().getLanguage().equals("zh") ? count - 1 : 0;
            int start = dayName.offsetByCodePoints(0, index);
            dayName = dayName.substring(start, dayName.offsetByCodePoints(start, 1));
        }
        holder.tvDayName.setText(dayName);
        holder.tvDayNumber.setText(dayNumFormat.format(date));

        // 1. Get strings for comparison
        String dateStr = sdf.format(date);
        String selectedStr = (selectedDate != null) ? sdf.format(selectedDate) : "";

        // Use the todayDate field passed from the constructor
        String todayStr = (todayDate != null) ? sdf.format(todayDate) : "";

        // 2. Priority Highlighting Logic
        if (dateStr.equals(selectedStr)) {
            // HIGHLIGHT: User's current selection (Blue Circle)
            holder.tvDayNumber.setBackgroundResource(R.drawable.circle_background_light_blue);
            holder.tvDayNumber.setBackgroundTintList(android.content.res.ColorStateList.valueOf(com.google.android.material.color.MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorPrimary)));
            holder.tvDayNumber.setTextColor(com.google.android.material.color.MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnPrimary));
            holder.tvDayName.setTextColor(com.google.android.material.color.MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurface));
        } else if (dateStr.equals(todayStr)) {
            // HIGHLIGHT: Today's date (Red text)
            holder.tvDayNumber.setBackground(null);
            holder.tvDayNumber.setTextColor(com.google.android.material.color.MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorError));
            holder.tvDayName.setTextColor(com.google.android.material.color.MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorError));
        } else {
            // DEFAULT: Normal day
            holder.tvDayNumber.setBackground(null);
            holder.tvDayNumber.setTextColor(com.google.android.material.color.MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurface));
            holder.tvDayName.setTextColor(com.google.android.material.color.MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurfaceVariant));
        }

        holder.itemView.setOnClickListener(v -> listener.onDateClick(date));
    }

    @Override
    public int getItemCount() {
        return days.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDayName, tvDayNumber;
        ViewHolder(View itemView) {
            super(itemView);
            tvDayName = itemView.findViewById(R.id.tvDayName);
            tvDayNumber = itemView.findViewById(R.id.tvDayNumber);
        }
    }
}
