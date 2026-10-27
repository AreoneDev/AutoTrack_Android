package com.areonedev.autotrack.persistence;

import com.areonedev.autotrack.application.Main;
import com.areonedev.autotrack.objects.Lead;
import com.areonedev.autotrack.objects.Notification;
import com.areonedev.autotrack.objects.Task;
import com.areonedev.autotrack.objects.Vehicle;
import com.areonedev.autotrack.persistence.DataAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Calendar;
import java.util.Date;

public class DataAccessStub implements DataAccess {
    private String dbName;
    private String dbType = "stub";

    private ArrayList<Lead> leads;

    public DataAccessStub(String dbName) {
        this.dbName = dbName;
    }

    public DataAccessStub() {
        this(Main.dbName);
    }

    public void open(String dbPath) {
        Lead lead;
        Date today, createdDate;
        leads = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        calendar.set(2026, Calendar.MARCH, 8);
        today = calendar.getTime();

        calendar.set(2023, Calendar.JANUARY, 1);
        createdDate = calendar.getTime();
        Vehicle jetta = new Vehicle("Volkswagen", "Jetta", "2024", "Comfortline");
        insertLead(com.areonedev.autotrack.testing.LeadFixtures.create(
                "Alice", "Chen", "204-555-8123", "alice@example.com", "Sales",
                "123 Main St", "Winnipeg", "MB", "Canada", "R3C 1A1",
                32000.0, jetta, null, "NEW", today,
                "First inquiry from website", createdDate));

        calendar.set(2024, Calendar.FEBRUARY, 2);
        createdDate = calendar.getTime();
        Vehicle tiguan = new Vehicle("Volkswagen", "Tiguan", "2024","Comfortline R-Line Black Edition");
        insertLead(com.areonedev.autotrack.testing.LeadFixtures.create(
                "Brian", "Miller", "204-555-1290", "brian@example.com", "Sales",
                "456 Broadway", "Winnipeg", "MB", "Canada", "R3C 1A2",
                45000.0, tiguan, null, "VISITED", today,
                "Visited showroom, interested in financing", createdDate));

        calendar.set(2025, Calendar.MARCH, 3);
        createdDate = calendar.getTime();
        Vehicle atlas = new Vehicle("Volkswagen", "Atlas", "2024","Peak Edition");
        insertLead(com.areonedev.autotrack.testing.LeadFixtures.create(
                "Sophia", "Martinez", "204-555-6677", "sophia@example.com", "Sales",
                "789 Portage Ave", "Winnipeg", "MB", "Canada", "R3C 1A3",
                52000.0, atlas, null, "NEGOTIATION", today,
                "Negotiating trade-in value", createdDate));
        System.out.println("Successfully connected "+dbType +" database " +dbPath);
    }

    @Override
    public void close() {
        leads.clear();
        System.out.println("Closed " +dbType +" database " +dbName);
    }

    @Override
    public String getLeadSequential(List<Lead> leadResult) {
        leadResult.clear();
        leadResult.addAll(leads);
        return null;
    }

    @Override
    public ArrayList<Lead> getLeadRandom(Lead currLead) {
        ArrayList<Lead> newLeads;
        int index;

        newLeads = new ArrayList<>();
        index = leads.indexOf(currLead);

        if (index >= 0) {
            newLeads.add(leads.get(index));
        }

        return newLeads;
    }

    @Override
    public String insertLead(Lead currLead) {
        //don't bother checking for duplicates, but I still added a duplicate lead checker
        String res = null;
        if(leads.contains(currLead)){
            res = "Duplicate lead.";
        }else{
            leads.add(currLead);
            currLead.setLeadID(leads.size());
        }

        return res;
    }

    @Override
    public String updateLead(Lead currLead) {
        int index;
        String res = null;
        index = leads.indexOf(currLead);
        if (index >= 0) {
            leads.set(index, currLead);
        }else{
            res = "Lead not found.";
        }
        return res;
    }

    @Override
    public String deleteLead(Lead currLead) {
        int index;
        String res = null;
        index = leads.indexOf(currLead);
        if (index >= 0) {
            leads.remove(index);
        }else{
            res="Lead not found.";
        }
        return res;
    }

    @Override
    public List<String> getUniqueColumnValues(String columnName) {
        return new ArrayList<>();
    }

    @Override
    public List<String> getFilteredColumnValues(String targetColumn, String selection, String[] selectionArgs) {
        return new ArrayList<>();
    }

    @Override public String getNotificationSequential(List<Notification> result) { result.clear(); return null; }
    @Override public ArrayList<Notification> getNotificationRandom(Notification value) { return new ArrayList<>(); }
    @Override public String insertNotification(Notification value) { return null; }
    @Override public String updateNotification(Notification value) { return null; }
    @Override public String deleteNotification(Notification value) { return null; }
    @Override public List<Notification> getAllNotifications() { return new ArrayList<>(); }
    @Override public String getTaskSequential(List<Task> result) { result.clear(); return null; }
    @Override public ArrayList<Task> getTaskRandom(Task value) { return new ArrayList<>(); }
    @Override public String insertTask(Task value) { return null; }
    @Override public String updateTask(Task value) { return null; }
    @Override public String deleteTask(Task value) { return null; }
    @Override public String getVehicleSequential(List<Vehicle> result) { result.clear(); return null; }
    @Override public ArrayList<Vehicle> getVehicleRandom(Vehicle value) { return new ArrayList<>(); }
    @Override public String insertVehicle(Vehicle value) { return null; }
    @Override public String updateVehicle(Vehicle value) { return null; }
    @Override public String deleteVehicle(Vehicle value) { return null; }
}
