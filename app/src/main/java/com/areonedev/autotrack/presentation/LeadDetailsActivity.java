package com.areonedev.autotrack.presentation;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.net.Uri;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.PopupMenu;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.areonedev.autotrack.R;
import com.areonedev.autotrack.business.AccessLeads;
import com.areonedev.autotrack.business.AccessTasks;
import com.areonedev.autotrack.business.AccessVehicles;
import com.areonedev.autotrack.business.ScoringService;
import com.areonedev.autotrack.objects.Lead;
import com.areonedev.autotrack.objects.Vehicle;
import com.areonedev.autotrack.objects.Task;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Calendar;
import java.util.ArrayList;

public class LeadDetailsActivity extends AccountActivity {

    // Layout Containers
    private View layoutViewMode, layoutEditMode;
    private LinearLayout llStatusToggle;
    private RecyclerView rvTimeline;

    // View Mode Components
    private TextView tvViewName, tvViewAddress, tvViewVehicle, tvStatusLabel, tvViewNotes, tvDetDate, tvDetUpdatedDate;
    private ImageView ivPhone, ivEmail, ivSms;
    private AutoCompleteTextView actvYear, actvMake, actvModel, actvTrim;
    private ImageButton btnAddTask;
    private View viewStatusDot;

    // Edit Mode Components
    private EditText etFirstName, etLastName, etPhone, etEmail, etNotes;
    private Button btnUpdate, btnDelete;
    private ScoringService scoringService;
    private Lead currentLead;
    private AccessLeads accessLeads;
    private AccessTasks accessTasks;
    private AccessVehicles accessVehicles;
    private List<Vehicle> allVehicles;
    private boolean isEditMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!com.areonedev.autotrack.auth.AuthRuntime.requireAccount(this)) return;
        setContentView(R.layout.activity_lead_details);

        scoringService = new ScoringService();

        accessLeads = new AccessLeads();
        currentLead = (Lead) getIntent().getSerializableExtra("SELECTED_LEAD");

        if (currentLead == null) {
            long leadId = getIntent().getLongExtra("LEAD_ID", -1);
            if (leadId != -1) {
                // Fetch the lead from your database using the ID
                currentLead = accessLeads.getRandom(leadId);
            }
        }

        /*** Load Tasks ***/
        accessTasks = new AccessTasks();

        setupToolbar();
        initViews();
        setupContactActions();
        btnAddTask.setOnClickListener(v -> showAddTaskDialog());
        if (currentLead != null) {
            refreshUI();
        } else {
            Toast.makeText(this, R.string.lead_not_found, Toast.LENGTH_SHORT).show();
            finish(); // Close activity if no data
        }

        btnUpdate.setOnClickListener(v -> handleUpdate());
        btnDelete.setOnClickListener(v -> showDeleteConfirmation());

        /*** Load Vehicles ***/
        accessVehicles = new AccessVehicles();
        allVehicles = new ArrayList<>();
        accessVehicles.getVehicles(allVehicles);

        setupVehicleDropdowns();
        setupDropdownBehaviors();
    }

    private void setupToolbar() {
        // 1. Find the toolbar from your XML
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);

        // 2. Set it as the SupportActionBar
        setSupportActionBar(toolbar);

        // 3. Now configure the back button and title
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.lead_details);
        }
    }

    private void initViews() {
        // Containers
        layoutViewMode = findViewById(R.id.layoutViewMode);
        layoutEditMode = findViewById(R.id.layoutEditMode);
        llStatusToggle = findViewById(R.id.llStatusToggle);

        // View Mode TextViews
        viewStatusDot = findViewById(R.id.viewStatusDot);
        tvStatusLabel = findViewById(R.id.tvStatusLabel);
        tvViewName = findViewById(R.id.tvViewName);
        tvViewAddress = findViewById(R.id.tvViewAddress);
        tvViewVehicle = findViewById(R.id.tvViewVehicle);
        tvViewNotes = findViewById(R.id.tvViewNotes);
        tvDetDate = findViewById(R.id.tvDetDate);
        tvDetUpdatedDate = findViewById(R.id.tvDetUpdatedDate);

        ivPhone = findViewById(R.id.ivPhoneIcon);
        ivEmail = findViewById(R.id.ivEmailIcon);
        ivSms = findViewById(R.id.ivSmsIcon);

        rvTimeline = findViewById(R.id.rvLeadTimeline);
        btnAddTask = findViewById(R.id.btnAddTask);

        // Edit Mode EditTexts
        etFirstName = findViewById(R.id.etDetFirstName);
        etLastName = findViewById(R.id.etDetLastName);
        etPhone = findViewById(R.id.etDetPhone);
        etEmail = findViewById(R.id.etDetEmail);

        actvYear = findViewById(R.id.actvYear);
        actvMake = findViewById(R.id.actvMake);
        actvModel = findViewById(R.id.actvModel);
        actvTrim = findViewById(R.id.actvTrim);
//        etMake = findViewById(R.id.etDetMake);
//        etModel = findViewById(R.id.etDetModel);
//        etYear = findViewById(R.id.etDetYear);
//        etTrim = findViewById(R.id.etDetTrim);
        etNotes = findViewById(R.id.etDetNotes);

        // Buttons
        btnUpdate = findViewById(R.id.btnUpdateLead);
        btnDelete = findViewById(R.id.btnDeleteLead);
    }

    private void setupContactActions() {
        // 1. Status Toggle (Active/Lost)
        llStatusToggle.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, llStatusToggle);
            String[] statusLabels = getResources().getStringArray(R.array.filter_status);
            popup.getMenu().add(0, 1, 0, statusLabels[1]);
            popup.getMenu().add(0, 2, 1, statusLabels[2]);

            popup.setOnMenuItemClickListener(item -> {
                String selectedTitle = item.getTitle().toString();
                boolean isActive = item.getItemId() == 1;

                currentLead.setLeadStatus(isActive);

                // Save immediately to DB
                String result = accessLeads.updateLead(currentLead);
                if (result == null) {
                    refreshUI(); // Update the dot color and text label
                    Toast.makeText(this, getString(R.string.status_updated, selectedTitle), Toast.LENGTH_SHORT).show();
                }
                return true;
            });
            popup.show();
        });

        // 1. Phone Popup + Dial
        ivPhone.setOnClickListener(v -> {
            if (currentLead == null || currentLead.getLeadPhoneNumber() == null) return;

            new AlertDialog.Builder(this)
                    .setTitle(R.string.call_lead)
                    .setMessage(getString(R.string.call_confirm, currentLead.getLeadFirstName(), currentLead.getLeadPhoneNumber()))
                    .setPositiveButton(R.string.call, (dialog, which) -> {
                        Intent intent = new Intent(Intent.ACTION_DIAL);
                        intent.setData(Uri.parse("tel:" + currentLead.getLeadPhoneNumber()));
                        startActivity(intent);
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });

        // 2. Email Board
        ivEmail.setOnClickListener(v -> {
            if (currentLead == null || currentLead.getLeadEmail() == null) return;

            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:" + currentLead.getLeadEmail()));
            startActivity(Intent.createChooser(intent, "Send Email"));
        });

        // 3. SMS Chat Window
        ivSms.setOnClickListener(v -> {
            if (currentLead == null || currentLead.getLeadPhoneNumber() == null) return;

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("sms:" + currentLead.getLeadPhoneNumber()));
            startActivity(intent);
        });
    }

    private void setupVehicleDropdowns() {
        List<String> allYears = new ArrayList<>();
        List<String> allMakes = new ArrayList<>();
        for (Vehicle v : allVehicles) {
            if (!allYears.contains(v.getYear())) allYears.add(v.getYear());
            if (!allMakes.contains(v.getMake())) allMakes.add(v.getMake());
        }
        updateAdapter(actvYear, allYears);
        updateAdapter(actvMake, allMakes);

        // 2. When Year is selected -> Filter Makes available for that year
        actvYear.setOnItemClickListener((parent, view, position, id) -> {
            String selectedYear = (String) parent.getItemAtPosition(position);

            // Reset dependent fields
            actvMake.setText("");
            actvModel.setText("");
            actvTrim.setText("");

            List<String> filteredMakes = new ArrayList<>();
            for (Vehicle v : allVehicles) {
                if (v.getYear().equals(selectedYear) && !filteredMakes.contains(v.getMake())) {
                    filteredMakes.add(v.getMake());
                }
            }
            updateAdapter(actvMake, filteredMakes);
            // Automatically show the next dropdown
            actvMake.showDropDown();
        });

        // 3. When Make is selected -> Filter Models based on Year + Make
        actvMake.setOnItemClickListener((parent, view, position, id) -> {
            String selectedYear = actvYear.getText().toString();
            String selectedMake = (String) parent.getItemAtPosition(position);

            actvModel.setText("");
            actvTrim.setText("");

            List<String> filteredModels = new ArrayList<>();
            for (Vehicle v : allVehicles) {
                // If year is empty, filter by make only. If year is selected, filter by both.
                boolean yearMatch = selectedYear.isEmpty() || v.getYear().equals(selectedYear);
                if (yearMatch && v.getMake().equals(selectedMake) && !filteredModels.contains(v.getModel())) {
                    filteredModels.add(v.getModel());
                }
            }
            updateAdapter(actvModel, filteredModels);
            actvModel.showDropDown();
        });

        // 4. When Model is selected -> Filter Trims based on Year + Make + Model
        actvModel.setOnItemClickListener((parent, view, position, id) -> {
            String selectedYear = actvYear.getText().toString();
            String selectedMake = actvMake.getText().toString();
            String selectedModel = (String) parent.getItemAtPosition(position);

            actvTrim.setText("");

            List<String> filteredTrims = new ArrayList<>();
            for (Vehicle v : allVehicles) {
                boolean yearMatch = selectedYear.isEmpty() || v.getYear().equals(selectedYear);
                if (yearMatch && v.getMake().equals(selectedMake) && v.getModel().equals(selectedModel)) {
                    filteredTrims.add(v.getTrim());
                }
            }
            updateAdapter(actvTrim, filteredTrims);
            actvTrim.showDropDown();
        });

        // 5. Final Selection: Update the currentLead object
        actvTrim.setOnItemClickListener((parent, view, position, id) -> {
            String sYear = actvYear.getText().toString();
            String sMake = actvMake.getText().toString();
            String sModel = actvModel.getText().toString();
            String sTrim = (String) parent.getItemAtPosition(position);

            for (Vehicle v : allVehicles) {
                if (v.getYear().equals(sYear) && v.getMake().equals(sMake) &&
                        v.getModel().equals(sModel) && v.getTrim().equals(sTrim)) {
                    currentLead.setLeadVehicleInterest(v);
                    break;
                }
            }
        });
    }

    private void setupDropdownBehaviors() {
        // Create a shared listener for focus changes
        View.OnFocusChangeListener focusListener = (v, hasFocus) -> {
            if (hasFocus && v instanceof AutoCompleteTextView) {
                ((AutoCompleteTextView) v).showDropDown();
            }
        };

        // Apply focus listener to all 4 dropdowns
        actvYear.setOnFocusChangeListener(focusListener);
        actvMake.setOnFocusChangeListener(focusListener);
        actvModel.setOnFocusChangeListener(focusListener);
        actvTrim.setOnFocusChangeListener(focusListener);

        // Apply click listener so it re-opens if they click while already focused
        actvYear.setOnClickListener(v -> actvYear.showDropDown());
        actvMake.setOnClickListener(v -> actvMake.showDropDown());
        actvModel.setOnClickListener(v -> actvModel.showDropDown());
        actvTrim.setOnClickListener(v -> actvTrim.showDropDown());
    }

    // Helper to refresh adapters
    private void updateAdapter(AutoCompleteTextView view, List<String> data) {
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, data);
        view.setAdapter(adapter);
    }

    private void refreshUI() {
        if (currentLead == null) return;

        setupTimeline();

        // 3. Populate Contact Info
        tvViewName.setText(currentLead.getLeadFirstName() + " " + currentLead.getLeadLastName());
        //tvViewPhone.setText(currentLead.getLeadPhoneNumber());
        //tvViewEmail.setText(currentLead.getLeadEmail());

        String address = currentLead.getLeadAddress() + "\n" +
                currentLead.getLeadCity() + ", " +
                currentLead.getLeadProvince() + ", " +
                currentLead.getLeadPostalCode();
        tvViewAddress.setText(address);

        // Update Status Dot and Label based on boolean
        if (currentLead.getLeadStatus()) { // if true (Active)
            tvStatusLabel.setText(getResources().getStringArray(R.array.filter_status)[1]);
            viewStatusDot.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50")));
        } else { // if false (Lost)
            tvStatusLabel.setText(getResources().getStringArray(R.array.filter_status)[2]);
            viewStatusDot.setBackgroundTintList(ColorStateList.valueOf(Color.RED));
        }

        // 4. Populate Vehicle Interest
        Vehicle v = currentLead.getLeadVehicleInterest();
        if (v != null) {
            String vehicleDisplay = String.format("%s %s %s %s",
                    v.getYear(), v.getMake(), v.getModel(), v.getTrim());
            // Update the View Mode TextView
            tvViewVehicle.setText(vehicleDisplay);

            actvYear.setText(v.getYear(), false);
            actvMake.setText(v.getMake(), false);
            actvModel.setText(v.getModel(), false);
            actvTrim.setText(v.getTrim(), false);

            updateDependentAdapters(v);
        }else {
            tvViewVehicle.setText(R.string.no_vehicle_selected);
            actvYear.setText("");
            actvMake.setText("");
            actvModel.setText("");
            actvTrim.setText("");
        }

        // 5. Set Dates
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault());
        if (currentLead.getLeadFollowUpDate() != null) {
            tvDetUpdatedDate.setText(getString(R.string.last_updated, sdf.format(currentLead.getLeadFollowUpDate())));
        }else{
            // Hide if there's no update yet to keep the bottom clean
            tvDetUpdatedDate.setVisibility(View.GONE);
        }
        if (currentLead.getLeadCreatedAt() != null) {
            tvDetDate.setText(getString(R.string.created_on, sdf.format(currentLead.getLeadCreatedAt())));
        }

        // 6. Populate Edit Mode Fields (Hidden until Edit is clicked)
        etFirstName.setText(currentLead.getLeadFirstName());
        etLastName.setText(currentLead.getLeadLastName());
        etPhone.setText(currentLead.getLeadPhoneNumber());
        etEmail.setText(currentLead.getLeadEmail());
        etNotes.setText(currentLead.getLeadNotes());

        if (v != null) {
            actvYear.setText(v.getYear(), false);
            actvMake.setText(v.getMake(), false);
            actvModel.setText(v.getModel(), false);
            actvTrim.setText(v.getTrim(), false);
        }
    }

    private void updateDependentAdapters(Vehicle v) {
        // 1. CRITICAL SAFETY CHECK: If the master list hasn't loaded yet, stop here.
        if (allVehicles == null) {
            Log.e("LeadDetails", "allVehicles is null. Make sure it is initialized in onCreate.");
            return;
        }

        List<String> makes = new ArrayList<>();
        List<String> models = new ArrayList<>();
        List<String> trims = new ArrayList<>();

        // 2. Get current values safely to avoid NullPointerExceptions during comparison
        String currentYear = (v != null) ? v.getYear() : "";
        String currentMake = (v != null) ? v.getMake() : "";
        String currentModel = (v != null) ? v.getModel() : "";

        for (Vehicle veh : allVehicles) {
            // A. Logic for Makes:
            // If no year is selected, show all makes. If a year is selected, filter by that year.
            boolean yearMatch = currentYear.isEmpty() || currentYear.equals(veh.getYear());
            if (yearMatch && veh.getMake() != null && !makes.contains(veh.getMake())) {
                makes.add(veh.getMake());
            }

            // B. Logic for Models:
            // Only show models if the Make matches (and Year matches)
            boolean makeMatch = !currentMake.isEmpty() && currentMake.equals(veh.getMake());
            if (yearMatch && makeMatch && veh.getModel() != null && !models.contains(veh.getModel())) {
                models.add(veh.getModel());
            }

            // C. Logic for Trims:
            // Only show trims if Year, Make, and Model all match
            boolean modelMatch = !currentModel.isEmpty() && currentModel.equals(veh.getModel());
            if (yearMatch && makeMatch && modelMatch && veh.getTrim() != null && !trims.contains(veh.getTrim())) {
                trims.add(veh.getTrim());
            }
        }

        // 3. Update the UI adapters with the filtered lists
        updateAdapter(actvMake, makes);
        updateAdapter(actvModel, models);
        updateAdapter(actvTrim, trims);
    }

    private void setupTimeline() {
        // 1. Safety Check: Ensure the view exists in the XML
        if (rvTimeline == null) {
            Log.e("LeadDetails", "CRITICAL: rvLeadTimeline not found in XML.");
            return;
        }

        // 2. Safety Check: Ensure we have a lead and a creation date
        if (currentLead == null || currentLead.getLeadCreatedAt() == null) {
            Log.e("LeadDetails", "Lead data or CreatedAt date is null. Cannot generate timeline.");
            return;
        }

        // 3. Set Layout Manager ONLY if it hasn't been set yet
        if (rvTimeline.getLayoutManager() == null) {
            rvTimeline.setLayoutManager(new LinearLayoutManager(this));
        }

        // 4. Generate the 1-year scientific plan
        List<Task> timeline = scoringService.getFullTimeline(currentLead);

        // 5. Bind to Adapter
        if (timeline != null && !timeline.isEmpty()) {
            TimelineAdapter adapter = new TimelineAdapter(timeline);
            // Handle clicks on the timeline tasks
            adapter.setOnTaskStatusChangedListener(task -> {
                // 1. Update the task in the database (IsCompleted 0 -> 1)
                accessTasks.updateTask(task);

                // 2. Refresh the whole UI
                // This updates the Engagement Score and the Mission Board
                // because a completed task changes the lead's status.
                refreshUI();
            });

            adapter.setOnTaskClickListener(task -> {
                showTaskOptionsDialog(task);
            });

            rvTimeline.setNestedScrollingEnabled(true);
            rvTimeline.setAdapter(adapter);
        } else {
            Log.w("LeadDetails", "Timeline generated was empty.");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_lead_details, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_edit) {
            toggleEditMode();
            return true;
        } else if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void toggleEditMode() {
        isEditMode = !isEditMode;
        if (isEditMode) {
            layoutViewMode.setVisibility(View.GONE);
            layoutEditMode.setVisibility(View.VISIBLE);
            if (getSupportActionBar() != null) getSupportActionBar().setTitle(R.string.edit_lead);
        } else {
            layoutViewMode.setVisibility(View.VISIBLE);
            layoutEditMode.setVisibility(View.GONE);
            if (getSupportActionBar() != null) getSupportActionBar().setTitle(R.string.lead_details);
            refreshUI(); // Reset fields if they cancelled
        }
    }

    private void handleUpdate() {
        // Update the object
        currentLead.setLeadFirstName(etFirstName.getText().toString().trim());
        currentLead.setLeadLastName(etLastName.getText().toString().trim());
        currentLead.setLeadPhoneNumber(etPhone.getText().toString().trim());
        currentLead.setLeadEmail(etEmail.getText().toString().trim());
        currentLead.setLeadNotes(etNotes.getText().toString().trim());
        currentLead.setLeadFollowUpDate(new Date());

        String selectedYear = actvYear.getText().toString().trim();
        String selectedMake = actvMake.getText().toString().trim();
        String selectedModel = actvModel.getText().toString().trim();
        String selectedTrim = actvTrim.getText().toString().trim();

        Vehicle v = currentLead.getLeadVehicleInterest();
        if (v == null) {
            // If the lead had no vehicle interest before, create a new object
            v = new Vehicle();
        }

        // Force the new values into the object
        v.setYear(selectedYear);
        v.setMake(selectedMake);
        v.setModel(selectedModel);
        v.setTrim(selectedTrim);

        // Re-assign to ensure the lead object is holding the updated vehicle
        currentLead.setLeadVehicleInterest(v);

        String result = accessLeads.updateLead(currentLead);
        if (result == null) {
            Toast.makeText(this, R.string.lead_updated, Toast.LENGTH_SHORT).show();
            toggleEditMode(); // Switch back to view mode
        } else {
            Toast.makeText(this, getString(R.string.error_detail, result), Toast.LENGTH_SHORT).show();
        }
    }

    private void showDeleteConfirmation() {
        // Ensure we have a valid lead to delete
        if (currentLead == null) {
            Toast.makeText(this, R.string.no_lead_selected, Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_lead)
                .setMessage(getString(R.string.delete_lead_confirm, currentLead.getLeadFirstName()))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    // Call the business logic layer to delete
                    String result = accessLeads.deleteLead(currentLead);

                    if (result == null) {
                        // Success!
                        Toast.makeText(this, R.string.lead_deleted, Toast.LENGTH_SHORT).show();

                        // Set a result code so the previous activity (Calendar/AllLeads) knows to refresh
                        setResult(RESULT_OK);
                        finish();
                    } else {
                        // Failure: Show the specific error message from the database/business layer
                        Toast.makeText(this, getString(R.string.delete_failed, result), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void showAddTaskDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.schedule_task);

        // These keywords are recognized by your ScoringService for the +25 bonus
        String[] taskTypes = {"Appointment", "Test Drive", "Follow-up", "Other"};

        builder.setItems(getResources().getStringArray(R.array.task_type_labels), (dialog, which) -> {
            String selectedType = taskTypes[which];
            promptForTaskDetails(selectedType);
        });
        builder.show();
    }

    private void promptForTaskDetails(String type) {
        // 1. Initialize a Calendar to store the user's selection
        final Calendar calendar = java.util.Calendar.getInstance();

        // 2. First, pick the Date
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            calendar.set(java.util.Calendar.YEAR, year);
            calendar.set(java.util.Calendar.MONTH, month);
            calendar.set(java.util.Calendar.DAY_OF_MONTH, dayOfMonth);

            // 3. Second, pick the Time
            new TimePickerDialog(this, (timeView, hourOfDay, minute) -> {
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                calendar.set(Calendar.MINUTE, minute);

                // 4. Finally, ask for optional details
                showOptionalDetailsDialog(type, calendar.getTime());

            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show();

        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showOptionalDetailsDialog(String type, Date selectedDate) {
        EditText etInput = new EditText(this);
        etInput.setHint(R.string.details_optional);

        // Format the date for the dialog title so the user sees what they picked
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.details_for, type))
                .setMessage(getString(R.string.scheduled_for, sdf.format(selectedDate)))
                .setView(etInput)
                .setPositiveButton(R.string.confirm_save, (dialog, which) -> {
                    String note = etInput.getText().toString();
                    saveManualTask(type, note, selectedDate);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void saveManualTask(String type, String note,Date taskDate) {
        if (currentLead == null) return;
        // 1. Create the Task object
        String finalDescription = type + (note.trim().isEmpty() ? "" : ": " + note);
        Task newTask = new Task(currentLead, finalDescription, taskDate);
        String result = accessTasks.insertTask(newTask);

        if (result == null) {
            Toast.makeText(this, R.string.task_saved, Toast.LENGTH_SHORT).show();
            // 4. Refresh UI so the new task appears on the timeline immediately
            refreshUI();
        } else {
            Toast.makeText(this, getString(R.string.task_error, result), Toast.LENGTH_SHORT).show();
        }
    }

    private void showTaskOptionsDialog(Task task) {
        if (task.getEventID() <= 0) {
            String result = accessTasks.insertTask(task);
            if (result != null) {
                Toast.makeText(this, R.string.failed_tasks, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        String[] options = getResources().getStringArray(R.array.task_action_labels);

        new AlertDialog.Builder(this)
                .setTitle(R.string.manage_task)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showEditTaskDialog(task);
                    } else if (which == 1) {
                        showDeleteTaskConfirmation(task);
                    }
                })
                .show();
    }

    private void showEditTaskDialog(Task task) {
        // Updated list to include the 4 main types + Other
        String[] taskTypes = {"Appointment", "Test Drive", "Follow-up", "Other"};

        new AlertDialog.Builder(this)
                .setTitle(R.string.update_task_type)
                .setItems(getResources().getStringArray(R.array.task_type_labels), (dialog, which) -> {
                    String selectedType = taskTypes[which];
                    // Proceed to Date/Time selection specific to Editing
                    promptForEditDetails(task, selectedType);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void promptForEditDetails(Task task, String newType) {
        final Calendar calendar = Calendar.getInstance();
        // Initialize with the task's CURRENT date/time
        calendar.setTime(task.getDate());

        // 1. Pick the New Date
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

            // 2. Pick the New Time
            new TimePickerDialog(this, (timeView, hourOfDay, minute) -> {
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                calendar.set(Calendar.MINUTE, minute);

                // 3. Final Step: Edit the Note and Save
                showEditFinalConfirmation(task, newType, calendar.getTime());

            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show();

        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showEditFinalConfirmation(Task task, String type, Date newDate) {
        EditText etInput = new EditText(this);

        // Logic to extract the existing note from the "Type: Note" format
        String currentTitle = task.getTitle();
        String existingNote = currentTitle.contains(":") ? currentTitle.split(":", 2)[1].trim() : "";
        etInput.setText(existingNote);
        etInput.setHint(R.string.update_details);

        // UI Padding for the EditText
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        etInput.setPadding(padding, padding, padding, padding);

        new AlertDialog.Builder(this)
                .setTitle(R.string.finalize_changes)
                .setView(etInput)
                .setPositiveButton(R.string.save_changes, (dialog, which) -> {
                    String note = etInput.getText().toString();

                    // Reconstruct title to maintain "Type: Note" format for ScoringService
                    String finalTitle = type + (note.trim().isEmpty() ? "" : ": " + note);

                    // Update the object fields
                    task.setTitle(finalTitle);
                    task.setDate(newDate);

                    // Persist changes to SQLite
                    String result = accessTasks.updateTask(task);
                    if (result == null) {
                        Toast.makeText(this, R.string.task_updated, Toast.LENGTH_SHORT).show();
                        refreshUI(); // Refresh timeline and engagement score
                    } else {
                        Toast.makeText(this, getString(R.string.update_failed, result), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showDeleteTaskConfirmation(Task task) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_task)
                .setMessage(R.string.delete_task_question)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    String result = accessTasks.deleteTask(task);
                    if (result == null) {
                        Toast.makeText(this, R.string.task_removed, Toast.LENGTH_SHORT).show();
                        refreshUI();
                    } else {
                        Toast.makeText(this, getString(R.string.delete_failed, result), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
