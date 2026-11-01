package com.areonedev.autotrack.presentation;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.areonedev.autotrack.business.AgendaService;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import com.areonedev.autotrack.R;
import com.areonedev.autotrack.objects.Lead;
import com.areonedev.autotrack.objects.Task;
import com.areonedev.autotrack.business.AccessLeads;
import com.areonedev.autotrack.business.AccessTasks;
import com.areonedev.autotrack.business.ScoringService;
import com.areonedev.autotrack.business.PriorityManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CalendarActivity extends AccountActivity {

    private RecyclerView rvWeek, rvGeneralTasks;
    private TextView tvMonthYear, tvAgendaHeader;
    private BottomNavigationView bottomNav;
    private ImageButton btnPrevWeek, btnNextWeek;
    private Calendar currentCal; // Tracks the currently viewed week strip

    private ScoringService scoringService;
    private PriorityManager priorityManager;
    private AgendaService agendaService;
    private AccessLeads accessLeads;
    private AccessTasks accessTasks;
    private Date selectedDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!com.areonedev.autotrack.auth.AuthRuntime.requireAccount(this)) return;
        setContentView(R.layout.activity_calendar);

        // Initialize Business Logic
        scoringService = new ScoringService();
        priorityManager = new PriorityManager(scoringService);
        agendaService = new AgendaService(scoringService, priorityManager);
        accessLeads = new AccessLeads();
        accessTasks = new AccessTasks();

        // Initialize Calendar State
        currentCal = Calendar.getInstance(); // Defaults to Today
        selectedDate = currentCal.getTime();

        initViews();
        setupBottomNav();
        loadWeekView();
        updateTaskPanels(selectedDate);
    }

    /**
     * Shifts the current calendar view by exactly 7 days.
     * This updates the 7-day strip and selects the same day in the new week.
     */
    private void shiftWeek(int days) {
        // Move the reference calendar forward or backward by 7
        currentCal.add(Calendar.DAY_OF_MONTH, days);

        // Refresh the 7-day strip UI
        loadWeekView();

        // Automatically select the corresponding day in the new week to refresh the agenda
        onDateSelected(currentCal.getTime());
    }

    /**
     * Triggered when a user clicks a specific day in the WeekAdapter.
     */
    public void onDateSelected(Date date) {
        this.selectedDate = date;
        // Refresh the 7-day strip to update the highlight/circle on the new date
        loadWeekView();
        // Refresh the task list for the new date
        updateTaskPanels(date);
    }

    /**
     * Fetches, scores, and sorts all leads for the selected day into one unified list.
     */
    private void updateTaskPanels(Date date) {
        // 1. Fetch all leads from the database
        List<Lead> allLeads = accessLeads.getAllLeads();

        // 2. Fetch ALL tasks from the database using the sequential method
        List<Task> allTasks = new ArrayList<>();
        String error = accessTasks.getTasks(allTasks);

        // Optional: Log error if the database retrieval failed
        if (error != null) {
            Log.e("CalendarActivity", "Error fetching tasks: " + error);
        }

        // 3. Use AgendaService to get the prioritized agenda for the selected date
        // Now allTasks contains every task for every lead, ensuring historical tasks appear
        List<Lead> agendaLeads = agendaService.getTodayAgenda(allLeads, allTasks, date);

        // 4. Bind to the unified RecyclerView
        View emptyView = findViewById(R.id.llEmptyState);

        // TaskAdapter will display the list.
        rvGeneralTasks.setAdapter(new TaskAdapter(agendaLeads, scoringService, emptyView, date));

        // 5. Update the Agenda Header text
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMM dd", Locale.getDefault());
        tvAgendaHeader.setText("Agenda for " + sdf.format(date));
    }

    private void initViews() {
        rvWeek = findViewById(R.id.rvWeekCalendar);
        tvMonthYear = findViewById(R.id.tvMonthYear);
        tvAgendaHeader = findViewById(R.id.tvAgendaHeader);
        bottomNav = findViewById(R.id.bottom_navigation);
        rvGeneralTasks = findViewById(R.id.rvGeneralTasks);

        // Navigation Buttons for the 7-day swipe logic
        btnPrevWeek = findViewById(R.id.btnPrevWeek);
        btnNextWeek = findViewById(R.id.btnNextWeek);

        if (btnPrevWeek != null) {
            btnPrevWeek.setOnClickListener(v -> shiftWeek(-7));
        }
        if (btnNextWeek != null) {
            btnNextWeek.setOnClickListener(v -> shiftWeek(7));
        }

        if(tvMonthYear!=null){
            tvMonthYear.setOnClickListener(v -> jumpToToday());
        }

        // Layout Managers
        rvWeek.setLayoutManager(new GridLayoutManager(this, 7));
        rvGeneralTasks.setLayoutManager(new LinearLayoutManager(this));
    }

    /**
     * Generates the 7 dates for the current week strip based on currentCal.
     */
    private void loadWeekView() {
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        tvMonthYear.setText(monthFormat.format(currentCal.getTime()));

        List<Date> days = new ArrayList<>();
        Calendar weekCal = (Calendar) currentCal.clone();
        weekCal.set(Calendar.DAY_OF_WEEK, weekCal.getFirstDayOfWeek());

        for (int i = 0; i < 7; i++) {
            days.add(weekCal.getTime());
            weekCal.add(Calendar.DAY_OF_MONTH, 1);
        }

        Date today = Calendar.getInstance().getTime();

        //Pass selectedDate (which is a Date object) to the updated constructor
        WeekAdapter adapter = new WeekAdapter(days, selectedDate, today,date -> onDateSelected(date));
        rvWeek.setAdapter(adapter);
    }

    private void setupBottomNav() {
        bottomNav.setSelectedItemId(R.id.nav_calendar);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_calendar) {
                return true;
            }
            Intent intent;
            if (id == R.id.nav_leads) {
                intent = new Intent(CalendarActivity.this, LeadsActivity.class);
            } else if (id == R.id.nav_notifications) {
                // ADDED: Logic to navigate to Notifications
                intent = new Intent(CalendarActivity.this, NotificationsActivity.class);
            } else {
                return false;
            }

            // Use REORDER_TO_FRONT to prevent freezing/re-initializing the database
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);

            // Optional: Use 0,0 transition for a "tab-like" feel
            overridePendingTransition(0, 0);
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh the agenda whenever you return to this screen (e.g., after a deletion)
        if (selectedDate != null) {
            // Use the existing method that fetches and sorts the leads
            updateTaskPanels(selectedDate);
        }
    }

    private void jumpToToday() {
        // 1. Reset the calendar instance to right now
        currentCal = Calendar.getInstance();

        // 2. Set the selected date to today
        selectedDate = currentCal.getTime();

        // 3. Refresh the Week Strip (this will now show the current week)
        loadWeekView();

        // 4. Refresh the task panels for today
        updateTaskPanels(selectedDate);
    }
}
