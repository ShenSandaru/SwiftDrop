package com.swiftdrop.delivery.web;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.swiftdrop.delivery.application.HubStaffAdministration.StaffMember;

/**
 * A dispatcher or courier and where they work (ADR 0012 §5). A dispatcher with no hubs sees nothing;
 * the admin page lists them so the gap is visible.
 */
record StaffMemberResponse(UUID userId, String fullName, Set<String> roles, List<UUID> hubIds) {

    static StaffMemberResponse from(StaffMember member) {
        return new StaffMemberResponse(member.userId(), member.fullName(), member.roles(), member.hubIds());
    }
}
