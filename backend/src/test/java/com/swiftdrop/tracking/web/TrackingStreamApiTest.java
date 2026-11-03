package com.swiftdrop.tracking.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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

/** The live tracking stream through the real security configuration and Spring MVC async handling. */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
class TrackingStreamApiTest {

    private static final String EVENTS = "/api/v1/tracked-parcels/%s/events";

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
    void anyoneCanOpenAStreamAndGetsTheSnapshotFirst() throws Exception {
        givenParcel("SD7K3M9Q2XWB", ParcelStatus.IN_TRANSIT);

        MvcResult result = mvc.perform(get(EVENTS.formatted("SD7K3M9Q2XWB")).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        assertThat(result.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(result.getResponse().getContentAsString())
                .startsWith("event:snapshot")
                .contains("\"trackingNumber\":\"SD7K3M9Q2XWB\"", "\"status\":\"IN_TRANSIT\"", "\"pickupCity\":\"Colombo\"");
    }

    @Test
    void unknownNumberGets404WithoutAStream() throws Exception {
        when(queries.findPublicTrackingView("SD0000000000")).thenReturn(Optional.empty());

        mvc.perform(get(EVENTS.formatted("SD0000000000")).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncNotStarted())
                .andExpect(status().isNotFound());
    }

    @Test
    void streamIsReadOnly() throws Exception {
        mvc.perform(post(EVENTS.formatted("SD7K3M9Q2XWB"))).andExpect(status().isUnauthorized());
    }

    @Test
    void tooManyStreamsGet503WithRetryAfter() throws Exception {
        givenParcel("SD1111111111", ParcelStatus.CREATED);
        for (int i = 0; i < 20; i++) {
            mvc.perform(get(EVENTS.formatted("SD1111111111")).accept(MediaType.TEXT_EVENT_STREAM))
                    .andExpect(request().asyncStarted());
        }

        mvc.perform(get(EVENTS.formatted("SD1111111111")).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"));
    }

    private void givenParcel(String number, ParcelStatus status) {
        when(queries.findPublicTrackingView(number)).thenReturn(Optional.of(new PublicTrackingView(number, status,
                "Colombo", "Kandy", List.of(new PublicStatusEvent(ParcelStatus.CREATED,
                        Instant.parse("2026-11-03T10:15:30Z"))))));
    }
}
