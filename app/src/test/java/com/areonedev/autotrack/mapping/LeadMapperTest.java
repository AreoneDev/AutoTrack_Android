package com.areonedev.autotrack.mapping;

import com.areonedev.autotrack.objects.Lead;
import com.areonedev.autotrack.objects.Vehicle;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class LeadMapperTest {
    private final TimeZone zone = TimeZone.getTimeZone("America/Toronto");
    private final LeadMapper mapper = new LeadMapper(zone);

    @Test public void mapsEveryCustomerFieldWithoutExportingTheLocalPrimaryKey() {
        Lead lead = new Lead();
        lead.setLeadID(42);
        lead.setLeadFirstName("Synthetic"); lead.setLeadLastName("客户");
        lead.setLeadPhoneNumber("+1-555-0100"); lead.setLeadEmail("synthetic@example.invalid");
        lead.setLeadDivision("Retail"); lead.setLeadAddress("1 Example Street");
        lead.setLeadCity("Toronto"); lead.setLeadProvince("ON");
        lead.setLeadCountry("Canada"); lead.setLeadPostalCode("A1A 1A1");
        lead.setLeadBudget(12345.67); lead.setLeadStage("CONTACTED");
        lead.setLeadScore(12.5); lead.setLeadNotes("Synthetic notes"); lead.setLeadStatus(false);
        lead.setLeadFollowUpDate(new Date(1793505600000L));
        lead.setLeadCreatedAt(new Date(1234567890123L));
        lead.setLastInteractionDate(new Date(1234567890456L)); lead.setLastInteractionBy("SALES");
        Map<String, Object> row = mapper.toRow(lead);
        assertFalse(row.containsKey("lead_id"));
        assertEquals(new HashSet<>(Arrays.asList("first_name", "last_name", "phone", "lead_email",
                "lead_division", "lead_address", "lead_city", "lead_province", "lead_country",
                "lead_postal_code", "budget", "stage", "follow_up_date", "score", "notes", "created_at",
                "last_interaction_date", "last_interaction_by", "status")), row.keySet());
        Lead copy = mapper.fromRow(row, 99);
        assertEquals(99, copy.getLeadID());
        assertEquals(lead.getLeadFirstName(), copy.getLeadFirstName());
        assertEquals(lead.getLeadLastName(), copy.getLeadLastName());
        assertEquals(lead.getLeadPhoneNumber(), copy.getLeadPhoneNumber());
        assertEquals(lead.getLeadEmail(), copy.getLeadEmail());
        assertEquals(lead.getLeadDivision(), copy.getLeadDivision());
        assertEquals(lead.getLeadAddress(), copy.getLeadAddress());
        assertEquals(lead.getLeadCity(), copy.getLeadCity());
        assertEquals(lead.getLeadProvince(), copy.getLeadProvince());
        assertEquals(lead.getLeadCountry(), copy.getLeadCountry());
        assertEquals(lead.getLeadPostalCode(), copy.getLeadPostalCode());
        assertEquals(lead.getLeadBudget(), copy.getLeadBudget(), 0);
        assertEquals(lead.getLeadStage(), copy.getLeadStage());
        assertEquals(lead.getLeadScore(), copy.getLeadScore(), 0);
        assertEquals(lead.getLeadNotes(), copy.getLeadNotes());
        assertFalse(copy.getLeadStatus());
        assertEquals(lead.getLeadCreatedAt(), copy.getLeadCreatedAt());
        assertEquals(lead.getLastInteractionDate(), copy.getLastInteractionDate());
        assertEquals("SALES", copy.getLastInteractionBy());
        assertEquals(row.get("follow_up_date"), mapper.toRow(copy).get("follow_up_date"));
    }

    @Test public void emptyDatesRemainEmptyAndMissingFieldsDoNotBecomePatchDefaults() {
        Lead lead = mapper.fromRow(Collections.singletonMap("first_name", "Synthetic"), 7);
        assertNull(lead.getLeadFollowUpDate()); assertNull(lead.getLeadCreatedAt());
        assertNull(lead.getLastInteractionDate());
        Map<String, Object> row = mapper.toRow(lead);
        assertNull(row.get("follow_up_date")); assertNull(row.get("created_at"));
        assertFalse(mapper.legacyPatch(row).containsKey("score"));
        assertFalse(mapper.legacyPatch(row).containsKey("last_interaction_date"));
        assertFalse(mapper.legacyPatch(row).containsKey("last_interaction_by"));
    }

    @Test public void calendarDaysSurviveDstAndInstantOffsetsAreHonored() {
        for (String day : new String[]{"2026-03-08", "2026-11-01", "2024-02-29"}) {
            Map<String, Object> row = new HashMap<>();
            row.put("follow_up_date", day);
            row.put("created_at", "2026-11-01T01:30:00-04:00");
            Lead lead = mapper.fromRow(row, 1);
            Map<String, Object> copy = mapper.toRow(lead);
            assertEquals(day, copy.get("follow_up_date"));
            assertEquals("2026-11-01T05:30:00.000Z", copy.get("created_at"));
        }
    }

    @Test public void rejectsInvalidDatesRatherThanNormalizingThem() {
        for (String invalid : new String[]{"2026-02-29", "2026-13-01", "2026-01-01extra"}) {
            try { mapper.fromRow(Collections.singletonMap("follow_up_date", invalid), 1); fail(invalid); }
            catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void vehicleSnapshotsPreserveAllAttributesAndNeverReuseCatalogIds() {
        Vehicle original = new Vehicle("Synthetic", "Model", "2024", "Custom", 25000.5,
                "Blue", true, "SYNTHETIC-VIN", "Manual");
        original.setVehicleID(42);
        Map<String, Object> row = mapper.vehicleToRow(original);
        assertFalse(row.containsKey("vehicle_id"));
        assertEquals(2024, row.get("year"));
        Vehicle copy = mapper.vehicleFromRow(row);
        assertEquals(0, copy.getVehicleID());
        assertEquals(original.getMake(), copy.getMake()); assertEquals(original.getModel(), copy.getModel());
        assertEquals(original.getYear(), copy.getYear()); assertEquals(original.getTrim(), copy.getTrim());
        assertEquals(original.getPrice(), copy.getPrice(), 0); assertEquals(original.getColor(), copy.getColor());
        assertEquals(original.isInStock(), copy.isInStock()); assertEquals(original.getVin(), copy.getVin());
        assertEquals(original.getTransmission(), copy.getTransmission());
        assertNull(mapper.vehicleToRow(null)); assertNull(mapper.vehicleFromRow(null));
    }

    @Test public void cloudPayloadRetainsTheExistingWebTimestampContract() {
        Lead lead = mapper.fromRow(Collections.singletonMap("follow_up_date", "2026-11-01"), 1);
        assertEquals("2026-11-01T04:00:00.000Z", mapper.toCloudRow(lead).get("follow_up_date"));
        lead.setLeadFollowUpDate(null); assertNull(mapper.toCloudRow(lead).get("follow_up_date"));
    }

    @Test public void acceptsPostgresMicrosecondsAtJavaDatePrecision() {
        Lead lead = mapper.fromRow(Collections.singletonMap("created_at", "2026-11-01T05:30:00.123456+00:00"), 1);
        assertEquals("2026-11-01T05:30:00.123Z", mapper.toRow(lead).get("created_at"));
    }
    @Test public void acceptsPostgresTimestampsWithoutOffsetInBusinessTimeZone() {
        Map<String,Object> row = new HashMap<>();
        row.put("follow_up_date", "2026-11-01T14:00:00");
        row.put("created_at", "2026-11-02T03:45:08.552");
        Lead lead = mapper.fromRow(row, 1);
        assertEquals("2026-11-01", mapper.toRow(lead).get("follow_up_date"));
        assertEquals("2026-11-02T08:45:08.552Z", mapper.toRow(lead).get("created_at"));
    }
}
