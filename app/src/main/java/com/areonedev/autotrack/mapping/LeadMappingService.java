package com.areonedev.autotrack.mapping;

import com.areonedev.autotrack.auth.AccountSession;
import com.areonedev.autotrack.persistence.DataAccess;
import com.areonedev.autotrack.objects.Lead;
import java.util.*;

public final class LeadMappingService {
    public static final class PreparedLead {
        public final LeadIdentity identity;
        public final Map<String, Object> fields, vehicleInterest, tradeInVehicle;
        private final long generation;
        private final LeadMappingService issuer;
        private PreparedLead(LeadMappingService issuer, LeadIdentity identity, long generation,
                Map<String, Object> fields, Map<String, Object> interest, Map<String, Object> tradeIn) {
            this.issuer = issuer; this.identity = identity; this.generation = generation;
            this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
            vehicleInterest = interest == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(interest));
            tradeInVehicle = tradeIn == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(tradeIn));
        }
    }
    private final AccountSession session;
    private final DataAccess data;
    private final LeadMapper mapper;
    /** Supply the Services account facade, never an unscoped legacy DAO. Call prepare in the background. */
    public LeadMappingService(AccountSession session, DataAccess data, TimeZone zone) {
        this.session = Objects.requireNonNull(session); this.data = Objects.requireNonNull(data);
        mapper = new LeadMapper(zone);
    }
    public PreparedLead prepare(long localId) throws Exception {
        synchronized (session) {
            AccountSession.Session current = session.restore();
            if (current == null) throw new IllegalStateException("Sign in before mapping customer data.");
            long generation = session.generation();
            Lead criteria = new Lead(); criteria.setLeadID(localId);
            List<Lead> rows = data.getLeadRandom(criteria);
            if (localId <= 0 || rows == null || rows.size() != 1 || rows.get(0).getLeadID() != localId)
                throw new IllegalArgumentException("Local lead not found.");
            Lead lead = rows.get(0);
            LeadIdentity identity = data.getLeadIdentity(localId);
            if (!current.userId.equals(identity.ownerId) || identity.localId != localId
                    || !session.isCurrent(current.userId, generation))
                throw new IllegalStateException("The account is no longer active.");
            Map<String, Object> fields = mapper.toCloudRow(lead);
            if (!identity.fieldsKnown) fields = mapper.legacyPatch(fields);
            fields.put("sync_id", identity.syncId);
            return new PreparedLead(this, identity, generation, fields,
                    mapper.vehicleToRow(lead.getLeadVehicleInterest()), mapper.vehicleToRow(lead.getTradeInVehicle()));
        }
    }
    /** Records the server identity only; queue acknowledgement and conflict resolution belong to synchronization. */
    public void acknowledge(PreparedLead prepared, long cloudId, long version) {
        synchronized (session) {
            if (prepared.issuer != this || !session.isCurrent(prepared.identity.ownerId, prepared.generation))
                throw new IllegalStateException("The account is no longer active.");
            LeadIdentity current = data.getLeadIdentity(prepared.identity.localId);
            if (!current.ownerId.equals(prepared.identity.ownerId) || !current.syncId.equals(prepared.identity.syncId))
                throw new IllegalStateException("Record identity changed.");
            data.bindLeadIdentity(current.localId, current.syncId, cloudId, version);
        }
    }
}
