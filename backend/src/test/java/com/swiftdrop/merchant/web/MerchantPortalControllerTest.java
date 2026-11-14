package com.swiftdrop.merchant.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.delivery.api.PageRequest;
import com.swiftdrop.delivery.api.PageResult;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelSummary;
import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.merchant.api.MerchantOrderDetailView;
import com.swiftdrop.merchant.application.MerchantPortalReadService;
import com.swiftdrop.merchant.application.PortalOrderNotFoundException;
import com.swiftdrop.merchant.domain.Merchant;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.MerchantStatusHistoryRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpointRepository;
import com.swiftdrop.pricing.api.ParcelSize;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.ZoneRepository;
import com.swiftdrop.test.security.TestPrincipal;

/** Phase 5 read-side endpoints: access rules, merchant scoping from the principal, 404 isolation and DTO shape. */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
@MockitoBean(types = {com.swiftdrop.delivery.domain.ParcelAssignmentRepository.class,
        com.swiftdrop.delivery.domain.HubRepository.class,
        com.swiftdrop.merchant.domain.MerchantApiKeyRepository.class,
        com.swiftdrop.merchant.domain.MerchantEventStateRepository.class,
        com.swiftdrop.merchant.domain.MerchantProcessedEventRepository.class,
        com.swiftdrop.merchant.domain.MerchantPendingEventRepository.class,
        com.swiftdrop.merchant.domain.MerchantEventAnomalyRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookDeliveryRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookAttemptRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookRedeliveryRepository.class,
        com.swiftdrop.notification.domain.NotificationRecordRepository.class,
        com.swiftdrop.assistant.application.AssistantService.class,
        com.swiftdrop.assistant.application.LlmClient.class})
class MerchantPortalControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MerchantPortalReadService readService;

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

    private final UUID userId = UUID.randomUUID();
    private final UUID merchantId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchant = new Merchant(userId, "Apex Store", "Retail", "BR-1001",
                "+94771234567", "No 10, Main St", "Apex Retailers Ltd", "https://apex.com");
        ReflectionTestUtils.setField(merchant, "id", merchantId);
        merchant.approve(UUID.randomUUID());
        when(merchants.findByUserId(userId)).thenReturn(Optional.of(merchant));
    }

    private ParcelSummary parcel(ParcelStatus status) {
        return new ParcelSummary(orderId, "SD7K3M9Q2XWB", status, ParcelSize.MEDIUM, 1200, ParcelSource.MERCHANT,
                null, merchantId, "ORD-10045", null, Instant.parse("2026-11-14T10:00:00Z"));
    }

    @Test
    @DisplayName("GET /me/dashboard returns counts per status and recent orders for the principal's merchant")
    void dashboard() throws Exception {
        Map<ParcelStatus, Long> counts = new EnumMap<>(ParcelStatus.class);
        for (ParcelStatus s : ParcelStatus.values()) {
            counts.put(s, 0L);
        }
        counts.put(ParcelStatus.IN_TRANSIT, 2L);
        counts.put(ParcelStatus.DELIVERED, 3L);
        when(readService.dashboard(merchantId)).thenReturn(
                new MerchantPortalReadService.Dashboard(counts, 5, 2, List.of(parcel(ParcelStatus.IN_TRANSIT))));

        mvc.perform(get("/api/v1/merchants/me/dashboard").with(TestPrincipal.merchant(userId).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(5))
                .andExpect(jsonPath("$.activeOrders").value(2))
                .andExpect(jsonPath("$.deliveredOrders").value(3))
                .andExpect(jsonPath("$.countsByStatus.IN_TRANSIT").value(2))
                .andExpect(jsonPath("$.countsByStatus.CREATED").value(0))
                .andExpect(jsonPath("$.recentOrders[0].trackingNumber").value("SD7K3M9Q2XWB"))
                .andExpect(jsonPath("$.recentOrders[0].merchantOrderReference").value("ORD-10045"))
                .andExpect(jsonPath("$.recentOrders[0].courierAssigned").value(false))
                .andExpect(jsonPath("$.recentOrders[0].merchantId").doesNotExist());
    }

    @Test
    @DisplayName("GET /me/orders passes the status filter and clamped paging, scoped to the principal's merchant")
    void orders() throws Exception {
        when(readService.orders(eq(merchantId), eq(ParcelStatus.DELIVERED), any(PageRequest.class)))
                .thenReturn(PageResult.of(List.of(parcel(ParcelStatus.DELIVERED)), new PageRequest(1, 100), 101));

        mvc.perform(get("/api/v1/merchants/me/orders").param("status", "DELIVERED").param("page", "1")
                        .param("size", "500").with(TestPrincipal.merchant(userId).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("DELIVERED"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.totalItems").value(101))
                .andExpect(jsonPath("$.totalPages").value(2));

        verify(readService).orders(merchantId, ParcelStatus.DELIVERED, new PageRequest(1, 100));
    }

    @Test
    @DisplayName("GET /me/orders without a status lists every status")
    void ordersWithoutFilter() throws Exception {
        when(readService.orders(eq(merchantId), isNull(), any(PageRequest.class)))
                .thenReturn(PageResult.of(List.of(), new PageRequest(0, 20), 0));

        mvc.perform(get("/api/v1/merchants/me/orders").with(TestPrincipal.merchant(userId).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    @DisplayName("GET /me/orders/{id} returns the order with its status timeline")
    void orderDetail() throws Exception {
        when(readService.order(merchantId, orderId)).thenReturn(new MerchantOrderDetailView(orderId, "SD7K3M9Q2XWB",
                "ORD-10045", ParcelStatus.AT_HUB, "MEDIUM", 1200, "Colombo", "Kandy", true,
                Instant.parse("2026-11-14T10:00:00Z"),
                List.of(new MerchantOrderDetailView.StatusEvent(ParcelStatus.CREATED,
                                Instant.parse("2026-11-14T10:00:00Z"), null, null),
                        new MerchantOrderDetailView.StatusEvent(ParcelStatus.AT_HUB,
                                Instant.parse("2026-11-14T12:00:00Z"), "Colombo Hub", null))));

        mvc.perform(get("/api/v1/merchants/me/orders/{id}", orderId)
                        .with(TestPrincipal.merchant(userId).postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.pickupCity").value("Colombo"))
                .andExpect(jsonPath("$.courierAssigned").value(true))
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.history[1].status").value("AT_HUB"))
                .andExpect(jsonPath("$.history[1].hubName").value("Colombo Hub"));
    }

    @Test
    @DisplayName("GET /me/orders/{id} answers 404, not 403, for another merchant's or an unknown order")
    void orderDetailNotFound() throws Exception {
        when(readService.order(merchantId, orderId)).thenThrow(new PortalOrderNotFoundException());

        mvc.perform(get("/api/v1/merchants/me/orders/{id}", orderId)
                        .with(TestPrincipal.merchant(userId).postProcessor()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("Order not found"));
    }

    @Test
    @DisplayName("Read views are refused while the merchant is not approved")
    void pendingMerchantForbidden() throws Exception {
        Merchant pending = new Merchant(userId, "Apex Store", "Retail", "BR-1001",
                "+94771234567", "No 10, Main St", "Apex Retailers Ltd", "https://apex.com");
        when(merchants.findByUserId(userId)).thenReturn(Optional.of(pending));

        mvc.perform(get("/api/v1/merchants/me/dashboard").with(TestPrincipal.merchant(userId).postProcessor()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(readService);
    }

    @Test
    @DisplayName("Read views are refused for other roles and anonymous callers")
    void otherRolesRefused() throws Exception {
        mvc.perform(get("/api/v1/merchants/me/orders").with(TestPrincipal.customer().postProcessor()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/merchants/me/orders"))
                .andExpect(status().isUnauthorized());
        verify(readService, never()).orders(any(), any(), any());
    }
}
