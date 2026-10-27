package com.areonedev.autotrack.testing;

import com.areonedev.autotrack.objects.Lead;
import com.areonedev.autotrack.objects.Vehicle;
import java.util.Date;

/** Keeps explicit dates/stages in fixtures without depending on a removed constructor. */
public final class LeadFixtures {
    private LeadFixtures() {}

    public static Lead create(String first, String last, String phone, String email,
            String division, String address, String city, String province, String country,
            String postal, double budget, Vehicle interest, Vehicle tradeIn, String stage,
            Date followUp, String notes, Date createdAt) {
        Lead lead = new Lead(first, last, phone, email, division, address, city, province,
                country, postal, budget, interest, tradeIn, notes);
        if (stage != null) lead.setLeadStage(stage);
        if (followUp != null) lead.setLeadFollowUpDate(followUp);
        if (createdAt != null) lead.setLeadCreatedAt(createdAt);
        return lead;
    }
}
