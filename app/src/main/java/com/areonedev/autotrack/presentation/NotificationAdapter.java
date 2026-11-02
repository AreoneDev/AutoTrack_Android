package com.areonedev.autotrack.presentation;

import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.areonedev.autotrack.R;
import com.areonedev.autotrack.objects.Notification;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

    private List<Notification> notifications;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("dd/MMM/yyyy\nhh:mm a", Locale.getDefault());

    public NotificationAdapter(List<Notification> notifications) {
        this.notifications = notifications;
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification_card, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        Notification note = notifications.get(position);
        String fullTitle = note.getTitle(); // e.g., "Incoming Call from Pengyu Chen"

        // 1. Set the Time
        holder.tvTime.setText(timeFormat.format(note.getDate()));

        // 2. Logic to determine icon and split title based on keywords
        String lowerTitle = fullTitle.toLowerCase();

        if (lowerTitle.contains("call")) {
            holder.tvTitle.setText(R.string.incoming_call);
            holder.ivIcon.setImageResource(android.R.drawable.ic_menu_call);
            holder.ivIcon.setColorFilter(Color.parseColor("#F44336")); // Red
        } else if (lowerTitle.contains("sms") || lowerTitle.contains("message")) {
            holder.tvTitle.setText(R.string.new_message);
            holder.ivIcon.setImageResource(android.R.drawable.sym_action_chat);
            holder.ivIcon.setColorFilter(Color.parseColor("#4CAF50")); // Green
        } else if (lowerTitle.contains("email")) {
            holder.tvTitle.setText(R.string.new_email);
            holder.ivIcon.setImageResource(android.R.drawable.ic_dialog_email);
            holder.ivIcon.setColorFilter(Color.parseColor("#2196F3")); // Blue
        } else {
            holder.tvTitle.setText(R.string.notification);
            holder.ivIcon.setImageResource(android.R.drawable.ic_dialog_info);
            holder.ivIcon.setColorFilter(Color.GRAY);
        }

        // 3. Extract Lead Name from Title (assuming format "Type from Name")
        if (fullTitle.contains("from ")) {
            String leadName = fullTitle.substring(fullTitle.indexOf("from ") + 5);
            holder.tvLeadName.setText(holder.itemView.getContext().getString(R.string.from_name, leadName));
        } else {
            holder.tvLeadName.setText(fullTitle);
        }

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), LeadDetailsActivity.class);
            // Pass the leadId stored in the Notification object
            intent.putExtra("LEAD_ID", note.getLeadID());
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    static class NotificationViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvTitle, tvLeadName, tvTime;

        public NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivNotificationIcon);
            tvTitle = itemView.findViewById(R.id.tvNotificationTitle);
            tvLeadName = itemView.findViewById(R.id.tvNotificationLeadName);
            tvTime = itemView.findViewById(R.id.tvNotificationTime);
        }
    }
}