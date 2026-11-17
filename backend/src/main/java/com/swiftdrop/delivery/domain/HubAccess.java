package com.swiftdrop.delivery.domain;

/** What a staff member's hubs allow them to do with a parcel (ADR 0012 §3). */
public enum HubAccess {
    /** The parcel is in the responsible hub's hands: the staff member may act on it. */
    ACT,
    /** The parcel passed through or ends at one of their hubs: they may follow it, not change it. */
    READ,
    NONE;

    public boolean canAct() {
        return this == ACT;
    }

    public boolean canRead() {
        return this != NONE;
    }
}
