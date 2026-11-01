package com.areonedev.autotrack.persistence;

import java.util.ArrayList;
import java.util.List;

import com.areonedev.autotrack.objects.Lead;
import com.areonedev.autotrack.objects.Notification;
import com.areonedev.autotrack.objects.Task;
import com.areonedev.autotrack.objects.Vehicle;

public interface DataAccess {
    void open(String string);

    void close();

    /*** Lead ***/
    String getLeadSequential(List<Lead> leadResult);

    ArrayList<Lead> getLeadRandom(Lead lead);

    String insertLead(Lead lead);

    String updateLead(Lead lead);

    String deleteLead(Lead lead);

    default com.areonedev.autotrack.mapping.LeadIdentity getLeadIdentity(long localId) {
        throw new UnsupportedOperationException("Lead mapping is unavailable.");
    }
    default com.areonedev.autotrack.mapping.LeadIdentity findLeadIdentity(String syncId) {
        throw new UnsupportedOperationException("Lead mapping is unavailable.");
    }
    default void bindLeadIdentity(long localId, String syncId, long cloudId, long version) {
        throw new UnsupportedOperationException("Lead mapping is unavailable.");
    }

    default java.util.List<com.areonedev.autotrack.sync.SyncEngine.Operation> syncPending(String owner) throws Exception { throw new UnsupportedOperationException(); }
    default void syncAcknowledge(String owner,com.areonedev.autotrack.sync.SyncEngine.Operation op,String response) throws Exception { throw new UnsupportedOperationException(); }
    default void syncApply(String owner,String snapshot) throws Exception { throw new UnsupportedOperationException(); }
    default java.util.List<String> syncConflicts(String owner) throws Exception { throw new UnsupportedOperationException(); }
    default void syncResolve(String owner,String operationId,boolean keepLocal) throws Exception { throw new UnsupportedOperationException(); }

    /*** Notification ***/
    String getNotificationSequential(List<Notification>notificationResult);
    ArrayList<Notification>getNotificationRandom(Notification notification);
    String insertNotification(Notification notification);
    String updateNotification(Notification notification);
    String deleteNotification(Notification notification);
    List<Notification> getAllNotifications();

    /*** Task ***/
    String getTaskSequential(List<Task>taskResult);
    ArrayList<Task>getTaskRandom(Task task);
    String insertTask(Task task);
    String updateTask(Task task);
    String deleteTask(Task task);

    /*** Vehicle ***/
    String getVehicleSequential(List<Vehicle>vehicleResult);
    ArrayList<Vehicle>getVehicleRandom(Vehicle vehicle);
    String insertVehicle(Vehicle vehicle);
    String updateVehicle(Vehicle vehicle);
    String deleteVehicle(Vehicle vehicle);

    List<String> getFilteredColumnValues(String targetColumn, String selection, String[] selectionArgs);
    List<String> getUniqueColumnValues(String columnName);

}
