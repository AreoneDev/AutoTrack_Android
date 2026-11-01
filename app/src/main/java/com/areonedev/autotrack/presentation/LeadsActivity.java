package com.areonedev.autotrack.presentation;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.areonedev.autotrack.R;
import com.areonedev.autotrack.business.AccessLeads;
import com.areonedev.autotrack.business.AccessVehicles;
import com.areonedev.autotrack.objects.Lead;
import com.areonedev.autotrack.objects.Vehicle;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LeadsActivity extends AccountActivity {
    private static final String TAG = "LeadsActivity";

    private RecyclerView recyclerView;
    private BottomNavigationView bottomNav;
    private SearchView searchView;
    private View emptyStateView;
    private FloatingActionButton fab;
    private ImageButton btnFilterToggle;
    private Button btnApply, btnReset;
    private LinearLayout filterPanel;
    private Spinner spinnerStatusFilter, spinnerStageFilter, spinnerDivisionFilter;
    private AutoCompleteTextView actvYear, actvMake, actvModel;
    private AccessLeads accessLeads;
    private AccessVehicles accessVehicles;
    private LeadAdapter adapter;
    private boolean isFilterPanelExpanded = false;
    private List<Vehicle> allVehicles;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!com.areonedev.autotrack.auth.AuthRuntime.requireAccount(this)) return;
        setContentView(R.layout.activity_all_my_leads);
        findViewById(R.id.auth_sign_out).setOnClickListener(view -> signOutAccount());

        accessLeads = new AccessLeads();

        accessVehicles = new AccessVehicles();
        allVehicles = new ArrayList<>();
        accessVehicles.getVehicles(allVehicles);

        initViews();
        setupListeners();

        setupVehicleDropdowns();
        setupDropdownBehaviors();

        applyFilters();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerViewLeads);
        bottomNav = findViewById(R.id.bottom_navigation);
        fab = findViewById(R.id.fab_add_lead);
        searchView = findViewById(R.id.searchView);
        btnFilterToggle = findViewById(R.id.btnFilterToggle);
        filterPanel = findViewById(R.id.filterPanel);
        spinnerStatusFilter = findViewById(R.id.spinnerStatusFilter);
        spinnerStageFilter = findViewById(R.id.spinnerStageFilter);
        spinnerDivisionFilter = findViewById(R.id.spinnerDivisionFilter);
        actvYear = findViewById(R.id.actvYearFilter);
        actvMake = findViewById(R.id.actvMakeFilter);
        actvModel = findViewById(R.id.actvModelFilter);
        btnApply = findViewById(R.id.btnApplyFilters);
        btnReset = findViewById(R.id.btnResetFilters);
        emptyStateView = findViewById(R.id.empty_state_view);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        bottomNav.setSelectedItemId(R.id.nav_leads);


    }

    private void setupListeners() {
        // 1. Toggle Filter Panel
        btnFilterToggle.setOnClickListener(v -> {
            isFilterPanelExpanded = !isFilterPanelExpanded;
            filterPanel.setVisibility(isFilterPanelExpanded ? View.VISIBLE : View.GONE);
            if (isFilterPanelExpanded) {
                btnFilterToggle.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            } else {
                btnFilterToggle.setImageResource(R.drawable.ic_filter_funnel); // Switch back to funnel
            }
        });

        // 2. Apply Button (Triggers the database/list update)
        btnApply.setOnClickListener(v -> {
            applyFilters();
            filterPanel.setVisibility(View.GONE);
            isFilterPanelExpanded = false;
            btnFilterToggle.setImageResource(R.drawable.ic_filter_funnel);
        });

        // 3. Reset Button
        btnReset.setOnClickListener(v -> {
            spinnerStatusFilter.setSelection(0);
            spinnerStageFilter.setSelection(0);
            spinnerDivisionFilter.setSelection(0);
            actvYear.setText("Year", false);
            actvMake.setText("Make", false);
            actvModel.setText("Model", false);

            // Reset the dropdown lists to default
            setupVehicleDropdowns();
            searchView.setQuery("", false);
            applyFilters(); // Refresh to show all
        });

        // 4. Search View (Keep real-time for quick name/phone lookup)
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                applyFilters();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                applyFilters();
                return true;
            }
        });

        // 5. FAB & Navigation
        fab.setOnClickListener(v -> startActivity(new Intent(this, LeadsCreationActivity.class)));

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_leads) {
                recyclerView.smoothScrollToPosition(0);
                return true;
            }

            Intent intent;
            if (id == R.id.nav_calendar) intent = new Intent(this, CalendarActivity.class);
            else if (id == R.id.nav_notifications)
                intent = new Intent(this, NotificationsActivity.class);
            else return false;

            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            overridePendingTransition(0, 0);
            return true;
        });
    }

    /**
     * Combined Method: Handles Search, Filters, Database Query, and UI Updates
     */
    private void applyFilters() {
        // 1. Get current values from all UI components
        String query = (searchView != null) ? searchView.getQuery().toString().trim() : "";

        // Use a helper to get Spinner text safely
        String status = (spinnerStatusFilter.getSelectedItem() != null) ?
                spinnerStatusFilter.getSelectedItem().toString() : "All Status";
        String stage = (spinnerStageFilter.getSelectedItem() != null) ?
                spinnerStageFilter.getSelectedItem().toString() : "All Stages";
        String division = (spinnerDivisionFilter.getSelectedItem() != null) ?
                spinnerDivisionFilter.getSelectedItem().toString() : "All Divisions";

        // Get specific vehicle selections
        String year = actvYear.getText().toString().trim();
        String make = actvMake.getText().toString().trim();
        String model = actvModel.getText().toString().trim();

        // 2. Fetch filtered results from Business Layer (Option B)
        // Ensure your AccessLeads.getLeadsFiltered signature matches these 7 parameters
        List<Lead> filteredList = accessLeads.getLeadsFiltered(query, status, stage, division, year, make, model);
        if (filteredList == null) filteredList = new ArrayList<>();

        // 3. Update or Initialize Adapter
        if (adapter == null) {
            adapter = new LeadAdapter(new ArrayList<>(filteredList), lead -> {
                Intent intent = new Intent(LeadsActivity.this, LeadDetailsActivity.class);
                intent.putExtra("SELECTED_LEAD", lead);
                startActivity(intent);
            });
            recyclerView.setAdapter(adapter);
        } else {
            adapter.updateList(filteredList);
        }

        // 4. Toggle Empty State
        boolean isEmpty = filteredList.isEmpty();
        if (emptyStateView != null) {
            emptyStateView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
    }

    private void setupVehicleDropdowns() {
        if (allVehicles == null) return;

        // 1. Initial Load: Populate all Years and all Makes from the master list
        List<String> allYears = new ArrayList<>();
        List<String> allMakes = new ArrayList<>();
        for (Vehicle v : allVehicles) {
            if (!allYears.contains(v.getYear())) allYears.add(v.getYear());
            if (!allMakes.contains(v.getMake())) allMakes.add(v.getMake());
        }

        // Sort them so they look nice in the dropdown
        Collections.sort(allYears, Collections.reverseOrder());
        Collections.sort(allMakes);

        updateAdapter(actvYear, allYears);
        updateAdapter(actvMake, allMakes);

        // 2. When Year is selected -> Filter Makes available for that year
        actvYear.setOnItemClickListener((parent, view, position, id) -> {
            String selectedYear = (String) parent.getItemAtPosition(position);

            // Reset dependent fields (using "" allows the hint to show or typing to start fresh)
            actvMake.setText("", false);
            actvModel.setText("", false);

            List<String> filteredMakes = new ArrayList<>();
            for (Vehicle v : allVehicles) {
                if (v.getYear().equals(selectedYear) && !filteredMakes.contains(v.getMake())) {
                    filteredMakes.add(v.getMake());
                }
            }
            Collections.sort(filteredMakes);
            updateAdapter(actvMake, filteredMakes);

            actvMake.showDropDown();
            applyFilters(); // Refresh the lead list
        });

        // 3. When Make is selected -> Filter Models based on Year + Make
        actvMake.setOnItemClickListener((parent, view, position, id) -> {
            String selectedYear = actvYear.getText().toString();
            String selectedMake = (String) parent.getItemAtPosition(position);

            actvModel.setText("", false);

            List<String> filteredModels = new ArrayList<>();
            for (Vehicle v : allVehicles) {
                // Logic: If year is empty or "Year", match by make only. Otherwise match both.
                boolean yearMatch = selectedYear.isEmpty() || selectedYear.equals("Year") || v.getYear().equals(selectedYear);
                if (yearMatch && v.getMake().equals(selectedMake) && !filteredModels.contains(v.getModel())) {
                    filteredModels.add(v.getModel());
                }
            }
            Collections.sort(filteredModels);
            updateAdapter(actvModel, filteredModels);

            actvModel.showDropDown();
            applyFilters(); // Refresh the lead list
        });

        // 4. When Model is selected -> Just trigger filter
        actvModel.setOnItemClickListener((parent, view, position, id) -> {
            applyFilters();
        });
    }

    private void setupDropdownBehaviors() {
        View.OnFocusChangeListener focusListener = (v, hasFocus) -> {
            if (hasFocus && v instanceof AutoCompleteTextView) {
                ((AutoCompleteTextView) v).showDropDown();
            }
        };

        actvYear.setOnFocusChangeListener(focusListener);
        actvMake.setOnFocusChangeListener(focusListener);
        actvModel.setOnFocusChangeListener(focusListener);

        actvYear.setOnClickListener(v -> actvYear.showDropDown());
        actvMake.setOnClickListener(v -> actvMake.showDropDown());
        actvModel.setOnClickListener(v -> actvModel.showDropDown());
    }

    // Helper to refresh adapters (Same as your LeadDetailsActivity)
    private void updateAdapter(AutoCompleteTextView view, List<String> data) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, data);
        view.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!com.areonedev.autotrack.auth.AuthRuntime.hasAccount()) return;
        applyFilters(); // Refresh list when returning to activity
    }
}
