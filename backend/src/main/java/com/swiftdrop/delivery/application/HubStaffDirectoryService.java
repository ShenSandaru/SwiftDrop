package com.swiftdrop.delivery.application;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.HubStaffDirectory;
import com.swiftdrop.delivery.api.StaffScopeView;
import com.swiftdrop.delivery.domain.Hub;
import com.swiftdrop.delivery.domain.HubRepository;
import com.swiftdrop.delivery.domain.HubScope;
import com.swiftdrop.delivery.domain.HubStaff;
import com.swiftdrop.delivery.domain.HubStaffRepository;

@Service
@Transactional(readOnly = true)
class HubStaffDirectoryService implements HubStaffDirectory {

    private final StaffScopes scopes;
    private final HubStaffRepository staff;
    private final HubRepository hubs;

    HubStaffDirectoryService(StaffScopes scopes, HubStaffRepository staff, HubRepository hubs) {
        this.scopes = scopes;
        this.staff = staff;
        this.hubs = hubs;
    }

    @Override
    public StaffScopeView scopeOf(UUID userId) {
        HubScope scope = scopes.scopeOf(Objects.requireNonNull(userId, "userId"));
        if (scope.everywhere()) {
            return new StaffScopeView(true, List.of());
        }
        return new StaffScopeView(false, hubs.findAllById(scope.hubIds()).stream()
                .sorted(Comparator.comparing(Hub::getName, String.CASE_INSENSITIVE_ORDER))
                .map(hub -> new StaffScopeView.HubRef(hub.getId(), hub.getCode(), hub.getName()))
                .toList());
    }

    @Override
    public boolean worksEverywhere(UUID userId) {
        return scopes.scopeOf(Objects.requireNonNull(userId, "userId")).everywhere();
    }

    @Override
    public Set<UUID> colleaguesOf(UUID userId) {
        HubScope scope = scopes.scopeOf(Objects.requireNonNull(userId, "userId"));
        if (scope.isEmpty() || scope.everywhere()) {
            return Set.of();
        }
        return staff.findByHubIdIn(scope.hubIds()).stream().map(HubStaff::getUserId).collect(Collectors.toSet());
    }
}
