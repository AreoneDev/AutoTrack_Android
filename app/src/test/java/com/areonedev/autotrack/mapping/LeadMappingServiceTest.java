package com.areonedev.autotrack.mapping;

import com.areonedev.autotrack.auth.AccountSession;
import com.areonedev.autotrack.objects.Lead;
import com.areonedev.autotrack.objects.Vehicle;
import com.areonedev.autotrack.persistence.DataAccessStub;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class LeadMappingServiceTest {
    private static final String OWNER = "00000000-0000-4000-8000-000000000001";
    private static final String SYNC = "00000000-0000-4000-8000-000000000002";
    private static class Rows extends DataAccessStub {
        final Lead lead = new Lead();
        LeadIdentity identity = new LeadIdentity(OWNER, 17, SYNC, null, 0, false);
        int bindings;
        Rows() { super("synthetic"); lead.setLeadID(17); lead.setLeadFirstName("Synthetic");
            lead.setLeadVehicleInterest(new Vehicle("Synthetic", "Example", "2024", "Custom")); }
        @Override public ArrayList<Lead> getLeadRandom(Lead criteria) { return new ArrayList<>(Collections.singletonList(lead)); }
        @Override public LeadIdentity getLeadIdentity(long id) { return identity; }
        @Override public void bindLeadIdentity(long localId, String syncId, long cloudId, long version) { bindings++; }
    }
    private AccountSession session() throws Exception {
        AccountSession account = new AccountSession(new AccountSession.Provider() {
            public AccountSession.Session signIn(String email, String password) { return new AccountSession.Session(OWNER,"synthetic-access","synthetic-refresh",10000); }
            public AccountSession.Session refresh(String token) { throw new AssertionError(); }
            public void signOut(String token) { }
        }, new AccountSession.Store() {
            public AccountSession.Session load() { return null; }
            public void save(AccountSession.Session session) { }
            public void clear() { }
        }, () -> 1000);
        account.login("synthetic@example.invalid", "synthetic-password"); return account;
    }
    @Test public void preparesStableIdentityAndLegacyPatchWithSeparateVehicleSnapshots() throws Exception {
        Rows rows = new Rows(); AccountSession session = session();
        LeadMappingService service = new LeadMappingService(session, rows, TimeZone.getTimeZone("America/Toronto"));
        LeadMappingService.PreparedLead prepared = service.prepare(17);
        assertEquals(SYNC, prepared.identity.syncId); assertEquals(OWNER, prepared.identity.ownerId);
        assertEquals(SYNC, prepared.fields.get("sync_id"));
        assertFalse(prepared.fields.containsKey("lead_id")); assertFalse(prepared.fields.containsKey("version"));
        assertFalse(prepared.fields.containsKey("score")); assertFalse(prepared.fields.containsKey("last_interaction_date"));
        assertEquals("Custom", prepared.vehicleInterest.get("trim")); assertNull(prepared.tradeInVehicle);
        service.acknowledge(prepared, 100, 1); assertEquals(1, rows.bindings);
    }
    @Test public void includesKnownFieldsAndRejectsResultsFromAnEarlierSession() throws Exception {
        Rows rows = new Rows(); rows.identity = new LeadIdentity(OWNER,17,SYNC,null,0,true);
        AccountSession session = session(); LeadMappingService service = new LeadMappingService(session, rows, TimeZone.getDefault());
        LeadMappingService.PreparedLead prepared = service.prepare(17);
        assertTrue(prepared.fields.containsKey("score"));
        session.logout(); session.login("synthetic@example.invalid", "synthetic-password");
        try { service.acknowledge(prepared, 100, 1); fail("Old session receipt accepted."); }
        catch (IllegalStateException expected) { }
        assertEquals(0, rows.bindings);
    }
    @Test public void refusesAnIdentityOwnedByAnotherAccount() throws Exception {
        Rows rows = new Rows(); rows.identity = new LeadIdentity(SYNC,17,SYNC,null,0,true);
        try { new LeadMappingService(session(), rows, TimeZone.getDefault()).prepare(17); fail("Wrong owner accepted."); }
        catch (IllegalStateException expected) { }
    }
}
