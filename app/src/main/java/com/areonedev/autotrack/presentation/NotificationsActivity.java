package com.areonedev.autotrack.presentation;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.areonedev.autotrack.R;
import com.areonedev.autotrack.business.AccessLeads;
import com.areonedev.autotrack.business.AccessNotifications;
import com.areonedev.autotrack.objects.Notification;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NotificationsActivity extends AccountActivity {
    private static final String TAG = "NotificationActivity";
    private RecyclerView rvNotifications;
    private NotificationAdapter adapter;
    private List<Notification> notificationList;
    private BottomNavigationView bottomNavigationView;
    private AccessNotifications accessNotifications;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!com.areonedev.autotrack.auth.AuthRuntime.requireAccount(this)) return;
        setContentView(R.layout.activity_notifications);

        initViews();

        // Initialize data access
        accessNotifications = new AccessNotifications();
        notificationList = new ArrayList<>();

        // Initialize adapter once with empty list to prevent "No adapter attached" error
        adapter = new NotificationAdapter(notificationList);
        rvNotifications.setAdapter(adapter);

        setupNavigation();
    }

    private void initViews() {
        rvNotifications = findViewById(R.id.rvNotifications);
        bottomNavigationView = findViewById(R.id.bottom_navigation);

        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
    }

    private void loadNotifications() {
        // Use a background thread to prevent the UI from freezing
        new Thread(() -> {
            List<Notification> tempList = new ArrayList<>();
            String error = accessNotifications.getNotifications(tempList);

            // Switch back to UI thread to update the view
            runOnUiThread(() -> {
                if (error == null) {
                    notificationList.clear();
                    notificationList.addAll(tempList);

                    // Sort: Newest at the top
                    Collections.sort(notificationList, (n1, n2) -> n2.getDate().compareTo(n1.getDate()));

                    adapter.notifyDataSetChanged();
                } else {
                    Log.e(TAG, "Failed to load notifications: " + error);
                    Toast.makeText(this, getString(R.string.error_detail, error), Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void setupNavigation() {
        setupAccountNavigation(R.id.nav_notifications);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!com.areonedev.autotrack.auth.AuthRuntime.hasAccount()) return;
        // Refresh list when returning to this page
        loadNotifications();
    }
}
