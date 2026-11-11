package com.swiftdrop.delivery.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.domain.ActorRole;
import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.Hub;
import com.swiftdrop.delivery.domain.HubRepository;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAssignmentRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryEntry;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;

/**
 * Parcel history, detail and receipts (CUS-4, CUS-5) with role and ownership checks. A parcel the
 * viewer may not see is reported as {@link ParcelNotFoundException}, never as forbidden, so IDs
 * cannot be probed (ADR 0006 §7).
 */
@Service
@Transactional(readOnly = true)
public class ParcelReadService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private final ParcelRepository parcels;
    private final ParcelStatusHistoryRepository history;
    private final DeliveryProofRepository proofs;
    private final HubRepository hubs;
    private final ParcelAssignmentRepository assignments;

    public ParcelReadService(ParcelRepository parcels, ParcelStatusHistoryRepository history,
            DeliveryProofRepository proofs, HubRepository hubs, ParcelAssignmentRepository assignments) {
        this.parcels = parcels;
        this.history = history;
        this.proofs = proofs;
        this.hubs = hubs;
        this.assignments = assignments;
    }

    /**
     * Customers get their own parcels; dispatchers/admins get all. Newest first.
     *
     * @param status optional filter
     * @throws IllegalArgumentException for viewers who are neither customers nor admins
     */
    public Page<Parcel> list(Viewer viewer, ParcelStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, NEWEST_FIRST);
        if (viewer.hasDispatchAccess()) {
            return status == null ? parcels.findAll(pageable) : parcels.findByStatus(status, pageable);
        }
        if (viewer.roles().contains(ActorRole.CUSTOMER)) {
            return status == null
                    ? parcels.findByCustomerId(viewer.userId(), pageable)
                    : parcels.findByCustomerIdAndStatus(viewer.userId(), status, pageable);
        }
        throw new IllegalArgumentException("Only customers and dispatchers/admins list parcels");
    }

    /** Detail with timeline for the owner, a courier with a task on it, dispatchers and admins. */
    public ParcelView detail(UUID parcelId, Viewer viewer) {
        boolean heldTask = viewer.isCourier() && assignments.existsByParcelIdAndCourierId(parcelId, viewer.userId());
        Parcel parcel = visibleParcel(parcelId, found -> viewer.canView(found, heldTask));
        List<ParcelStatusHistoryEntry> entries = history.findByParcelIdOrderBySequenceAsc(parcelId);
        return new ParcelView(parcel, entries, viewer.canSeeNotes(parcel, heldTask), null, hubNamesFor(entries));
    }

    /**
     * Receipt for the owner or an admin (ADR 0006 §6): a booking confirmation until delivery, then
     * with the name of whoever received it (the recipient, or the sender for a return).
     */
    public ParcelView receipt(UUID parcelId, Viewer viewer) {
        Parcel parcel = visibleParcel(parcelId, viewer::canSeeReceipt);
        boolean handedOver = parcel.getStatus() == ParcelStatus.DELIVERED || parcel.getStatus() == ParcelStatus.RETURNED;
        String recipientName = handedOver ? proofs.findRecipientNameByParcelId(parcelId).orElse(null) : null;
        List<ParcelStatusHistoryEntry> entries = history.findByParcelIdOrderBySequenceAsc(parcelId);
        return new ParcelView(parcel, entries, false, recipientName, hubNamesFor(entries));
    }

    private Map<String, String> hubNamesFor(List<ParcelStatusHistoryEntry> entries) {
        if (entries.stream().noneMatch(entry -> entry.getHubCode() != null)) {
            return Map.of();
        }
        return hubs.findAll().stream().collect(Collectors.toMap(Hub::getCode, Hub::getName));
    }

    private Parcel visibleParcel(UUID parcelId, Predicate<Parcel> allowed) {
        return parcels.findById(parcelId).filter(allowed).orElseThrow(ParcelNotFoundException::new);
    }
}
