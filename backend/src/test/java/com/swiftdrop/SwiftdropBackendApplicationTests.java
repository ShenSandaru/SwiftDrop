package com.swiftdrop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.merchant.api.WebhookUrlValidator;
import com.swiftdrop.merchant.application.MerchantWebhookService;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.merchant.domain.MerchantStatusHistoryRepository;
import com.swiftdrop.merchant.domain.MerchantWebhookEndpointRepository;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.ZoneRepository;

/**
 * Checks that the application context starts without Docker, PostgreSQL or RabbitMQ.
 * The database auto-configuration is excluded because no database is available here;
 * RabbitMQ connects lazily, so it needs no exclusion. Without JPA there are no repositories, so
 * the identity repository is mocked.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@MockitoBean(types = {com.swiftdrop.delivery.domain.ParcelAssignmentRepository.class, com.swiftdrop.delivery.domain.HubRepository.class})
class SwiftdropBackendApplicationTests {

    @MockitoBean
    private UserRepository users;
    @MockitoBean
    private ExternalIdentityRepository identities;
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
    @MockitoBean
    private MerchantRepository merchants;
    @MockitoBean
    private MerchantStatusHistoryRepository merchantStatusHistory;
    @MockitoBean
    private MerchantWebhookEndpointRepository merchantWebhookEndpoints;
    @MockitoBean
    private com.swiftdrop.merchant.domain.MerchantApiKeyRepository merchantApiKeys;

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
    }

    /** F-02: one webhook URL validator, the SSRF-safe one, used for registration and for sending. */
    @Test
    void webhookRegistrationAndDeliveryUseTheSsrfSafeValidator() {
        Map<String, WebhookUrlValidator> validators = context.getBeansOfType(WebhookUrlValidator.class);
        assertThat(validators).hasSize(1);
        WebhookUrlValidator validator = validators.values().iterator().next();
        assertThat(validator.getClass().getSimpleName()).isEqualTo("WebhookDestinationValidator");

        Object service = context.getBean(MerchantWebhookService.class);
        assertThat(ReflectionTestUtils.getField(service, "urlValidator")).isSameAs(validator);
        Object sender = context.getBean("webhookHttpClient");
        assertThat(ReflectionTestUtils.getField(sender, "validator")).isSameAs(validator);

        // Private and loopback destinations are refused at registration, without any DNS lookup.
        assertThat(validator.validate("https://10.0.0.1/hook").isValid()).isFalse();
        assertThat(validator.validate("https://127.0.0.1/hook").isValid()).isFalse();
        assertThat(validator.validate("http://8.8.8.8/hook").isValid()).isFalse();
    }

}
