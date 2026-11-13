package com.swiftdrop.dispatch.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.delivery.api.FailureReason;
import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.Staff;
import com.swiftdrop.delivery.application.ParcelHubService;
import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.ZoneRepository;
import com.swiftdrop.test.security.TestPrincipal;

/** The dispatcher's hub steps through HTTP (ADR 0009 §3, §6); delivery's operations are mocked. */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
@MockitoBean(types = {
        com.swiftdrop.delivery.domain.ParcelAssignmentRepository.class,
        com.swiftdrop.delivery.domain.HubRepository.class,
        com.swiftdrop.merchant.domain.MerchantRepository.class,
        com.swiftdrop.merchant.domain.MerchantStatusHistoryRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookEndpointRepository.class,
        com.swiftdrop.merchant.domain.MerchantApiKeyRepository.class,
        com.swiftdrop.merchant.domain.MerchantEventStateRepository.class,
        com.swiftdrop.merchant.domain.MerchantProcessedEventRepository.class,
        com.swiftdrop.merchant.domain.MerchantPendingEventRepository.class,
        com.swiftdrop.merchant.domain.MerchantEventAnomalyRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookDeliveryRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookAttemptRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookRedeliveryRepository.class
})
class DispatchHubApiTest {

    private static final UUID PARCEL = UUID.randomUUID();
    private static final String BASE = "/api/v1/dispatch/parcels/" + PARCEL;

    @Autowired
    private MockMvc mvc;

    // The concrete service: it is both delivery's ParcelHubOperations and the customer cancel endpoint's
    @MockitoBean
    private ParcelHubService hubOperations;
    @MockitoBean
    private UserRepository users;
    @MockitoBean
    private ExternalIdentityRepository identities;
    @MockitoBean
    private GoogleLoginCodeRepository googleCodes;
    @MockitoBean
    private ZoneRepository zones;
    @MockitoBean
    private PricingRuleRepository pricingRules;
    @MockitoBean
    private ParcelRepository parcels;
    @MockitoBean
    private ParcelStatusHistoryRepository parcelHistory;
    @MockitoBean
    private DeliveryProofRepository deliveryProofs;
    @MockitoBean
    private OutboxEventRepository outboxEvents;

    @Test
    void dispatcherScansSendsReturnsFailsAndCancels() throws Exception {
        UUID dispatcherId = UUID.randomUUID();
        UUID hub = UUID.randomUUID();
        TestPrincipal dispatcher = TestPrincipal.dispatcher(dispatcherId);

        mvc.perform(post(BASE + "/hub-scans").with(dispatcher.postProcessor()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"hubId\":\"" + hub + "\"}")).andExpect(status().isNoContent());
        mvc.perform(post(BASE + "/transit").with(dispatcher.postProcessor())).andExpect(status().isNoContent());
        mvc.perform(post(BASE + "/return").with(dispatcher.postProcessor())).andExpect(status().isNoContent());
        mvc.perform(post(BASE + "/failure").with(dispatcher.postProcessor()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"DAMAGED\",\"note\":\"Crushed\"}")).andExpect(status().isNoContent());
        mvc.perform(post(BASE + "/cancellation").with(dispatcher.postProcessor())).andExpect(status().isNoContent());

        Staff staff = Staff.dispatcher(dispatcherId);
        verify(hubOperations).scanAtHub(PARCEL, hub, staff);
        verify(hubOperations).sendToNextHub(PARCEL, staff);
        verify(hubOperations).startReturn(PARCEL, staff);
        verify(hubOperations).markFailed(PARCEL, FailureReason.DAMAGED, "Crushed", staff);
        verify(hubOperations).cancel(PARCEL, staff);
    }

    @Test
    void theAdminIsRecordedAsAdmin() throws Exception {
        UUID adminId = UUID.randomUUID();

        mvc.perform(post(BASE + "/transit").with(TestPrincipal.admin(adminId).postProcessor()))
                .andExpect(status().isNoContent());

        verify(hubOperations).sendToNextHub(PARCEL, Staff.admin(adminId));
    }

    @Test
    void customersAndCouriersCannotUseHubSteps() throws Exception {
        for (TestPrincipal caller : List.of(TestPrincipal.customer(), TestPrincipal.courier())) {
            mvc.perform(post(BASE + "/transit").with(caller.postProcessor())).andExpect(status().isForbidden());
        }
        mvc.perform(post(BASE + "/transit")).andExpect(status().isUnauthorized());
        verifyNoInteractions(hubOperations);
    }

    @Test
    void lifecycleRulesAnswerConflictAndUnknownParcelsNotFound() throws Exception {
        doThrow(new ParcelConflictException("The parcel is not routed to this hub")).when(hubOperations)
                .scanAtHub(eq(PARCEL), any(), any());
        doThrow(new ParcelNotFoundException()).when(hubOperations).startReturn(eq(PARCEL), any());

        mvc.perform(post(BASE + "/hub-scans").with(TestPrincipal.dispatcher().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"hubId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The parcel is not routed to this hub"));
        mvc.perform(post(BASE + "/return").with(TestPrincipal.dispatcher().postProcessor()))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingHubOrReasonIsABadRequest() throws Exception {
        mvc.perform(post(BASE + "/hub-scans").with(TestPrincipal.dispatcher().postProcessor())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/failure").with(TestPrincipal.dispatcher().postProcessor())
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"no reason\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(hubOperations);
    }
}
