package com.areonedev.autotrack.mapping;

/** Numeric database keys are local to their database; syncId is the cross-device identity. */
public final class LeadIdentity {
    public final String ownerId, syncId;
    public final long localId, baseVersion;
    public final Long cloudId;
    public final boolean fieldsKnown;
    public LeadIdentity(String ownerId, long localId, String syncId, Long cloudId, long baseVersion, boolean fieldsKnown) {
        this.ownerId = ownerId; this.localId = localId; this.syncId = syncId;
        this.cloudId = cloudId; this.baseVersion = baseVersion; this.fieldsKnown = fieldsKnown;
    }
}
