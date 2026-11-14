package com.swiftdrop.merchant.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.merchant.api.MerchantStatus;
import com.swiftdrop.merchant.application.DuplicateRegistrationException;
import com.swiftdrop.merchant.application.MerchantAdminService;
import com.swiftdrop.merchant.application.MerchantApprovalService;
import com.swiftdrop.merchant.application.MerchantRegistrationService;
import com.swiftdrop.merchant.domain.IllegalStateTransitionException;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.MerchantStatusHistoryRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpointRepository;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.ZoneRepository;
import com.swiftdrop.test.security.TestPrincipal;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
@MockitoBean(types = {com.swiftdrop.delivery.domain.ParcelAssignmentRepository.class, com.swiftdrop.delivery.domain.HubRepository.class, com.swiftdrop.notification.domain.NotificationRecordRepository.class})
class MerchantControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MerchantRegistrationService registrationService;

    @MockitoBean
    private MerchantApprovalService approvalService;

    @MockitoBean
    private MerchantAdminService adminService;

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
    @MockitoBean
    private MerchantRepository merchants;
    @MockitoBean
    private MerchantStatusHistoryRepository merchantStatusHistory;
    @MockitoBean
    private MerchantWebhookEndpointRepository merchantWebhookEndpoints;
    @MockitoBean
    private com.swiftdrop.merchant.domain.MerchantApiKeyRepository merchantApiKeys;
    @MockitoBean
    private com.swiftdrop.merchant.domain.MerchantEventStateRepository merchantEventStates;
    @MockitoBean
    private com.swiftdrop.merchant.domain.MerchantProcessedEventRepository merchantProcessedEvents;
    @MockitoBean
    private com.swiftdrop.merchant.domain.MerchantPendingEventRepository merchantPendingEvents;
    @MockitoBean
    private com.swiftdrop.merchant.domain.MerchantEventAnomalyRepository merchantEventAnomalies;
    @MockitoBean
    private com.swiftdrop.merchant.domain.MerchantWebhookDeliveryRepository merchantWebhookDeliveries;
    @MockitoBean
    private com.swiftdrop.merchant.domain.MerchantWebhookAttemptRepository merchantWebhookAttempts;
    @MockitoBean
    private com.swiftdrop.merchant.domain.MerchantWebhookRedeliveryRepository merchantWebhookRedeliveries;

    @Test
    @DisplayName("POST /api/v1/merchants is public and returns 201 Created on valid submission")
    void publicRegistrationSuccess() throws Exception {
        UUID merchantId = UUID.randomUUID();
        when(registrationService.register(any(MerchantApplicationRequest.class)))
                .thenReturn(new MerchantApplicationResponse(merchantId, MerchantStatus.PENDING));

        String body = """
                {
                  "fullName": "Jane Doe",
                  "email": "jane@example.com",
                  "password": "SecurePassword123!",
                  "businessName": "Doe Groceries",
                  "businessType": "Grocery",
                  "businessRegistrationNo": "BR-12345",
                  "contactPhone": "+94771234567",
                  "businessAddress": "123 Main St, Colombo",
                  "businessDescription": "Fresh groceries and household goods",
                  "websiteUrl": "https://doegroceries.lk"
                }
                """;

        mvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/v1/merchants returns 409 Conflict Problem Details on duplicate registration")
    void duplicateRegistrationReturnsConflict() throws Exception {
        when(registrationService.register(any(MerchantApplicationRequest.class)))
                .thenThrow(new DuplicateRegistrationException("We couldn't complete this registration."));

        String body = """
                {
                  "fullName": "Jane Doe",
                  "email": "jane@example.com",
                  "password": "SecurePassword123!",
                  "businessName": "Doe Groceries",
                  "businessType": "Grocery",
                  "businessRegistrationNo": "BR-12345",
                  "contactPhone": "+94771234567",
                  "businessAddress": "123 Main St, Colombo"
                }
                """;

        mvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("We couldn't complete this registration."))
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    @DisplayName("POST /api/v1/merchants returns 400 Bad Request on invalid fields")
    void invalidInputReturnsBadRequest() throws Exception {
        String body = """
                {
                  "fullName": "",
                  "email": "not-an-email",
                  "password": "short",
                  "businessName": "",
                  "businessType": "",
                  "businessRegistrationNo": "bad!reg#",
                  "contactPhone": "invalid-phone",
                  "businessAddress": "12"
                }
                """;

        mvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidFields").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/merchants/me returns status for authenticated merchant user")
    void getMyStatusSuccess() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        MerchantStatusResponse statusResponse = new MerchantStatusResponse(
                merchantId,
                MerchantStatus.PENDING,
                "Doe Groceries",
                "Grocery",
                "BR-12345",
                "+94771234567",
                "123 Main St, Colombo",
                null,
                null,
                Instant.now(),
                null,
                null);

        when(approvalService.getMerchantStatusForUser(userId)).thenReturn(statusResponse);

        mvc.perform(get("/api/v1/merchants/me").with(TestPrincipal.merchant(userId).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.businessName").value("Doe Groceries"));
    }

    @Test
    @DisplayName("GET /api/v1/merchants/me is rejected for unauthenticated caller")
    void getMyStatusUnauthenticated() throws Exception {
        mvc.perform(get("/api/v1/merchants/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/merchants/me is forbidden for customer or courier")
    void getMyStatusForbiddenForNonMerchant() throws Exception {
        mvc.perform(get("/api/v1/merchants/me").with(TestPrincipal.customer().postProcessor()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/merchants/me").with(TestPrincipal.courier().postProcessor()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /api/v1/merchants/me updates profile for authenticated merchant")
    void updateMyProfileSuccess() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();

        MerchantStatusResponse updatedResponse = new MerchantStatusResponse(
                merchantId,
                MerchantStatus.PENDING,
                "Updated Supermarket",
                "Grocery",
                "BR-12345",
                "+94771234567",
                "999 Galle Road, Colombo",
                "Upgraded items",
                "https://updated.lk",
                Instant.now(),
                null,
                null);

        when(approvalService.updateProfile(eq(userId), any())).thenReturn(updatedResponse);

        String body = """
                {
                  "businessName": "Updated Supermarket",
                  "businessAddress": "999 Galle Road, Colombo"
                }
                """;

        mvc.perform(patch("/api/v1/merchants/me")
                        .with(TestPrincipal.merchant(userId).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Updated Supermarket"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/v1/merchants requires ADMIN")
    void listMerchantsRequiresAdmin() throws Exception {
        when(approvalService.findMerchants(any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/api/v1/merchants").with(TestPrincipal.admin().postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());

        mvc.perform(get("/api/v1/merchants").with(TestPrincipal.dispatcher().postProcessor()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/merchants").with(TestPrincipal.merchant().postProcessor()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/merchants/{id} returns full merchant details and status history for DISPATCHER_ADMIN")
    void getMerchantDetailSuccess() throws Exception {
        UUID merchantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        MerchantDetailResponse detail = new MerchantDetailResponse(
                merchantId,
                UUID.randomUUID(),
                "Acme Store",
                "Retail",
                "BR-55555",
                "+94771234567",
                "456 Main St",
                null,
                null,
                MerchantStatus.APPROVED,
                null,
                adminId,
                Instant.now(),
                Instant.now(),
                Instant.now(),
                1L,
                List.of(new MerchantDetailResponse.StatusHistoryItem(
                        UUID.randomUUID(),
                        merchantId,
                        MerchantStatus.PENDING,
                        MerchantStatus.APPROVED,
                        adminId,
                        "Approved",
                        Instant.now())));

        when(approvalService.getMerchantDetail(merchantId)).thenReturn(detail);

        mvc.perform(get("/api/v1/merchants/" + merchantId).with(TestPrincipal.dispatcherAdmin(adminId).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(merchantId.toString()))
                .andExpect(jsonPath("$.businessName").value("Acme Store"))
                .andExpect(jsonPath("$.statusHistory").isArray())
                .andExpect(jsonPath("$.statusHistory[0].toStatus").value("APPROVED"));

        mvc.perform(get("/api/v1/merchants/" + merchantId).with(TestPrincipal.merchant().postProcessor()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/merchants/{id}/approval-decisions executes decision for DISPATCHER_ADMIN")
    void decideApprovalSuccess() throws Exception {
        UUID merchantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        when(approvalService.reviewDecision(eq(merchantId), any(), any()))
                .thenReturn(new MerchantStatusResponse(
                        merchantId, MerchantStatus.APPROVED, "Store", "Retail", "BR-1",
                        "+94771234567", "Addr", null, null, Instant.now(), Instant.now(), null));

        String body = """
                {
                  "decision": "APPROVE"
                }
                """;

        mvc.perform(post("/api/v1/merchants/" + merchantId + "/approval-decisions")
                        .with(TestPrincipal.dispatcherAdmin(adminId).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("POST /api/v1/merchants/{id}/approval-decisions returns 409 Conflict on illegal state transition")
    void decideApprovalIllegalTransition() throws Exception {
        UUID merchantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        when(approvalService.reviewDecision(eq(merchantId), any(), any()))
                .thenThrow(new IllegalStateTransitionException(MerchantStatus.PENDING, MerchantStatus.SUSPENDED));

        String body = """
                {
                  "decision": "SUSPEND",
                  "reason": "Invalid"
                }
                """;

        mvc.perform(post("/api/v1/merchants/" + merchantId + "/approval-decisions")
                        .with(TestPrincipal.dispatcherAdmin(adminId).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.fromStatus").value("PENDING"))
                .andExpect(jsonPath("$.toStatus").value("SUSPENDED"));
    }

    @Test
    @DisplayName("POST /api/v1/merchants/{id}/approval requires ADMIN")
    void approvalRequiresAdmin() throws Exception {
        UUID merchantId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        when(approvalService.reviewDecision(eq(merchantId), any(), any()))
                .thenReturn(new MerchantStatusResponse(
                        merchantId, MerchantStatus.APPROVED, "Store", "Retail", "BR-1",
                        "+94771234567", "Addr", null, null, Instant.now(), Instant.now(), null));

        String body = """
                {
                  "decision": "APPROVE"
                }
                """;

        mvc.perform(post("/api/v1/merchants/" + merchantId + "/approval")
                        .with(TestPrincipal.admin(adminId).postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mvc.perform(post("/api/v1/merchants/" + merchantId + "/approval")
                        .with(TestPrincipal.dispatcher().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/merchants/" + merchantId + "/approval")
                        .with(TestPrincipal.merchant().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }
}

