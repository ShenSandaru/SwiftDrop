package com.swiftdrop.merchant.infrastructure.webhook;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the outbound webhook client (ADR 0004 §7.3 and §9). Values outside the ADR's bounds
 * stop the application at startup.
 *
 * @param allowLocal      local development only: allows {@code http} and the loopback addresses
 *                        {@code 127.0.0.1} and {@code ::1} on any port (from
 *                        {@code SWIFTDROP_WEBHOOK_ALLOW_LOCAL}); never private or other prohibited
 *                        ranges
 * @param allowedPorts    ports allowed for non-local destinations (default 443)
 * @param blockedHosts    host names that are always refused, for example the backend's own host and
 *                        local services
 * @param connectTimeout  TCP and TLS connect timeout (default 5 s, at most 10 s)
 * @param responseTimeout maximum wait for response data (default 10 s, at most 30 s)
 */
@ConfigurationProperties("swiftdrop.webhook")
public record WebhookClientProperties(boolean allowLocal, List<Integer> allowedPorts,
        List<String> blockedHosts, Duration connectTimeout, Duration responseTimeout) {

    static final Duration MAX_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    static final Duration MAX_RESPONSE_TIMEOUT = Duration.ofSeconds(30);

    public WebhookClientProperties {
        allowedPorts = allowedPorts == null || allowedPorts.isEmpty() ? List.of(443) : List.copyOf(allowedPorts);
        blockedHosts = blockedHosts == null ? List.of() : List.copyOf(blockedHosts);
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        responseTimeout = responseTimeout == null ? Duration.ofSeconds(10) : responseTimeout;

        for (int port : allowedPorts) {
            if (port < 1 || port > 65535) {
                throw new IllegalStateException("swiftdrop.webhook.allowed-ports must contain ports 1 to 65535");
            }
        }
        requireWithin(connectTimeout, MAX_CONNECT_TIMEOUT, "swiftdrop.webhook.connect-timeout");
        requireWithin(responseTimeout, MAX_RESPONSE_TIMEOUT, "swiftdrop.webhook.response-timeout");
    }

    private static void requireWithin(Duration value, Duration max, String name) {
        if (value.isNegative() || value.isZero() || value.compareTo(max) > 0) {
            throw new IllegalStateException(name + " must be positive and at most " + max.toSeconds() + " s");
        }
    }
}
