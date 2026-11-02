package com.areonedev.autotrack.mapping;

import com.areonedev.autotrack.objects.Lead;
import com.areonedev.autotrack.objects.Vehicle;
import java.util.*;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;

/** Plaintext boundary after local decryption. Vehicle relationships are separate payloads. */
public final class LeadMapper {
    private final TimeZone zone;
    public LeadMapper(TimeZone zone) { this.zone = (TimeZone) Objects.requireNonNull(zone).clone(); }
    /** Keep the current web schema until both clients adopt a date-only database column. */
    public Map<String, Object> toCloudRow(Lead lead) {
        Map<String, Object> row = toRow(lead);
        String day = (String) row.get("follow_up_date");
        row.put("follow_up_date", day == null ? null : instant(parse(day, "yyyy-MM-dd", zone)));
        return row;
    }
    public Map<String, Object> toRow(Lead lead) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("first_name", lead.getLeadFirstName()); row.put("last_name", lead.getLeadLastName());
        row.put("phone", lead.getLeadPhoneNumber()); row.put("lead_email", lead.getLeadEmail());
        row.put("lead_division", lead.getLeadDivision()); row.put("lead_address", lead.getLeadAddress());
        row.put("lead_city", lead.getLeadCity()); row.put("lead_province", lead.getLeadProvince());
        row.put("lead_country", lead.getLeadCountry()); row.put("lead_postal_code", lead.getLeadPostalCode());
        row.put("budget", lead.getLeadBudget()); row.put("stage", lead.getLeadStage());
        row.put("follow_up_date", format(lead.getLeadFollowUpDate(), "yyyy-MM-dd", zone));
        row.put("score", lead.getLeadScore()); row.put("notes", lead.getLeadNotes());
        row.put("created_at", instant(lead.getLeadCreatedAt()));
        row.put("last_interaction_date", instant(lead.getLastInteractionDate()));
        row.put("last_interaction_by", lead.getLastInteractionBy()); row.put("status", lead.getLeadStatus());
        return row;
    }
    public Lead fromRow(Map<String, Object> row, long localId) {
        if (localId <= 0) throw new IllegalArgumentException("A local primary key is required.");
        Lead lead = new Lead(); lead.setLeadID(localId);
        lead.setLeadFirstName(text(row, "first_name")); lead.setLeadLastName(text(row, "last_name"));
        lead.setLeadPhoneNumber(text(row, "phone")); lead.setLeadEmail(text(row, "lead_email"));
        lead.setLeadDivision(text(row, "lead_division")); lead.setLeadAddress(text(row, "lead_address"));
        lead.setLeadCity(text(row, "lead_city")); lead.setLeadProvince(text(row, "lead_province"));
        lead.setLeadCountry(text(row, "lead_country")); lead.setLeadPostalCode(text(row, "lead_postal_code"));
        lead.setLeadBudget(number(row, "budget")); lead.setLeadStage(text(row, "stage"));
        lead.setLeadFollowUpDate(day(text(row, "follow_up_date")));
        lead.setLeadScore(number(row, "score")); lead.setLeadNotes(text(row, "notes"));
        lead.setLeadCreatedAt(parseInstant(text(row, "created_at"), zone));
        lead.setLastInteractionDate(parseInstant(text(row, "last_interaction_date"), zone));
        lead.setLastInteractionBy(text(row, "last_interaction_by"));
        lead.setLeadStatus(row.get("status") == null || bool(row, "status"));
        return lead;
    }
    /** Old SQLite rows never persisted these values; omit them from a cloud patch. */
    public Map<String, Object> legacyPatch(Map<String, Object> row) {
        Map<String, Object> patch = new LinkedHashMap<>(row);
        patch.remove("score"); patch.remove("last_interaction_date"); patch.remove("last_interaction_by");
        return patch;
    }
    public Map<String, Object> vehicleToRow(Vehicle v) {
        if (v == null) return null;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("make", v.getMake()); row.put("model", v.getModel());
        String year = v.getYear();
        if (year != null && !year.isEmpty() && !year.matches("[0-9]{4}"))
            throw new IllegalArgumentException("Invalid vehicle year.");
        row.put("year", year == null || year.isEmpty() ? null : Integer.valueOf(year));
        row.put("trim", v.getTrim()); row.put("price", v.getPrice()); row.put("color", v.getColor());
        row.put("in_stock", v.isInStock()); row.put("vin", v.getVin()); row.put("transmission", v.getTransmission());
        return row;
    }
    public Vehicle vehicleFromRow(Map<String, Object> row) {
        if (row == null) return null;
        String year = row.get("year") == null ? null : row.get("year").toString();
        if (year != null && !year.matches("[0-9]{4}")) throw new IllegalArgumentException("Invalid vehicle year.");
        return new Vehicle(text(row, "make"), text(row, "model"), year, text(row, "trim"),
                number(row, "price"), text(row, "color"), bool(row, "in_stock"),
                text(row, "vin"), text(row, "transmission"));
    }
    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value != null && !(value instanceof String)) throw new IllegalArgumentException("Invalid " + key);
        return (String) value;
    }
    private static double number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) return 0;
        if (!(value instanceof Number) || !Double.isFinite(((Number) value).doubleValue()))
            throw new IllegalArgumentException("Invalid " + key);
        return ((Number) value).doubleValue();
    }
    private static boolean bool(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) return false;
        if (!(value instanceof Boolean)) throw new IllegalArgumentException("Invalid " + key);
        return (Boolean) value;
    }
    private Date day(String value) {
        if (value == null || value.isEmpty()) return null;
        // Existing web rows use timestamps. Preserve their calendar day in the selected business zone.
        if (value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return parse(value, "yyyy-MM-dd", zone);
        if (value.contains("T")) {
            Date instant = parseInstant(value, zone);
            return parse(format(instant, "yyyy-MM-dd", zone), "yyyy-MM-dd", zone);
        }
        throw new IllegalArgumentException("Invalid follow-up date.");
    }
    public static String instant(Date value) { return format(value, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", TimeZone.getTimeZone("UTC")); }
    public static Date parseInstant(String value) { return parseInstant(value, TimeZone.getDefault()); }
    private static Date parseInstant(String value, TimeZone localZone) {
        if (value == null || value.isEmpty()) return null;
        if (value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,6})?")) {
            String normalized = value;
            if (value.contains(".")) {
                int start = value.indexOf('.') + 1;
                normalized = value.substring(0, start) + (value.substring(start) + "000").substring(0, 3);
            }
            return parse(normalized, value.contains(".") ? "yyyy-MM-dd'T'HH:mm:ss.SSS" : "yyyy-MM-dd'T'HH:mm:ss", localZone);
        }
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,6})?(Z|[+-][0-9]{2}:[0-9]{2})"))
            throw new IllegalArgumentException("Invalid timestamp.");
        String normalized = value;
        if (value.contains(".")) {
            int start = value.indexOf('.') + 1, end = start;
            while (Character.isDigit(value.charAt(end))) end++;
            String milliseconds = (value.substring(start, end) + "000").substring(0, 3);
            normalized = value.substring(0, start) + milliseconds + value.substring(end);
        }
        return parse(normalized, value.contains(".") ? "yyyy-MM-dd'T'HH:mm:ss.SSSXXX" : "yyyy-MM-dd'T'HH:mm:ssXXX", TimeZone.getTimeZone("UTC"));
    }
    public static Date parse(String value, String pattern, TimeZone zone) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.ROOT); f.setLenient(false); f.setTimeZone(zone);
        ParsePosition position = new ParsePosition(0); Date result = f.parse(value, position);
        if (result == null || position.getIndex() != value.length()) throw new IllegalArgumentException("Invalid date.");
        return result;
    }
    public static String format(Date value, String pattern, TimeZone zone) {
        if (value == null) return null;
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.ROOT); f.setTimeZone(zone); return f.format(value);
    }
}
