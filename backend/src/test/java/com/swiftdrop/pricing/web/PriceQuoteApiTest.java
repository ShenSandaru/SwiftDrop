package com.swiftdrop.pricing.web;

import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.pricing.domain.PricingRule;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.Zone;
import com.swiftdrop.pricing.domain.ZoneRepository;
import com.swiftdrop.test.security.TestPrincipal;

/** The quote endpoint through HTTP with the real calculator; only the repositories are mocked. */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
class PriceQuoteApiTest {

    private static final UUID COLOMBO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID KANDY = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID RULE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mvc;

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

    @BeforeEach
    void setUp() {
        givenZone(COLOMBO, "CMB", "Colombo");
        givenZone(KANDY, "KDY", "Kandy");
        PricingRule rule = new PricingRule(COLOMBO, KANDY, new BigDecimal("400.00"), new BigDecimal("100.00"),
                new BigDecimal("0.00"), new BigDecimal("50.00"), new BigDecimal("150.00"), "LKR");
        ReflectionTestUtils.setField(rule, "id", RULE_ID);
        when(pricingRules.findByOriginZoneIdAndDestinationZoneIdAndActiveTrue(COLOMBO, KANDY))
                .thenReturn(Optional.of(rule));
    }

    @Test
    void customerGetsQuoteWithBreakdownAsStrings() throws Exception {
        quote(TestPrincipal.customer(), body(COLOMBO, KANDY, "\"MEDIUM\"", "2000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("650.00"))
                .andExpect(jsonPath("$.currency").value("LKR"))
                .andExpect(jsonPath("$.pricingRuleId").value(RULE_ID.toString()))
                .andExpect(jsonPath("$.breakdown.baseFee").value("400.00"))
                .andExpect(jsonPath("$.breakdown.weightFee").value("200.00"))
                .andExpect(jsonPath("$.breakdown.sizeSurcharge").value("50.00"));
    }

    @Test
    void anonymousCallerIsRejected() throws Exception {
        quote(TestPrincipal.anonymous(), body(COLOMBO, KANDY, "\"SMALL\"", "1000"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unpricedRouteReturnsConflictWithoutZoneIds() throws Exception {
        quote(TestPrincipal.customer(), body(KANDY, COLOMBO, "\"SMALL\"", "1000"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("No pricing for this route"));
    }

    @Test
    void missingAndOutOfRangeFieldsAreListedWithoutValues() throws Exception {
        quote(TestPrincipal.customer(), "{\"pickupZoneId\":\"" + COLOMBO + "\",\"weightGrams\":30001}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidFields", contains("dropoffZoneId", "size", "weightGrams")));
    }

    @Test
    void unknownSizeIsBadRequest() throws Exception {
        quote(TestPrincipal.customer(), body(COLOMBO, KANDY, "\"HUGE\"", "1000"))
                .andExpect(status().isBadRequest());
    }

    private ResultActions quote(TestPrincipal principal, String json) throws Exception {
        return mvc.perform(post("/api/v1/price-quotes")
                .with(principal.postProcessor())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String body(UUID pickup, UUID dropoff, String size, String weightGrams) {
        return "{\"pickupZoneId\":\"" + pickup + "\",\"dropoffZoneId\":\"" + dropoff
                + "\",\"size\":" + size + ",\"weightGrams\":" + weightGrams + "}";
    }

    private void givenZone(UUID id, String code, String name) {
        Zone zone = new Zone(code, name);
        ReflectionTestUtils.setField(zone, "id", id);
        when(zones.findById(id)).thenReturn(Optional.of(zone));
    }
}
