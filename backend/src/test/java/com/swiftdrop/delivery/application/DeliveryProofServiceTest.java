package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.domain.ActorRole;
import com.swiftdrop.delivery.domain.DeliveryProof;
import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelAddress;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.pricing.api.ParcelSize;

class DeliveryProofServiceTest {

    private static final UUID PARCEL_ID = UUID.fromString("5a0e0000-0000-0000-0000-000000000001");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID COURIER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};

    private ParcelRepository parcels;
    private DeliveryProofRepository proofs;
    private DeliveryProofService service;
    private Parcel parcel;

    @BeforeEach
    void setUp() {
        parcels = mock(ParcelRepository.class);
        proofs = mock(DeliveryProofRepository.class);
        service = new DeliveryProofService(parcels, proofs, Clock.fixed(NOW, ZoneOffset.UTC));
        ParcelAddress address = new ParcelAddress("Nimal Perera", "+94771234567", "12 Temple Rd", null, "Colombo",
                UUID.randomUUID(), null);
        parcel = Parcel.forCustomer("SD7K3M9Q2XWB", OWNER_ID, address, address, ParcelSize.SMALL, 1000, null,
                new Parcel.Fee(new BigDecimal("650.00"), "LKR", UUID.randomUUID()), NOW.minusSeconds(3600));
        ReflectionTestUtils.setField(parcel, "id", PARCEL_ID);
        parcel.assignCourier(COURIER_ID, NOW.minusSeconds(1800));
        ReflectionTestUtils.setField(parcel, "status", ParcelStatus.OUT_FOR_DELIVERY);
        when(parcels.findByIdForUpdate(PARCEL_ID)).thenReturn(Optional.of(parcel));
        when(parcels.findById(PARCEL_ID)).thenReturn(Optional.of(parcel));
        when(proofs.findByParcelId(PARCEL_ID)).thenReturn(Optional.empty());
        when(proofs.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void assignedCourierCapturesProofWithDetectedPhotoType() {
        DeliveryProof proof = service.capture(PARCEL_ID, Actor.courier(COURIER_ID), " Kumari Silva ", JPEG);

        assertThat(proof.getRecipientName()).isEqualTo("Kumari Silva");
        assertThat(proof.getPhotoContentType()).isEqualTo("image/jpeg");
        assertThat(proof.getCapturedAt()).isEqualTo(NOW);
        verify(parcels).findByIdForUpdate(PARCEL_ID);
        verify(proofs).save(proof);
    }

    @Test
    void proofWithoutPhotoIsAccepted() {
        assertThat(service.capture(PARCEL_ID, Actor.courier(COURIER_ID), "Kumari", null).hasPhoto()).isFalse();
        assertThat(service.capture(PARCEL_ID, Actor.courier(COURIER_ID), "Kumari", new byte[0]).hasPhoto()).isFalse();
    }

    @Test
    void secondUploadReplacesTheFirst() {
        DeliveryProof existing = new DeliveryProof(PARCEL_ID, "Someone", null, null, COURIER_ID, NOW.minusSeconds(60));
        when(proofs.findByParcelId(PARCEL_ID)).thenReturn(Optional.of(existing));

        DeliveryProof proof = service.capture(PARCEL_ID, Actor.courier(COURIER_ID), "Kumari Silva", JPEG);

        assertThat(proof).isSameAs(existing);
        assertThat(existing.getRecipientName()).isEqualTo("Kumari Silva");
        assertThat(existing.hasPhoto()).isTrue();
        verify(proofs, never()).save(any());
    }

    @Test
    void anotherCourierGetsNotFound() {
        assertThatThrownBy(() -> service.capture(PARCEL_ID, Actor.courier(UUID.randomUUID()), "Kumari", JPEG))
                .isInstanceOf(ParcelNotFoundException.class);
        verify(proofs, never()).save(any());
    }

    @Test
    void proofOnlyWhileOutForDelivery() {
        for (ParcelStatus status : new ParcelStatus[] {ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT,
            ParcelStatus.DELIVERED, ParcelStatus.FAILED}) {
            ReflectionTestUtils.setField(parcel, "status", status);
            assertThatThrownBy(() -> service.capture(PARCEL_ID, Actor.courier(COURIER_ID), "Kumari", JPEG))
                    .isInstanceOf(ParcelConflictException.class);
        }
        verify(proofs, never()).save(any());
    }

    @Test
    void nonImageBytesAreRejectedWhateverTheyClaimToBe() {
        byte[] html = "<html><script>alert(1)</script>".getBytes();

        assertThatThrownBy(() -> service.capture(PARCEL_ID, Actor.courier(COURIER_ID), "Kumari", html))
                .isInstanceOf(IllegalArgumentException.class);
        verify(proofs, never()).save(any());
    }

    @Test
    void photoIsReadableByOwnerAssignedCourierAndAdminOnly() {
        DeliveryProof stored = new DeliveryProof(PARCEL_ID, "Kumari", JPEG, "image/jpeg", COURIER_ID, NOW);
        when(proofs.findByParcelId(PARCEL_ID)).thenReturn(Optional.of(stored));

        assertThat(service.photo(PARCEL_ID, Viewer.of(OWNER_ID, ActorRole.CUSTOMER))).get()
                .satisfies(photo -> assertThat(photo.contentType()).isEqualTo("image/jpeg"));
        assertThat(service.photo(PARCEL_ID, Viewer.of(COURIER_ID, ActorRole.COURIER))).isPresent();
        assertThat(service.photo(PARCEL_ID, Viewer.of(UUID.randomUUID(), ActorRole.DISPATCHER_ADMIN))).isPresent();
        assertThatThrownBy(() -> service.photo(PARCEL_ID, Viewer.of(UUID.randomUUID(), ActorRole.CUSTOMER)))
                .isInstanceOf(ParcelNotFoundException.class);
        assertThatThrownBy(() -> service.photo(PARCEL_ID, Viewer.of(UUID.randomUUID(), ActorRole.COURIER)))
                .isInstanceOf(ParcelNotFoundException.class);
    }

    @Test
    void proofWithoutPhotoHasNoPhotoToRead() {
        when(proofs.findByParcelId(PARCEL_ID))
                .thenReturn(Optional.of(new DeliveryProof(PARCEL_ID, "Kumari", null, null, COURIER_ID, NOW)));

        assertThat(service.photo(PARCEL_ID, Viewer.of(OWNER_ID, ActorRole.CUSTOMER))).isEmpty();
    }
}
