package com.swiftdrop.delivery.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.PublicStatusEvent;
import com.swiftdrop.delivery.api.PublicTrackingView;
import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.ZoneRepository;
import com.swiftdrop.test.security.TestPrincipal;

/** Public tracking (CUS-6) through the real security configuration: no login, no personal data. */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
class TrackingApiTest {

    private static final String PATH = "/api/v1/tracked-parcels/";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ParcelQueries queries;

    @MockitoBean
    private UserRepository users;
    @MockitoBean
    private ExternalIdentityRepository identities;
    @MockitoBean
    private GoogleLoginCodeRepository googleCodes;
    @MockitoBean
    private ParcelRepository parcels;
    @MockitoBean
    private ParcelStatusHistoryRepository parcelHistory;
    @MockitoBean
    private DeliveryProofRepository deliveryProofs;
    @MockitoBean
    private OutboxEventRepository outboxEvents;
    @MockitoBean
    private FeeCalculator feeCalculator;
    @MockitoBean
    private ZoneRepository zones;
    @MockitoBean
    private PricingRuleRepository pricingRules;

    @Test
    void anyoneCanTrackWithoutLoggingIn() throws Exception {
        when(queries.findPublicTrackingView("sd7k3m9q2xwb")).thenReturn(Optional.of(new PublicTrackingView(
                "SD7K3M9Q2XWB", ParcelStatus.IN_TRANSIT, "Colombo", "Kandy", List.of(
                        new PublicStatusEvent(ParcelStatus.CREATED, Instant.parse("2026-11-03T10:15:30Z")),
                        new PublicStatusEvent(ParcelStatus.PICKED_UP, Instant.parse("2026-11-03T12:01:02Z"))))));

        mvc.perform(get(PATH + "sd7k3m9q2xwb"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value("SD7K3M9Q2XWB"))
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.pickupCity").value("Colombo"))
                .andExpect(jsonPath("$.history[1].status").value("PICKED_UP"))
                .andExpect(jsonPath("$.history[1].occurredAt").value("2026-11-03T12:01:02Z"))
                // Exactly the public fields: nothing else can leak
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$.history[0].length()").value(2));
    }

    @Test
    void unknownAndMalformedNumbersGetTheSame404() throws Exception {
        when(queries.findPublicTrackingView("SD0000000000")).thenReturn(Optional.empty());
        when(queries.findPublicTrackingView("not-a-number")).thenReturn(Optional.empty());

        for (String number : new String[] {"SD0000000000", "not-a-number"}) {
            mvc.perform(get(PATH + number))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Parcel not found"));
        }
    }

    @Test
    void loggedInUsersCanTrackToo() throws Exception {
        when(queries.findPublicTrackingView("SD7K3M9Q2XWB")).thenReturn(Optional.of(new PublicTrackingView(
                "SD7K3M9Q2XWB", ParcelStatus.CREATED, "Colombo", "Kandy", List.of())));

        mvc.perform(get(PATH + "SD7K3M9Q2XWB").with(TestPrincipal.customer().postProcessor()))
                .andExpect(status().isOk());
    }

    @Test
    void onlyReadingIsPublic() throws Exception {
        mvc.perform(post(PATH + "SD7K3M9Q2XWB")).andExpect(status().isUnauthorized());
        mvc.perform(delete(PATH + "SD7K3M9Q2XWB")).andExpect(status().isUnauthorized());
    }

    @Test
    void otherParcelEndpointsStillRequireLogin() throws Exception {
        mvc.perform(get("/api/v1/parcels")).andExpect(status().isUnauthorized());
    }
}
