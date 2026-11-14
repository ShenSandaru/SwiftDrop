package com.swiftdrop.delivery.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.Hub;
import com.swiftdrop.delivery.domain.HubLink;
import com.swiftdrop.delivery.domain.HubLinkRepository;
import com.swiftdrop.delivery.domain.HubRepository;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.ZoneRepository;
import com.swiftdrop.test.security.TestPrincipal;

/** The hub endpoints through HTTP (ADR 0009 §1): who may read and change hubs; the repository is mocked. */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
@MockitoBean(types = {
        com.swiftdrop.pricing.domain.DistrictRepository.class,
        com.swiftdrop.pricing.domain.PricingConfigRepository.class,
        com.swiftdrop.identity.domain.UserAddressRepository.class,
        com.swiftdrop.delivery.domain.ParcelAssignmentRepository.class,
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
        com.swiftdrop.merchant.domain.MerchantWebhookRedeliveryRepository.class,
        com.swiftdrop.notification.domain.NotificationRecordRepository.class
})
class HubApiTest {

    private static final Instant NOW = Instant.parse("2026-11-11T08:00:00Z");
    private static final String KANDY = """
            {"code": "kdy", "name": "Kandy branch", "city": "Kandy", "zoneId": "%s", "central": false}
            """.formatted(UUID.randomUUID());

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private HubRepository hubs;
    @MockitoBean
    private HubLinkRepository links;
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
    void dispatchersAndTheAdminReadTheNetwork() throws Exception {
        when(hubs.findAllByOrderByNameAsc()).thenReturn(List.of(
                new Hub("CMB", "Colombo hub", "Colombo", UUID.randomUUID(), true, NOW)));

        for (TestPrincipal caller : List.of(TestPrincipal.dispatcher(), TestPrincipal.admin())) {
            mvc.perform(get("/api/v1/hubs").with(caller.postProcessor()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].code").value("CMB"))
                    .andExpect(jsonPath("$[0].central").value(true))
                    .andExpect(jsonPath("$[0].active").value(true));
        }
    }

    @Test
    void customersAndCouriersDoNotSeeTheNetwork() throws Exception {
        for (TestPrincipal caller : List.of(TestPrincipal.customer(), TestPrincipal.courier())) {
            mvc.perform(get("/api/v1/hubs").with(caller.postProcessor())).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/hubs")).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyTheAdminCreatesHubs() throws Exception {
        when(hubs.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        mvc.perform(post("/api/v1/hubs").with(TestPrincipal.admin().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content(KANDY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("KDY"))
                .andExpect(jsonPath("$.central").value(false));
        mvc.perform(post("/api/v1/hubs").with(TestPrincipal.dispatcher().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content(KANDY))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidHubIsABadRequest() throws Exception {
        String branchWithoutZone = """
                {"code": "GAL", "name": "Galle branch", "city": "Galle", "central": false}
                """;

        mvc.perform(post("/api/v1/hubs").with(TestPrincipal.admin().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content(branchWithoutZone))
                .andExpect(status().isBadRequest());
    }

    @Test
    void secondCentralHubIsAConflict() throws Exception {
        when(hubs.findFirstByCentralTrueAndActiveTrue()).thenReturn(Optional.of(
                new Hub("CMB", "Colombo hub", "Colombo", UUID.randomUUID(), true, NOW)));
        String central = """
                {"code": "HUB2", "name": "Second hub", "city": "Kandy", "central": true}
                """;

        mvc.perform(post("/api/v1/hubs").with(TestPrincipal.admin().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content(central))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownHubCannotBeUpdated() throws Exception {
        mvc.perform(put("/api/v1/hubs/" + UUID.randomUUID()).with(TestPrincipal.admin().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content(KANDY))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyTheAdminDeletesHubs() throws Exception {
        UUID id = UUID.randomUUID();
        when(hubs.findById(id)).thenReturn(Optional.of(
                new Hub("KDY", "Kandy branch", "Kandy", UUID.randomUUID(), false, NOW)));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/hubs/" + id)
                        .with(TestPrincipal.dispatcher().postProcessor()))
                .andExpect(status().isForbidden());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/hubs/" + id)
                        .with(TestPrincipal.admin().postProcessor()))
                .andExpect(status().isNoContent());
    }

    // ---- Direct links (ADR 0011 §3) ----

    private Hub branch(String code, String city) {
        Hub hub = new Hub(code, city + " branch", city, UUID.randomUUID(), false, NOW);
        when(hubs.findById(hub.getId())).thenReturn(Optional.of(hub));
        return hub;
    }

    private static String linkBody(UUID hubId, UUID otherHubId) {
        return """
                {"hubId": "%s", "otherHubId": "%s"}
                """.formatted(hubId, otherHubId);
    }

    @Test
    void dispatchersAndTheAdminReadTheLinks() throws Exception {
        UUID badulla = UUID.randomUUID();
        UUID monaragala = UUID.randomUUID();
        HubLink link = new HubLink(badulla, monaragala, UUID.randomUUID(), NOW);
        when(links.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(link));

        for (TestPrincipal caller : List.of(TestPrincipal.dispatcher(), TestPrincipal.admin())) {
            mvc.perform(get("/api/v1/hubs/links").with(caller.postProcessor()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].hubId").value(link.getFirstHubId().toString()))
                    .andExpect(jsonPath("$[0].otherHubId").value(link.getSecondHubId().toString()))
                    .andExpect(jsonPath("$[0].createdBy").doesNotExist());
        }
        mvc.perform(get("/api/v1/hubs/links").with(TestPrincipal.courier().postProcessor()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/hubs/links")).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyTheAdminLinksBranches() throws Exception {
        Hub badulla = branch("BAD", "Badulla");
        Hub monaragala = branch("MON", "Monaragala");
        when(links.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        String body = linkBody(badulla.getId(), monaragala.getId());

        mvc.perform(post("/api/v1/hubs/links").with(TestPrincipal.admin().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hubId").exists())
                .andExpect(jsonPath("$.otherHubId").exists());
        mvc.perform(post("/api/v1/hubs/links").with(TestPrincipal.dispatcher().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidLinkIsRejected() throws Exception {
        Hub badulla = branch("BAD", "Badulla");
        Hub colombo = new Hub("CMB", "Colombo hub", "Colombo", UUID.randomUUID(), true, NOW);
        when(hubs.findById(colombo.getId())).thenReturn(Optional.of(colombo));

        // The same hub twice, and a missing hub ID
        mvc.perform(post("/api/v1/hubs/links").with(TestPrincipal.admin().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content(linkBody(badulla.getId(), badulla.getId())))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/hubs/links").with(TestPrincipal.admin().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hubId\": \"%s\"}".formatted(badulla.getId())))
                .andExpect(status().isBadRequest());
        // An unknown hub, and the central hub
        mvc.perform(post("/api/v1/hubs/links").with(TestPrincipal.admin().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(linkBody(badulla.getId(), UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/hubs/links").with(TestPrincipal.admin().postProcessor())
                        .contentType(MediaType.APPLICATION_JSON).content(linkBody(badulla.getId(), colombo.getId())))
                .andExpect(status().isConflict());
    }

    @Test
    void onlyTheAdminRemovesLinks() throws Exception {
        UUID badulla = UUID.randomUUID();
        UUID monaragala = UUID.randomUUID();
        when(links.findLink(badulla, monaragala)).thenReturn(Optional.of(
                new HubLink(badulla, monaragala, UUID.randomUUID(), NOW)));
        String path = "/api/v1/hubs/links/" + badulla + "/" + monaragala;

        mvc.perform(delete(path).with(TestPrincipal.dispatcher().postProcessor()))
                .andExpect(status().isForbidden());
        mvc.perform(delete(path).with(TestPrincipal.admin().postProcessor()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/hubs/links/" + monaragala + "/" + UUID.randomUUID())
                        .with(TestPrincipal.admin().postProcessor()))
                .andExpect(status().isNotFound());
    }
}
