package com.swiftdrop.delivery.application;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.swiftdrop.delivery.domain.HubScope;
import com.swiftdrop.delivery.domain.HubStaff;
import com.swiftdrop.delivery.domain.HubStaffRepository;
import com.swiftdrop.identity.api.UserDirectory;

/**
 * Works out which hubs a staff member works for (ADR 0012 §2): admins work everywhere, everyone else
 * for the hubs an admin gave them, and without any they see nothing.
 */
@Component
class StaffScopes {

    private static final String ADMIN = "ADMIN";

    private final HubStaffRepository staff;
    private final UserDirectory users;

    StaffScopes(HubStaffRepository staff, UserDirectory users) {
        this.staff = staff;
        this.users = users;
    }

    HubScope scopeOf(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        if (users.findUser(userId).map(user -> user.hasRole(ADMIN)).orElse(false)) {
            return HubScope.EVERYWHERE;
        }
        return scopeFrom(staff.findByUserId(userId));
    }

    static HubScope scopeFrom(List<HubStaff> rows) {
        Set<UUID> hubs = rows.stream().map(HubStaff::getHubId).collect(Collectors.toSet());
        return HubScope.of(hubs);
    }
}
