package com.swiftdrop.delivery.application;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.domain.HubRepository;
import com.swiftdrop.delivery.domain.HubStaff;
import com.swiftdrop.delivery.domain.HubStaffRepository;
import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.identity.api.UserSummary;

/**
 * Admins give dispatchers and couriers their home hubs and make dispatchers head office (ADR 0012
 * §1, §2). A person is either head office or works for hubs, never both: the later choice replaces
 * the earlier one, so the state an admin sees is the state that applies.
 */
@Service
public class HubStaffAdministration {

    private static final String DISPATCHER = "DISPATCHER";
    private static final String COURIER = "COURIER";

    private final HubStaffRepository staff;
    private final HubRepository hubs;
    private final UserDirectory users;
    private final Clock clock;

    @Autowired
    public HubStaffAdministration(HubStaffRepository staff, HubRepository hubs, UserDirectory users) {
        this(staff, hubs, users, Clock.systemUTC());
    }

    HubStaffAdministration(HubStaffRepository staff, HubRepository hubs, UserDirectory users, Clock clock) {
        this.staff = staff;
        this.hubs = hubs;
        this.users = users;
        this.clock = clock;
    }

    /** One dispatcher or courier and where they work. */
    public record StaffMember(UUID userId, String fullName, Set<String> roles, boolean headOffice,
            List<UUID> hubIds) {
    }

    /**
     * Every enabled dispatcher and courier with their hubs, by name. Someone who is neither head
     * office nor at a hub shows an empty list: a dispatcher in that state sees nothing (ADR 0012 §2).
     */
    @Transactional(readOnly = true)
    public List<StaffMember> list() {
        Map<UUID, UserSummary> people = new LinkedHashMap<>();
        users.findEnabledDispatchers().forEach(user -> people.put(user.id(), user));
        users.findEnabledCouriers().forEach(user -> people.put(user.id(), user));
        Map<UUID, List<HubStaff>> rows = staff.findAll().stream().collect(Collectors.groupingBy(HubStaff::getUserId));
        return people.values().stream()
                .map(user -> {
                    List<HubStaff> own = rows.getOrDefault(user.id(), List.of());
                    return new StaffMember(user.id(), user.fullName(), staffRoles(user),
                            own.stream().anyMatch(HubStaff::isHeadOffice),
                            own.stream().map(HubStaff::getHubId).filter(Objects::nonNull).sorted().toList());
                })
                .sorted(java.util.Comparator.comparing(StaffMember::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Gives the person a home hub. A head office dispatcher becomes a hub dispatcher. Asking again
     * for the same hub changes nothing.
     *
     * @throws ParcelNotFoundException if the hub or the user does not exist
     * @throws ParcelConflictException if the user is not a dispatcher or courier, or the hub is inactive
     */
    @Transactional
    public void addToHub(UUID hubId, UUID userId, UUID adminId) {
        Objects.requireNonNull(adminId, "adminId");
        var hub = hubs.findById(Objects.requireNonNull(hubId, "hubId")).orElseThrow(ParcelNotFoundException::new);
        if (!hub.isActive()) {
            throw new ParcelConflictException("Hub " + hub.getCode() + " is not active");
        }
        requireStaff(userId);
        if (staff.findByUserIdAndHubId(userId, hubId).isPresent()) {
            return;
        }
        staff.findByUserIdAndHubIdIsNull(userId).ifPresent(staff::delete);
        save(HubStaff.atHub(userId, hubId, adminId, now()));
    }

    /** @throws ParcelNotFoundException if the person does not work for this hub */
    @Transactional
    public void removeFromHub(UUID hubId, UUID userId) {
        staff.delete(staff.findByUserIdAndHubId(Objects.requireNonNull(userId, "userId"),
                Objects.requireNonNull(hubId, "hubId")).orElseThrow(ParcelNotFoundException::new));
    }

    /**
     * Makes a dispatcher work country-wide, replacing their hubs. Asking again changes nothing.
     *
     * @throws ParcelNotFoundException if the user does not exist
     * @throws ParcelConflictException if the user is not a dispatcher
     */
    @Transactional
    public void makeHeadOffice(UUID userId, UUID adminId) {
        Objects.requireNonNull(adminId, "adminId");
        UserSummary user = requireStaff(userId);
        if (!user.hasRole(DISPATCHER)) {
            throw new ParcelConflictException("Only a dispatcher can work for the head office");
        }
        if (staff.findByUserIdAndHubIdIsNull(userId).isPresent()) {
            return;
        }
        staff.deleteAll(staff.findByUserId(userId));
        staff.flush();
        save(HubStaff.headOffice(userId, adminId, now()));
    }

    /** The dispatcher then works for no hub until an admin gives them one. @throws ParcelNotFoundException if not head office */
    @Transactional
    public void removeFromHeadOffice(UUID userId) {
        staff.delete(staff.findByUserIdAndHubIdIsNull(Objects.requireNonNull(userId, "userId"))
                .orElseThrow(ParcelNotFoundException::new));
    }

    private UserSummary requireStaff(UUID userId) {
        UserSummary user = users.findUser(Objects.requireNonNull(userId, "userId"))
                .orElseThrow(ParcelNotFoundException::new);
        if (!user.hasRole(DISPATCHER) && !user.hasRole(COURIER)) {
            throw new ParcelConflictException("Only dispatchers and couriers can work for a hub");
        }
        return user;
    }

    private static Set<String> staffRoles(UserSummary user) {
        Set<String> roles = new TreeSet<>();
        if (user.hasRole(DISPATCHER)) {
            roles.add(DISPATCHER);
        }
        if (user.hasRole(COURIER)) {
            roles.add(COURIER);
        }
        return roles;
    }

    private void save(HubStaff row) {
        try {
            staff.saveAndFlush(row);
        } catch (DataIntegrityViolationException e) {
            // A concurrent request gave the same person the same place first
            throw new ParcelConflictException("This person already works there");
        }
    }

    private Instant now() {
        return clock.instant();
    }
}
