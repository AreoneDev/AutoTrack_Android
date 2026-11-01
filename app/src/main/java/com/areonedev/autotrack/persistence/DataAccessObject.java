package com.areonedev.autotrack.persistence;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.File;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.areonedev.autotrack.application.Services;
import com.areonedev.autotrack.objects.Lead;
import com.areonedev.autotrack.objects.Task;
import com.areonedev.autotrack.objects.Vehicle;
import com.areonedev.autotrack.objects.Notification;

public class DataAccessObject implements DataAccess {
    private SQLiteDatabase db;
    private String dbName;
    private String dbType;
    private Context context;
    private final SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private static final String TAG = "DataAccessObject";
    private static final String EOF = "  ";
    private static final String TABLE_LEADS = "Leads";
    private static final String TABLE_NOTIFICATIONS = "Notifications";
    private static final String TABLE_TASKS = "Tasks";
    private static final String TABLE_VEHICLES = "Vehicles";
    private CryptoManager cryptoManager;
    private final boolean seedDemoData;

    public DataAccessObject(String dbName) {
        this(dbName, true);
    }

    public DataAccessObject(String dbName, boolean seedDemoData) {
        this.dbName = dbName;
        this.seedDemoData = seedDemoData;
        this.cryptoManager = new CryptoManager();
    }

    public boolean isOpen() { return db != null && db.isOpen(); }

    @Override
    public void open(String dbPath) {
        try {

            context = Services.getAppContext();
            if (context == null) {
                Log.e(TAG, "Context is null. Vehicle CSV import cannot run.");
            }// this step is for Vehicle DB to read the CSV file

            // 1. Safety Check: Ensure the directory exists
            File dbFile = new File(dbPath);
            File dbDir = dbFile.getParentFile();
            if (dbDir != null && !dbDir.exists()) {
                dbDir.mkdirs(); // Create the /databases/ folder if it's missing
            }

            // 2. Open or Create the database
            db = SQLiteDatabase.openOrCreateDatabase(dbPath, null);
            dbType = "SQLite";

            // 3. Create Table (Matches Lead class fields)
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_LEADS + " (" +
                    "LeadID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "Status INTEGER DEFAULT 1, " +
                    "FirstName TEXT, LastName TEXT, PhoneNumber TEXT, Email TEXT, " +
                    "Division TEXT, Address TEXT, City TEXT, Province TEXT, " +
                    "Country TEXT, PostalCode TEXT, Budget REAL, Stage TEXT, " +
                    "Notes TEXT, FollowUpDate TEXT, CreatedAt TEXT, " +
                    // Vehicle Interest Columns
                    "VI_Make TEXT, VI_Model TEXT, VI_Year TEXT, VI_Trim TEXT, " +
                    "VI_Price REAL, VI_Color TEXT, VI_InStock INTEGER, VI_VIN TEXT, VI_Trans TEXT, " +
                    // Trade-In Vehicle Columns
                    "TI_Make TEXT, TI_Model TEXT, TI_Year TEXT, TI_Trim TEXT, " +
                    "TI_Price REAL, TI_Color TEXT, TI_InStock INTEGER, TI_VIN TEXT, TI_Trans TEXT" +
                    ")");

            // 4. Create Notifications Table
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NOTIFICATIONS + " (" +
                    "NotificationID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "Title TEXT, " +
                    "Timestamp INTEGER, " +
                    "LeadID INTEGER" + // <--- Add this to link to TABLE_LEADS
                    ")");

            //5. Create Tasks Table
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_TASKS + " (" +
                    "TaskID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "Title TEXT, " +
                    "Timestamp INTEGER, " +
                    "IsCompleted INTEGER, " +
                    "LeadID INTEGER, " +
                    "FOREIGN KEY(LeadID) REFERENCES " + TABLE_LEADS + "(LeadID)" +
                    ")");

            //6. Create Vehicles Table
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_VEHICLES + " (" +
                    "VehicleID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "Make TEXT, " +
                    "Model TEXT, " +
                    "Year TEXT, " +
                    "Trim TEXT, " +
                    "Price REAL, " +
                    "Color TEXT, " +
                    "InStock INTEGER, " +
                    "VIN TEXT, " +
                    "Transmission TEXT" +
                    ")");

            Log.d(TAG, "Database opened successfully at: " + dbPath);

            /* Insert three leads to the DB */
            int count = getTableCount(TABLE_LEADS);

            if (count == 0 && seedDemoData) {
                Log.d(TAG, "Database is empty. Adding initial dummy leads.");
                addDummyLeads();
            } else {
                Log.d(TAG, "Database already contains " + count + " leads. Skipping dummy data.");
            }

            count = getTableCount(TABLE_NOTIFICATIONS);
            if (count == 0 && seedDemoData) {
                addDummyNotifications();
            } else {
                Log.d(TAG, "Database already contains " + count + " notifications. Skipping dummy data.");
            }

            count = getTableCount(TABLE_VEHICLES);
            if (count == 0) {
                if (context != null) {
                    importVehiclesFromCSV();
                } else {
                    Log.e(TAG, "Context is null. Skipping vehicle CSV import.");
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "CRITICAL: Failed to open database: " + e.getMessage());
            db = null; // Ensure it's explicitly null if it fails
        }

    }

    private int getTableCount(String tableName) {
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + tableName, null);
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        return count;
    }

    private void importVehiclesFromCSV() {
        InputStream is = null;
        BufferedReader reader = null;

        try {
            // 1. Open the file from assets
            is = context.getAssets().open("db/CARS.csv");
            reader = new BufferedReader(new InputStreamReader(is));

            String line;
            db.beginTransaction(); // Use transaction for high-speed insertion
            try {
                // Skip the header row (Make, Year, Model, Category)
                reader.readLine();

                while ((line = reader.readLine()) != null) {
                    // Split by comma
                    String[] parts = line.split(",");

                    // Ensure we have at least the first 3 columns
                    if (parts.length >= 3) {
                        ContentValues values = new ContentValues();
                        values.put("Make", parts[0].trim());
                        values.put("Year", parts[1].trim());
                        values.put("Model", parts[2].trim());
                        values.put("Trim", ""); // Default empty as it's not in your CSV

                        // We ignore parts[3] (Category) as requested

                        db.insert(TABLE_VEHICLES, null, values);
                    }
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            Log.d("DAO", "CSV Import successful.");
        } catch (Exception e) {
            Log.e("DAO", "Error importing CSV: " + e.getMessage());
        } finally {
            try {
                if (reader != null) reader.close();
                if (is != null) is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void close() {
        if (db != null && db.isOpen()) {
            db.close();
        }
    }

    @Override
    public String getLeadSequential(List<Lead> leadResult) {
        if (db == null) return "DB Null";

        // Best practice: clear the list so the caller doesn't get duplicate data
        leadResult.clear();

        try {
            Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_LEADS, null);
            parseCursor(cursor, leadResult);
            cursor.close();
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    @Override
    public ArrayList<Lead> getLeadRandom(Lead criteria) {
        ArrayList<Lead> results = new ArrayList<>();
        try {
            Cursor cursor;
            // If ID is 0, we search by Business Keys (Name/Phone) to find the ID
            if (criteria.getLeadID() > 0) {
                cursor = db.rawQuery("SELECT * FROM " + TABLE_LEADS + " WHERE LeadID = ?",
                        new String[]{String.valueOf(criteria.getLeadID())});
            } else {
                cursor = db.rawQuery("SELECT * FROM " + TABLE_LEADS + " WHERE FirstName = ? AND LastName = ? AND PhoneNumber = ?",
                        new String[]{criteria.getLeadFirstName(), criteria.getLeadLastName(), criteria.getLeadPhoneNumber()});
            }

            parseCursor(cursor, results);
            cursor.close();
        } catch (Exception e) {
            Log.e(TAG, "Search error: " + e.getMessage());
        }
        return results;
    }


    @Override
    public String insertLead(Lead lead) {
        try {
            ContentValues values = getLeadContentValues(lead);

            // IMPORTANT: Remove LeadID from values so SQLite generates a new one
            values.remove("LeadID");

            long rowId = db.insert(TABLE_LEADS, null, values);

            if (rowId != -1) {
                // SUCCESS: Now we assign the DB-generated ID back to the Lead object
                lead.setLeadID((int) rowId);
                return null;
            } else {
                return "Insert failed: rowId is -1";
            }
        } catch (Exception e) {
            return "SQL Error: " + e.getMessage();
        }
    }

    @Override
    public String updateLead(Lead lead) {
        if (db == null) return "Database connection lost";
        try {
            ContentValues values = getLeadContentValues(lead);

            // Log for debugging: verify what is being sent to the DB
            Log.d(TAG, "Updating Lead ID: " + lead.getLeadID() +
                    " with Vehicle: " + (lead.getLeadVehicleInterest() != null ?
                    lead.getLeadVehicleInterest().getMake() : "NULL"));

            int rows = db.update(TABLE_LEADS, values, "LeadID = ?",
                    new String[]{String.valueOf(lead.getLeadID())});

            return (rows > 0) ? null : "Update failed: Lead ID " + lead.getLeadID() + " not found";
        } catch (Exception e) {
            Log.e(TAG, "Update Error: " + e.getMessage());
            return e.getMessage();
        }
    }

    @Override
    public String deleteLead(Lead lead) {
        try {
            int rows = db.delete(TABLE_LEADS, "LeadID = ?", new String[]{String.valueOf(lead.getLeadID())});
            return (rows > 0) ? null : "Delete failed: Lead not found";
        } catch (Exception e) {
            return e.getMessage();
        }
    }


    /**
     * Helper to map Lead object to ContentValues for Insert/Update
     */
    private ContentValues getLeadContentValues(Lead lead) {
        ContentValues values = new ContentValues();
        // Encrypt sensitive PII fields
        values.put("FirstName", cryptoManager.encrypt(lead.getLeadFirstName()));
        values.put("LastName", cryptoManager.encrypt(lead.getLeadLastName()));
        values.put("PhoneNumber", cryptoManager.encrypt(lead.getLeadPhoneNumber()));
        values.put("Email", cryptoManager.encrypt(lead.getLeadEmail()));

        //Non-sensitive fields remain plain text
        values.put("Division", lead.getLeadDivision());
        values.put("Address", lead.getLeadAddress());
        values.put("City", lead.getLeadCity());
        values.put("Province", lead.getLeadProvince());
        values.put("Country", lead.getLeadCountry());
        values.put("PostalCode", lead.getLeadPostalCode());
        values.put("Budget", lead.getLeadBudget());
        values.put("Stage", lead.getLeadStage());
        values.put("Notes", lead.getLeadNotes());
        values.put("FollowUpDate", lead.getLeadFollowUpDate() != null ? formatter.format(lead.getLeadFollowUpDate()) : "");
        values.put("CreatedAt", formatter.format(lead.getLeadCreatedAt()));
        values.put("Status", lead.getLeadStatus() ? 1 : 0);

        // Flatten Vehicle Interest
        Vehicle vi = lead.getLeadVehicleInterest();
        if (vi != null) {
            values.put("VI_Make", vi.getMake());
            values.put("VI_Model", vi.getModel());
            values.put("VI_Year", vi.getYear());
            values.put("VI_Trim", vi.getTrim());
            values.put("VI_Price", vi.getPrice());
            values.put("VI_Color", vi.getColor());
            values.put("VI_InStock", vi.isInStock() ? 1 : 0);
            values.put("VI_VIN", vi.getVin());
            values.put("VI_Trans", vi.getTransmission());
        }

        // Flatten Trade-In
        Vehicle ti = lead.getTradeInVehicle();
        if (ti != null) {
            values.put("TI_Make", ti.getMake());
            values.put("TI_Model", ti.getModel());
            values.put("TI_Year", ti.getYear());
            values.put("TI_Trim", ti.getTrim());
            values.put("TI_Price", ti.getPrice());
            values.put("TI_Color", ti.getColor());
            values.put("TI_InStock", ti.isInStock() ? 1 : 0);
            values.put("TI_VIN", ti.getVin());
            values.put("TI_Trans", ti.getTransmission());
        }

        return values;
    }

    public String processSQLError(Exception e) {
        String errorMsg = "*** SQL Error: " + e.getMessage();
        e.printStackTrace();
        return errorMsg;
    }

    private void parseCursor(Cursor cursor, List<Lead> list) throws Exception {
        if (cursor.moveToFirst()) {
            do {
                // Use the No-Args constructor to avoid triggering "Today" defaults
                Lead lead = new Lead();

                lead.setLeadID(cursor.getLong(cursor.getColumnIndexOrThrow("LeadID")));

                // Decrypt sensitive PII fields
                lead.setLeadFirstName(cryptoManager.decrypt(cursor.getString(cursor.getColumnIndexOrThrow("FirstName"))));
                lead.setLeadLastName(cryptoManager.decrypt(cursor.getString(cursor.getColumnIndexOrThrow("LastName"))));
                lead.setLeadPhoneNumber(cryptoManager.decrypt(cursor.getString(cursor.getColumnIndexOrThrow("PhoneNumber"))));
                lead.setLeadEmail(cryptoManager.decrypt(cursor.getString(cursor.getColumnIndexOrThrow("Email"))));

                // Non-sensitive fields
                lead.setLeadDivision(cursor.getString(cursor.getColumnIndexOrThrow("Division")));
                lead.setLeadAddress(cursor.getString(cursor.getColumnIndexOrThrow("Address")));
                lead.setLeadCity(cursor.getString(cursor.getColumnIndexOrThrow("City")));
                lead.setLeadProvince(cursor.getString(cursor.getColumnIndexOrThrow("Province")));
                lead.setLeadCountry(cursor.getString(cursor.getColumnIndexOrThrow("Country")));
                lead.setLeadPostalCode(cursor.getString(cursor.getColumnIndexOrThrow("PostalCode")));
                lead.setLeadBudget(cursor.getDouble(cursor.getColumnIndexOrThrow("Budget")));
                lead.setLeadStage(cursor.getString(cursor.getColumnIndexOrThrow("Stage")));
                lead.setLeadNotes(cursor.getString(cursor.getColumnIndexOrThrow("Notes")));
                lead.setLeadStatus(cursor.getInt(cursor.getColumnIndexOrThrow("Status")) == 1);
                // 1. Reconstruct Vehicle Interest
                int viYearIdx = cursor.getColumnIndexOrThrow("VI_Year");
                int viMakeIdx = cursor.getColumnIndexOrThrow("VI_Make");
                int viModelIdx = cursor.getColumnIndexOrThrow("VI_Model");

                if (!cursor.isNull(viYearIdx) || !cursor.isNull(viMakeIdx) || !cursor.isNull(viModelIdx)) {
                    Vehicle vi = new Vehicle(
                            cursor.getString(viMakeIdx),
                            cursor.getString(viModelIdx),
                            cursor.getString(viYearIdx),
                            cursor.getString(cursor.getColumnIndexOrThrow("VI_Trim")),
                            cursor.getDouble(cursor.getColumnIndexOrThrow("VI_Price")),
                            cursor.getString(cursor.getColumnIndexOrThrow("VI_Color")),
                            cursor.getInt(cursor.getColumnIndexOrThrow("VI_InStock")) == 1,
                            cursor.getString(cursor.getColumnIndexOrThrow("VI_VIN")),
                            cursor.getString(cursor.getColumnIndexOrThrow("VI_Trans"))
                    );
                    lead.setLeadVehicleInterest(vi);
                }

                // 2. Reconstruct Trade-In
                int tiMakeIdx = cursor.getColumnIndexOrThrow("TI_Make");
                if (!cursor.isNull(tiMakeIdx)) {
                    Vehicle ti = new Vehicle(
                            cursor.getString(tiMakeIdx),
                            cursor.getString(cursor.getColumnIndexOrThrow("TI_Model")),
                            cursor.getString(cursor.getColumnIndexOrThrow("TI_Year")),
                            cursor.getString(cursor.getColumnIndexOrThrow("TI_Trim")),
                            cursor.getDouble(cursor.getColumnIndexOrThrow("TI_Price")),
                            cursor.getString(cursor.getColumnIndexOrThrow("TI_Color")),
                            cursor.getInt(cursor.getColumnIndexOrThrow("TI_InStock")) == 1,
                            cursor.getString(cursor.getColumnIndexOrThrow("TI_VIN")),
                            cursor.getString(cursor.getColumnIndexOrThrow("TI_Trans"))
                    );
                    lead.setTradeInVehicle(ti);
                }

                // 3. Parse Dates from DB strings
                String fDateStr = cursor.getString(cursor.getColumnIndexOrThrow("FollowUpDate"));
                if (fDateStr != null && !fDateStr.isEmpty()) {
                    lead.setLeadFollowUpDate(formatter.parse(fDateStr));
                }

                String cDateStr = cursor.getString(cursor.getColumnIndexOrThrow("CreatedAt"));
                if (cDateStr != null && !cDateStr.isEmpty()) {
                    lead.setLeadCreatedAt(formatter.parse(cDateStr));
                }

                list.add(lead);
            } while (cursor.moveToNext());
        }
    }

    private Lead getLeadByID(long id) {
        Lead criteria = new Lead();
        criteria.setLeadID(id);
        ArrayList<Lead> results = getLeadRandom(criteria);
        return (results != null && !results.isEmpty()) ? results.get(0) : null;
    }

    private void addDummyLeads() {
        // 1. Setup Vehicles
        Vehicle atlas = new Vehicle("Volkswagen", "Atlas", "2024", "Execline");
        Vehicle tiguan = new Vehicle("Volkswagen", "Tiguan", "2024", "Highline R-Line");
        Vehicle jetta = new Vehicle("Volkswagen", "Jetta", "2024", "Highline");
        Vehicle oldCivic = new Vehicle("Honda", "Civic", "2018", "Highline");

        Calendar calendar = Calendar.getInstance();

        /* --- HISTORICAL LEADS --- */
        // We use the No-Args constructor here because we need to MANUALLY set
        // the dates to April 1st/2nd. The second constructor would force them to "Today".

        // Darren (April 1st)
        Lead demo1 = new Lead();
        demo1.setLeadFirstName("Demo1");
        demo1.setLeadLastName("Customer");
        demo1.setLeadPhoneNumber("416-222-3333");
        demo1.setLeadVehicleInterest(jetta);
        demo1.setTradeInVehicle(oldCivic);
        calendar.set(2026, Calendar.MAY, 1);
        demo1.setLeadCreatedAt(calendar.getTime());
        demo1.setLeadFollowUpDate(calendar.getTime());
        demo1.setLeadStage("NEW");
        insertLead(demo1);

        // Darryl (April 2nd)
        Lead demo2 = new Lead();
        demo2.setLeadFirstName("Demo2");
        demo2.setLeadLastName("Customer");
        demo2.setLeadPhoneNumber("647-111-2222");
        demo2.setLeadVehicleInterest(atlas);
        calendar.set(2026, Calendar.MAY, 2);
        demo2.setLeadCreatedAt(calendar.getTime());
        demo2.setLeadFollowUpDate(calendar.getTime());
        demo2.setLeadStage("VISITED");
        insertLead(demo2);

        /* --- CURRENT LEADS (Using your SECOND constructor) --- */
        // These will automatically be set to Stage="NEW" and Date="Today"
        // because of the logic inside your new constructor.

        // Pengyu Wang
        Lead demo3 = new Lead(
                "Demo3", "Customer", "613-111-2222", "pengyu@example.com",
                "New Cars", "101 Elgin St", "Ottawa", "ON", "Canada", "K1P 5K7",
                52000, atlas, null, "Interested in the new Atlas"
        );
        insertLead(demo3);

        // Irfan Nassir
        Lead demo4 = new Lead(
                "Demo4", "Customer", "416-777-8888", "irfan@example.com",
                "Fleet", "202 King St", "Toronto", "ON", "Canada", "M5V 1J2",
                60000, tiguan, null, "Looking for fleet pricing"
        );
        insertLead(demo4);
    }


    /*** Notification ***/
    @Override
    public String getNotificationSequential(List<Notification> notificationResult) {
        if (db == null) return "Database connection lost";
        notificationResult.clear();

        try {
            Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_NOTIFICATIONS + " ORDER BY Timestamp DESC", null);

            if (cursor.moveToFirst()) {
                do {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow("NotificationID"));
                    String title = cursor.getString(cursor.getColumnIndexOrThrow("Title"));
                    long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("Timestamp"));
                    long leadId = cursor.getLong(cursor.getColumnIndexOrThrow("LeadID"));

                    // 1. Resolve the Lead
                    Lead lead = getLeadByID(leadId);

                    // 2. SAFETY CHECK: If lead is null, create a "Dummy" lead so the app doesn't crash
                    if (lead == null) {
                        lead = new Lead();
                        lead.setLeadFirstName("Unknown");
                        lead.setLeadLastName("Contact");
                    }

                    // 3. Create the notification
                    Notification note = new Notification(lead, title, new Date(timestamp));
                    note.setEventID(id);
                    notificationResult.add(note);
                } while (cursor.moveToNext());
            }
            cursor.close();
            return null; // Success
        } catch (Exception e) {
            Log.e("DAO", "Error: " + e.getMessage());
            return e.getMessage(); // This is what triggers your Toast error
        }
    }

    @Override
    public ArrayList<Notification> getNotificationRandom(Notification criteria) {
        ArrayList<Notification> results = new ArrayList<>();
        if (db == null) return results;

        try {
            Cursor cursor;
            if (criteria.getTitle() != null && !criteria.getTitle().isEmpty()) {
                cursor = db.rawQuery("SELECT * FROM " + TABLE_NOTIFICATIONS + " WHERE Title LIKE ?",
                        new String[]{"%" + criteria.getTitle() + "%"});
            } else {
                cursor = db.rawQuery("SELECT * FROM " + TABLE_NOTIFICATIONS + " WHERE NotificationID = ?",
                        new String[]{String.valueOf(criteria.getEventID())});
            }

            if (cursor.moveToFirst()) {
                do {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow("NotificationID"));
                    String title = cursor.getString(cursor.getColumnIndexOrThrow("Title"));
                    long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("Timestamp"));
                    long leadId = cursor.getLong(cursor.getColumnIndexOrThrow("LeadID"));

                    Lead lead = getLeadByID(leadId);
                    Notification note = new Notification(lead, title, new Date(timestamp));
                    note.setEventID(id);
                    results.add(note);
                } while (cursor.moveToNext());
            }
            cursor.close();
        } catch (Exception e) {
            Log.e("DAO", "Random search error: " + e.getMessage());
        }
        return results;
    }

    @Override
    public String insertNotification(Notification notification) {
        try {
            ContentValues values = new ContentValues();
            values.put("Title", notification.getTitle());
            values.put("Timestamp", notification.getDate().getTime());
            // Get ID from the linked Lead object
            values.put("LeadID", notification.getLead() != null ? notification.getLead().getLeadID() : -1);

            long rowId = db.insert(TABLE_NOTIFICATIONS, null, values);
            if (rowId != -1) {
                notification.setEventID(rowId);
                return null;
            }
            return "Insert notification failed";
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    @Override
    public String updateNotification(Notification notification) {
        try {
            ContentValues values = new ContentValues();
            values.put("Title", notification.getTitle());
            values.put("Timestamp", notification.getDate().getTime());
            values.put("LeadID", notification.getLead() != null ? notification.getLead().getLeadID() : -1);

            int rows = db.update(TABLE_NOTIFICATIONS, values, "NotificationID = ?",
                    new String[]{String.valueOf(notification.getEventID())});

            return (rows > 0) ? null : "Update failed: Notification not found";
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    @Override
    public String deleteNotification(Notification notification) {
        try {
            int rows = db.delete(TABLE_NOTIFICATIONS, "NotificationID = ?",
                    new String[]{String.valueOf(notification.getEventID())});
            return (rows > 0) ? null : "Delete failed: Notification not found";
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    @Override
    public List<Notification> getAllNotifications() {
        List<Notification> notifications = new ArrayList<>();
        // Reuse the sequential method because it already handles:
        // 1. Database connection checks
        // 2. Lead resolution (getLeadByID)
        // 3. Mapping DB IDs to Event IDs
        // 4. Sorting by Timestamp
        getNotificationSequential(notifications);
        return notifications;
    }

    private void addDummyNotifications() {
        // We must fetch actual leads first to link them to the dummy notifications
        List<Lead> leads = new ArrayList<>();
        getLeadSequential(leads);

        if (!leads.isEmpty()) {
            // Link to the first lead (e.g., Darren Adam)
            insertNotification(new Notification(leads.get(0), "Incoming Call from " + leads.get(0).getLeadFirstName(), new Date()));

            if (leads.size() > 2) {
                // Link to the third lead (e.g., Pengyu Wang)
                insertNotification(new Notification(leads.get(2), "New SMS from " + leads.get(2).getLeadFirstName(),
                        new Date(System.currentTimeMillis() - 3600000)));
            }
        }
    }

    /*** Task ***/
    @Override
    public String getTaskSequential(List<Task> taskResult) {
        if (db == null) return "Database connection lost";
        taskResult.clear();

        try {
            // Order by Timestamp so the timeline flows correctly
            Cursor cursor = db.rawQuery("SELECT * FROM Tasks ORDER BY Timestamp ASC", null);

            if (cursor.moveToFirst()) {
                do {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow("TaskID"));
                    String title = cursor.getString(cursor.getColumnIndexOrThrow("Title"));
                    long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("Timestamp"));
                    int isCompleted = cursor.getInt(cursor.getColumnIndexOrThrow("IsCompleted"));
                    long leadId = cursor.getLong(cursor.getColumnIndexOrThrow("LeadID"));

                    // Resolve the Lead associated with this task
                    Lead lead = getLeadByID(leadId);

                    if (lead != null) {
                        Task task = new Task(lead, title, new Date(timestamp));
                        task.setCompleted(isCompleted == 1);
                        task.setEventID(id);
                        taskResult.add(task);
                    }
                } while (cursor.moveToNext());
            }
            cursor.close();
            return null;
        } catch (Exception e) {
            Log.e("DAO", "getTaskSequential Error: " + e.getMessage());
            return e.getMessage();
        }
    }

    @Override
    public ArrayList<Task> getTaskRandom(Task criteria) {
        ArrayList<Task> results = new ArrayList<>();
        if (db == null) return results;

        try {
            // Search by LeadID to get all tasks for a specific lead
            Cursor cursor = db.rawQuery("SELECT * FROM Tasks WHERE LeadID = ?",
                    new String[]{String.valueOf(criteria.getLead().getLeadID())});

            if (cursor.moveToFirst()) {
                do {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow("TaskID"));
                    String title = cursor.getString(cursor.getColumnIndexOrThrow("Title"));
                    long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("Timestamp"));
                    int isCompleted = cursor.getInt(cursor.getColumnIndexOrThrow("IsCompleted"));

                    Task task = new Task(criteria.getLead(), title, new Date(timestamp));
                    task.setCompleted(isCompleted == 1);
                    task.setEventID(id);
                    results.add(task);
                } while (cursor.moveToNext());
            }
            cursor.close();
        } catch (Exception e) {
            Log.e("DAO", "getTaskRandom Error: " + e.getMessage());
        }
        return results;
    }

    @Override
    public String insertTask(Task task) {
        try {
            ContentValues values = new ContentValues();
            values.put("Title", task.getTitle());
            values.put("Timestamp", task.getDate().getTime());
            values.put("IsCompleted", task.isCompleted() ? 1 : 0);
            values.put("LeadID", task.getLead() != null ? task.getLead().getLeadID() : -1);

            long rowId = db.insert("Tasks", null, values);
            if (rowId != -1) {
                task.setEventID((int) rowId);
                return null;
            } else {
                return "Insert task failed";
            }
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    @Override
    public String updateTask(Task task) {
        try {
            ContentValues values = new ContentValues();
            values.put("Title", task.getTitle());
            values.put("Timestamp", task.getDate().getTime());
            values.put("IsCompleted", task.isCompleted() ? 1 : 0);
            values.put("LeadID", task.getLead() != null ? task.getLead().getLeadID() : -1);

            String whereClause = "TaskID = ?";
            String[] whereArgs = {String.valueOf(task.getEventID())};

            int rows = db.update("Tasks", values, whereClause, whereArgs);

            return (rows > 0) ? null : "Update failed: Task not found";
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    @Override
    public String deleteTask(Task task) {
        try {
            int rows = db.delete("Tasks", "TaskID = ?",
                    new String[]{String.valueOf(task.getEventID())});
            return (rows > 0) ? null : "Delete failed";
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    /*** Vehicle ***/
    @Override
    public String getVehicleSequential(List<Vehicle> vehicleResult) {
        if (db == null) return "Database connection lost";
        vehicleResult.clear();

        try {
            // We only fetch the columns the user cares about
            Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_VEHICLES + " ORDER BY Year DESC, Make ASC", null);

            if (cursor.moveToFirst()) {
                do {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow("VehicleID"));
                    String make = cursor.getString(cursor.getColumnIndexOrThrow("Make"));
                    String model = cursor.getString(cursor.getColumnIndexOrThrow("Model"));
                    String year = cursor.getString(cursor.getColumnIndexOrThrow("Year"));
                    String trim = cursor.getString(cursor.getColumnIndexOrThrow("Trim"));

                    // Use the Partial Constructor (Make, Model, Year, Trim)
                    Vehicle v = new Vehicle(make, model, year, trim);
                    v.setVehicleID(id);

                    vehicleResult.add(v);
                } while (cursor.moveToNext());
            }
            cursor.close();
            return null;
        } catch (Exception e) {
            Log.e("DAO", "getVehicleSequential Error: " + e.getMessage());
            return e.getMessage();
        }
    }

    @Override
    public ArrayList<Vehicle> getVehicleRandom(Vehicle criteria) {
        ArrayList<Vehicle> results = new ArrayList<>();
        if (db == null) return results;

        try {
            // Search by Model or Make - this is what the user will type in the search bar
            String search = "%" + criteria.getModel() + "%";
            Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_VEHICLES + " WHERE Model LIKE ? OR Make LIKE ?",
                    new String[]{search, search});

            if (cursor.moveToFirst()) {
                do {
                    Vehicle v = new Vehicle(
                            cursor.getString(cursor.getColumnIndexOrThrow("Make")),
                            cursor.getString(cursor.getColumnIndexOrThrow("Model")),
                            cursor.getString(cursor.getColumnIndexOrThrow("Year")),
                            cursor.getString(cursor.getColumnIndexOrThrow("Trim"))
                    );
                    v.setVehicleID(cursor.getLong(cursor.getColumnIndexOrThrow("VehicleID")));
                    results.add(v);
                } while (cursor.moveToNext());
            }
            cursor.close();
        } catch (Exception e) {
            Log.e("DAO", "getVehicleRandom Error: " + e.getMessage());
        }
        return results;
    }

    @Override
    public String insertVehicle(Vehicle vehicle) {
        try {
            ContentValues values = new ContentValues();
            values.put("Make", vehicle.getMake());
            values.put("Model", vehicle.getModel());
            values.put("Year", vehicle.getYear());
            values.put("Trim", vehicle.getTrim());
            // Other fields get defaults from the DB schema or stay null

            long rowId = db.insert(TABLE_VEHICLES, null, values);
            if (rowId != -1) {
                vehicle.setVehicleID(rowId);
                return null;
            }
            return "Insert vehicle failed";
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    @Override
    public String updateVehicle(Vehicle vehicle) {
        try {
            ContentValues values = new ContentValues();
            values.put("Make", vehicle.getMake());
            values.put("Model", vehicle.getModel());
            values.put("Year", vehicle.getYear());
            values.put("Trim", vehicle.getTrim());

            // Use CarID as the unique identifier
            int rows = db.update(TABLE_VEHICLES, values, "VehicleID = ?",
                    new String[]{String.valueOf(vehicle.getVehicleID())});

            return (rows > 0) ? null : "Update failed: Vehicle not found";
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    @Override
    public String deleteVehicle(Vehicle vehicle) {
        try {
            int rows = db.delete(TABLE_VEHICLES, "VehicleID = ?",
                    new String[]{String.valueOf(vehicle.getVehicleID())});
            return (rows > 0) ? null : "Delete failed: Vehicle not found";
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    @Override
    public List<String> getFilteredColumnValues(String targetColumn, String selection, String[] selectionArgs) {
        List<String> values = new ArrayList<>();

        // Use the existing 'db' instance variable instead of calling getReadableDatabase()
        if (db == null) return values;

        try {
            // Use DISTINCT so we don't get duplicates
            // Note: Ensure "Leads" matches your TABLE_LEADS constant if you have one

            Cursor cursor = db.query(true, "Leads", new String[]{targetColumn},
                    selection, selectionArgs, null, null, targetColumn + " ASC", null);

            if (cursor.moveToFirst()) {
                do {
                    int columnIndex = cursor.getColumnIndex(targetColumn);
                    if (columnIndex != -1) {
                        String val = cursor.getString(columnIndex);
                        if (val != null && !val.isEmpty()) {
                            values.add(val);
                        }
                    }
                } while (cursor.moveToNext());
            }
            cursor.close();
        } catch (Exception e) {
            Log.e("DAO", "Error fetching filtered column values: " + e.getMessage());
        }

        return values;
    }

    @Override
    public List<String> getUniqueColumnValues(String columnName) {
        List<String> values = new ArrayList<>();
        if (db == null) return values;

        // Use true for 'distinct' parameter to get unique values only
        Cursor cursor = db.query(true, "Leads", new String[]{columnName},
                columnName + " IS NOT NULL", null, null, null, columnName + " DESC", null);

        if (cursor.moveToFirst()) {
            do {
                values.add(cursor.getString(0));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return values;
    }
}
