package com.swiftdrop.merchant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.merchant.api.MerchantApplicationSubmitted;
import com.swiftdrop.merchant.api.MerchantStatus;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.MerchantStatusHistory;
import com.swiftdrop.merchant.domain.MerchantStatusHistoryRepository;
import com.swiftdrop.merchant.web.MerchantApplicationRequest;
import com.swiftdrop.merchant.web.MerchantApplicationResponse;

@ExtendWith(MockitoExtension.class)
class MerchantRegistrationServiceTest {

    @Mock
    private UserDirectory userDirectory;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private MerchantStatusHistoryRepository historyRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private MerchantRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new MerchantRegistrationService(
                userDirectory, merchantRepository, historyRepository, eventPublisher);
    }

    private MerchantApplicationRequest sampleRequest() {
        return new MerchantApplicationRequest(
                "Jane Doe",
                "Jane.Doe@EXAMPLE.com",
                "SecureP@ss123",
                "Doe Store",
                "Grocery",
                "BR-998877",
                "+94771234567",
                "100 Galle Road, Colombo",
                "Family-owned grocery shop",
                "https://doestore.lk");
    }

    @Test
    @DisplayName("Happy path creates user via UserDirectory, saves merchant and history, and publishes event")
    void successfulRegistration() {
        MerchantApplicationRequest request = sampleRequest();
        UUID userId = UUID.randomUUID();

        when(merchantRepository.existsByBusinessRegistrationNo("BR-998877")).thenReturn(false);
        when(userDirectory.createMerchantUser(eq("Jane Doe"), eq("jane.doe@example.com"), eq("SecureP@ss123")))
                .thenReturn(userId);

        when(merchantRepository.saveAndFlush(any(Merchant.class))).thenAnswer(invocation -> {
            Merchant m = invocation.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(m, "id", UUID.randomUUID());
            return m;
        });

        MerchantApplicationResponse response = service.register(request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(MerchantStatus.PENDING);

        // Verify Merchant entity saved with correct attributes
        ArgumentCaptor<Merchant> merchantCaptor = ArgumentCaptor.forClass(Merchant.class);
        verify(merchantRepository).saveAndFlush(merchantCaptor.capture());
        Merchant savedMerchant = merchantCaptor.getValue();
        assertThat(savedMerchant.getUserId()).isEqualTo(userId);
        assertThat(savedMerchant.getBusinessName()).isEqualTo("Doe Store");
        assertThat(savedMerchant.getBusinessType()).isEqualTo("Grocery");
        assertThat(savedMerchant.getBusinessRegistrationNo()).isEqualTo("BR-998877");
        assertThat(savedMerchant.getContactPhone()).isEqualTo("+94771234567");
        assertThat(savedMerchant.getBusinessAddress()).isEqualTo("100 Galle Road, Colombo");
        assertThat(savedMerchant.getBusinessDescription()).isEqualTo("Family-owned grocery shop");
        assertThat(savedMerchant.getWebsiteUrl()).isEqualTo("https://doestore.lk");
        assertThat(savedMerchant.getStatus()).isEqualTo(MerchantStatus.PENDING);

        // Verify history row
        ArgumentCaptor<MerchantStatusHistory> historyCaptor = ArgumentCaptor.forClass(MerchantStatusHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        MerchantStatusHistory history = historyCaptor.getValue();
        assertThat(history.getFromStatus()).isNull();
        assertThat(history.getToStatus()).isEqualTo(MerchantStatus.PENDING);
        assertThat(history.getActorUserId()).isEqualTo(userId);
        assertThat(history.getReason()).isEqualTo("Application submitted");

        // Verify event published
        ArgumentCaptor<MerchantApplicationSubmitted> eventCaptor =
                ArgumentCaptor.forClass(MerchantApplicationSubmitted.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        MerchantApplicationSubmitted event = eventCaptor.getValue();
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.businessName()).isEqualTo("Doe Store");
        assertThat(event.email()).isEqualTo("jane.doe@example.com");
    }

    @Test
    @DisplayName("Pre-check duplicate registration number throws DuplicateRegistrationException")
    void duplicateRegistrationNumberPreCheck() {
        MerchantApplicationRequest request = sampleRequest();
        when(merchantRepository.existsByBusinessRegistrationNo("BR-998877")).thenReturn(true);

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(DuplicateRegistrationException.class)
                .hasMessage("We couldn't complete this registration.");

        verify(userDirectory, never()).createMerchantUser(any(), any(), any());
        verify(merchantRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Duplicate email during UserDirectory user creation throws DuplicateRegistrationException")
    void duplicateEmailFromUserDirectory() {
        MerchantApplicationRequest request = sampleRequest();
        when(merchantRepository.existsByBusinessRegistrationNo("BR-998877")).thenReturn(false);
        when(userDirectory.createMerchantUser(any(), any(), any()))
                .thenThrow(new RuntimeException("Email already registered"));

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(DuplicateRegistrationException.class)
                .hasMessage("We couldn't complete this registration.");

        verify(merchantRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Database unique constraint violation on save throws DuplicateRegistrationException")
    void databaseConstraintViolation() {
        MerchantApplicationRequest request = sampleRequest();
        UUID userId = UUID.randomUUID();

        when(merchantRepository.existsByBusinessRegistrationNo("BR-998877")).thenReturn(false);
        when(userDirectory.createMerchantUser(any(), any(), any())).thenReturn(userId);
        when(merchantRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("Unique constraint violation"));

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(DuplicateRegistrationException.class)
                .hasMessage("We couldn't complete this registration.");

        verify(historyRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
