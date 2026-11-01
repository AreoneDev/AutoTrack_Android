package com.areonedev.autotrack.presentation;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.areonedev.autotrack.R;
import com.areonedev.autotrack.business.AccessLeads;
import com.areonedev.autotrack.objects.Lead;
import com.areonedev.autotrack.objects.Vehicle;

import java.util.Date;

public class LeadsCreationActivity extends AccountActivity {
    private EditText etFirstName, etLastName, etPhone, etEmail, etMake, etModel, etYear,etTrim,etNotes;
    private Button btnSave;
    private AccessLeads accessLeads;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!com.areonedev.autotrack.auth.AuthRuntime.requireAccount(this)) return;
        setContentView(R.layout.activity_leads_creation);


        // 1. Initialize Toolbar
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // 1. Enable the Return Button in the Action Bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("Add New Lead");
        }

        accessLeads = new AccessLeads();
        initViews();

        btnSave.setOnClickListener(v -> validateAndSave());
    }

    // 2. Handle the Return Button Click
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed(); // Returns to LeadsActivity
        return true;
    }

    private void initViews() {
        etFirstName = findViewById(R.id.etFirstName);
        etLastName = findViewById(R.id.etLastName);
        etPhone = findViewById(R.id.etPhone);
        etEmail = findViewById(R.id.etEmail);
        etMake = findViewById(R.id.etMake);
        etModel = findViewById(R.id.etModel);
        etYear = findViewById(R.id.etYear);
        etTrim = findViewById(R.id.etTrim);
        etNotes = findViewById(R.id.etNotes);
        btnSave = findViewById(R.id.btnSaveLead);
    }

    private void validateAndSave() {
        String first = etFirstName.getText().toString().trim();
        String last = etLastName.getText().toString().trim();
        String phone = formatPhoneNumber(etPhone.getText().toString().trim());
        String email = etEmail.getText().toString().trim();

        // Basic Validation
        if (first.isEmpty() || last.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "Name and Phone are required", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. DUPLICATE CHECK (The "Situation" you mentioned)
        // We check if a lead with this Name + Phone already exists
        Lead existingLead = accessLeads.getLeadByName_Phone(first + " " + last, phone);

        if (existingLead != null) {
            // Case 2: Duplicate found
            Toast.makeText(this, "Error: A lead with this phone number already exists!", Toast.LENGTH_LONG).show();
        } else {
            // Case 1: Positive case - Create new lead
            saveNewLead(first, last, phone, email);
        }
    }

    private String formatPhoneNumber(String phone) {
        // Remove all non-digits (e.g., spaces, dashes, parentheses)
        String clean = phone.replaceAll("[^\\d]", "");

        // If we have exactly 10 digits, format as xxx-xxx-xxxx
        if (clean.length() == 10) {
            return clean.substring(0, 3) + "-" +
                    clean.substring(3, 6) + "-" +
                    clean.substring(6);
        }

        // If it's not 10 digits, return the cleaned numeric string or original
        return clean.isEmpty() ? phone : clean;
    }

    private void saveNewLead(String first, String last, String phone, String email) {
        // 1. Get Vehicle info from UI
        String make = etMake.getText() != null ? etMake.getText().toString().trim() : "";
        String model = etModel.getText() != null ? etModel.getText().toString().trim() : "";
        String year = etYear.getText() != null ? etYear.getText().toString().trim() : "";
        String trim = etTrim.getText() != null ? etTrim.getText().toString().trim() : "";

        // 2. Get Notes
        String notes = etNotes.getText() != null && !etNotes.getText().toString().isEmpty()
                ? etNotes.getText().toString()
                : "Added via App";

        // 3. Create the Vehicle Interest object
        Vehicle interest = new Vehicle(make, model, year, trim);

        // 4. Construct the Lead using your NEW 14-parameter constructor
        // Notice: We no longer pass "NEW", followUpDate, or createdAt.
        // The Lead class handles those automatically now!
        Lead newLead = new Lead(
                first,
                last,
                phone,
                email,
                "Sales", // Division
                "", "", "", "", "", // Address fields (Street, City, Prov, Country, Postal)
                0.0,      // Budget
                interest, // Vehicle Interest
                null,     // Trade-In Vehicle
                notes     // Notes
        );

        // 5. Insert into Database
        String result = accessLeads.insertLead(newLead);

        if (result == null) {
            Toast.makeText(this, "Lead created successfully!", Toast.LENGTH_SHORT).show();
            finish(); // Return to LeadsActivity
        } else {
            Toast.makeText(this, "Database Error: " + result, Toast.LENGTH_SHORT).show();
        }
    }
}
