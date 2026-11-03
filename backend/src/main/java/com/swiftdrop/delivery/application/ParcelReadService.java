package com.swiftdrop.delivery.application;

import java.util.UUID;
import java.util.function.Predicate;

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
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelRepository;
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

    public ParcelReadService(ParcelRepository parcels, ParcelStatusHistoryRepository history,
            DeliveryProofRepository proofs) {
        this.parcels = parcels;
        this.history = history;
        this.proofs = proofs;
    }

    /**
     * Customers get their own parcels; dispatchers/admins get all. Newest first.
     *
     * @param status optional filter
     * @throws IllegalArgumentException for viewers who are neither customers nor admins
     */
    public Page<Parcel> list(Viewer viewer, ParcelStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, NEWEST_FIRST);
        if (viewer.isAdmin()) {
            return status == null ? parcels.findAll(pageable) : parcels.findByStatus(status, pageable);
        }
        if (viewer.roles().contains(ActorRole.CUSTOMER)) {
            return status == null
                    ? parcels.findByCustomerId(viewer.userId(), pageable)
                    : parcels.findByCustomerIdAndStatus(viewer.userId(), status, pageable);
        }
        throw new IllegalArgumentException("Only customers and dispatchers/admins list parcels");
    }

    /** Detail with timeline for the owner, the assigned courier or an admin. */
    public ParcelView detail(UUID parcelId, Viewer viewer) {
        Parcel parcel = visibleParcel(parcelId, viewer::canView);
        return new ParcelView(parcel, history.findByParcelIdOrderBySequenceAsc(parcelId), viewer.canSeeNotes(parcel),
                null);
    }

    /**
     * Receipt for the owner or an admin (ADR 0006 §6): a booking confirmation until delivery, then
     * with the recipient's name.
     */
    public ParcelView receipt(UUID parcelId, Viewer viewer) {
        Parcel parcel = visibleParcel(parcelId, viewer::canSeeReceipt);
        String recipientName = parcel.getStatus() == ParcelStatus.DELIVERED
                ? proofs.findRecipientNameByParcelId(parcelId).orElse(null)
                : null;
        return new ParcelView(parcel, history.findByParcelIdOrderBySequenceAsc(parcelId), false, recipientName);
    }

    private Parcel visibleParcel(UUID parcelId, Predicate<Parcel> allowed) {
        return parcels.findById(parcelId).filter(allowed).orElseThrow(ParcelNotFoundException::new);
    }
}
