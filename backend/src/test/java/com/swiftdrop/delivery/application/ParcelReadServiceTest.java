package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.domain.ActorRole;
import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAddress;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryEntry;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.pricing.api.ParcelSize;

/** Who may see which parcel: everyone else gets "not found" (contract §6). */
class ParcelReadServiceTest {

    private static final UUID PARCEL_ID = UUID.fromString("5a0e0000-0000-0000-0000-000000000001");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID COURIER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final Instant NOW = Instant.parse("2026-11-03T10:15:30Z");

    private ParcelRepository parcels;
    private ParcelStatusHistoryRepository history;
    private DeliveryProofRepository proofs;
    private ParcelReadService service;
    private Parcel parcel;

    @BeforeEach
    void setUp() {
        parcels = mock(ParcelRepository.class);
        history = mock(ParcelStatusHistoryRepository.class);
        proofs = mock(DeliveryProofRepository.class);
        service = new ParcelReadService(parcels, history, proofs);

        ParcelAddress address = new ParcelAddress("Nimal Perera", "+94771234567", "12 Temple Rd", null, "Colombo",
                UUID.randomUUID(), null);
        parcel = Parcel.forCustomer("SD7K3M9Q2XWB", OWNER_ID, address, address, ParcelSize.SMALL, 1000, null,
                new Parcel.Fee(new BigDecimal("650.00"), "LKR", UUID.randomUUID()), NOW);
        ReflectionTestUtils.setField(parcel, "id", PARCEL_ID);
        ReflectionTestUtils.setField(parcel, "assignedCourierId", COURIER_ID);
        ReflectionTestUtils.setField(parcel, "assignedAt", NOW);
        when(parcels.findById(PARCEL_ID)).thenReturn(Optional.of(parcel));
        when(history.findByParcelIdOrderBySequenceAsc(PARCEL_ID)).thenReturn(List.of(new ParcelStatusHistoryEntry(
                PARCEL_ID, 1, null, ParcelStatus.CREATED, OWNER_ID, ActorRole.CUSTOMER, null, null, UUID.randomUUID(),
                NOW)));
    }

    @Test
    void ownerAssignedCourierAndAdminSeeDetail() {
        assertThat(service.detail(PARCEL_ID, Viewer.of(OWNER_ID, ActorRole.CUSTOMER)).history()).hasSize(1);
        assertThat(service.detail(PARCEL_ID, Viewer.of(COURIER_ID, ActorRole.COURIER)).parcel()).isSameAs(parcel);
        assertThat(service.detail(PARCEL_ID, Viewer.of(UUID.randomUUID(), ActorRole.DISPATCHER_ADMIN)).parcel())
                .isSameAs(parcel);
    }

    @Test
    void otherCustomerOtherCourierAndMerchantGetNotFound() {
        for (Viewer stranger : List.of(
                Viewer.of(UUID.randomUUID(), ActorRole.CUSTOMER),
                Viewer.of(UUID.randomUUID(), ActorRole.COURIER),
                Viewer.of(UUID.randomUUID(), ActorRole.MERCHANT),
                // The owner's ID but acting only as a courier is not the owner
                Viewer.of(OWNER_ID, ActorRole.COURIER))) {
            assertThatThrownBy(() -> service.detail(PARCEL_ID, stranger)).isInstanceOf(ParcelNotFoundException.class);
        }
    }

    @Test
    void unknownParcelIsNotFound() {
        assertThatThrownBy(() -> service.detail(UUID.randomUUID(), Viewer.of(OWNER_ID, ActorRole.CUSTOMER)))
                .isInstanceOf(ParcelNotFoundException.class);
    }

    @Test
    void notesAreVisibleToCourierAndAdminButNotToTheCustomer() {
        assertThat(service.detail(PARCEL_ID, Viewer.of(OWNER_ID, ActorRole.CUSTOMER)).notesVisible()).isFalse();
        assertThat(service.detail(PARCEL_ID, Viewer.of(COURIER_ID, ActorRole.COURIER)).notesVisible()).isTrue();
        assertThat(service.detail(PARCEL_ID, Viewer.of(UUID.randomUUID(), ActorRole.DISPATCHER_ADMIN)).notesVisible())
                .isTrue();
    }

    @Test
    void receiptIsForOwnerAndAdminOnly() {
        assertThat(service.receipt(PARCEL_ID, Viewer.of(OWNER_ID, ActorRole.CUSTOMER)).parcel()).isSameAs(parcel);
        assertThat(service.receipt(PARCEL_ID, Viewer.of(UUID.randomUUID(), ActorRole.DISPATCHER_ADMIN)).parcel())
                .isSameAs(parcel);
        assertThatThrownBy(() -> service.receipt(PARCEL_ID, Viewer.of(COURIER_ID, ActorRole.COURIER)))
                .isInstanceOf(ParcelNotFoundException.class);
    }

    @Test
    void recipientNameIsReadOnlyForDeliveredParcels() {
        assertThat(service.receipt(PARCEL_ID, Viewer.of(OWNER_ID, ActorRole.CUSTOMER)).recipientName()).isNull();
        verify(proofs, never()).findRecipientNameByParcelId(any());

        ReflectionTestUtils.setField(parcel, "status", ParcelStatus.DELIVERED);
        when(proofs.findRecipientNameByParcelId(PARCEL_ID)).thenReturn(Optional.of("Kumari Silva"));
        assertThat(service.receipt(PARCEL_ID, Viewer.of(OWNER_ID, ActorRole.CUSTOMER)).recipientName())
                .isEqualTo("Kumari Silva");
    }

    @Test
    void customersListTheirOwnParcelsNewestFirst() {
        Page<Parcel> page = new PageImpl<>(List.of(parcel));
        when(parcels.findByCustomerId(eq(OWNER_ID), any())).thenReturn(page);

        assertThat(service.list(Viewer.of(OWNER_ID, ActorRole.CUSTOMER), null, 2, 10)).isSameAs(page);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(parcels).findByCustomerId(eq(OWNER_ID), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
        verify(parcels, never()).findAll(any(Pageable.class));
    }

    @Test
    void statusFilterIsApplied() {
        when(parcels.findByCustomerIdAndStatus(eq(OWNER_ID), eq(ParcelStatus.DELIVERED), any()))
                .thenReturn(Page.empty());
        when(parcels.findByStatus(eq(ParcelStatus.FAILED), any())).thenReturn(Page.empty());

        service.list(Viewer.of(OWNER_ID, ActorRole.CUSTOMER), ParcelStatus.DELIVERED, 0, 20);
        service.list(Viewer.of(UUID.randomUUID(), ActorRole.DISPATCHER_ADMIN), ParcelStatus.FAILED, 0, 20);

        verify(parcels).findByCustomerIdAndStatus(eq(OWNER_ID), eq(ParcelStatus.DELIVERED), any());
        verify(parcels).findByStatus(eq(ParcelStatus.FAILED), any());
    }

    @Test
    void adminsListAllParcels() {
        when(parcels.findAll(any(Pageable.class))).thenReturn(Page.empty());

        service.list(Viewer.of(UUID.randomUUID(), ActorRole.DISPATCHER_ADMIN), null, 0, 20);

        verify(parcels).findAll(any(Pageable.class));
    }

    @Test
    void couriersCannotUseTheGeneralList() {
        assertThatThrownBy(() -> service.list(Viewer.of(COURIER_ID, ActorRole.COURIER), null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
