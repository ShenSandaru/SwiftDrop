package com.swiftdrop.merchant.infrastructure.webhook;

import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the SSRF-safe webhook client (SD-409). {@link WebhookDestinationValidator} is published as
 * the module's {@code WebhookUrlValidator} for endpoint registration (docs/merchant-contracts.md §3.1).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebhookClientProperties.class)
class WebhookClientConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WebhookClientConfiguration.class);

    @Bean
    ValidatingDnsResolver webhookDnsResolver(WebhookClientProperties properties) {
        if (properties.allowLocal()) {
            log.warn("SWIFTDROP_WEBHOOK_ALLOW_LOCAL is on: webhooks may use http to 127.0.0.1 and ::1."
                    + " Never enable this outside local development.");
        }
        return new ValidatingDnsResolver(HostResolver.system(), properties);
    }

    @Bean
    WebhookDestinationValidator webhookUrlValidator(ValidatingDnsResolver resolver,
            WebhookClientProperties properties) {
        return new WebhookDestinationValidator(resolver, properties);
    }

    @Bean
    WebhookHttpClient webhookHttpClient(WebhookDestinationValidator validator, ValidatingDnsResolver resolver,
            WebhookClientProperties properties) throws NoSuchAlgorithmException {
        // The JVM's default trust store and protocols: standard certificate validation.
        return new WebhookHttpClient(validator, resolver, properties, SSLContext.getDefault());
    }
}
