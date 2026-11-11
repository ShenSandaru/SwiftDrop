package com.swiftdrop.merchant.infrastructure.webhook;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

import com.swiftdrop.merchant.api.WebhookUrlValidator;

/**
 * URL rules of ADR 0004 §7.3: absolute URL, {@code https}, no user information, no fragment, an
 * allowed port, and a host whose resolved addresses are all permitted. Used when an endpoint is
 * registered or changed and again before every attempt.
 *
 * <p>Only with the local-development flag on, and only when every address is {@code 127.0.0.1} or
 * {@code ::1}, may the URL use {@code http} and any port, for the demo receiver.
 */
final class WebhookDestinationValidator implements WebhookUrlValidator {

    static final int MAX_URL_LENGTH = 2048;

    static final String INVALID_URL = "Webhook URL must be an absolute https URL of at most "
            + MAX_URL_LENGTH + " characters";
    static final String HTTPS_REQUIRED = "Webhook URL must use https";
    static final String USER_INFO = "Webhook URL must not contain user information";
    static final String FRAGMENT = "Webhook URL must not contain a fragment";
    static final String PORT_NOT_ALLOWED = "Webhook URL port is not allowed";
    static final String UNRESOLVABLE = "Webhook host could not be resolved";

    private final ValidatingDnsResolver resolver;
    private final Set<Integer> allowedPorts;

    WebhookDestinationValidator(ValidatingDnsResolver resolver, WebhookClientProperties properties) {
        this.resolver = resolver;
        this.allowedPorts = Set.copyOf(properties.allowedPorts());
    }

    @Override
    public ValidationResult validate(String url) {
        try {
            check(url);
            return ValidationResult.valid();
        } catch (RefusedDestinationException e) {
            return ValidationResult.invalid(e.getMessage());
        } catch (UnknownHostException e) {
            return ValidationResult.invalid(UNRESOLVABLE);
        }
    }

    /**
     * Returns the parsed URL if it may be called now.
     *
     * @throws RefusedDestinationException if a rule refuses it
     * @throws UnknownHostException        if the host cannot be resolved
     */
    URI check(String url) throws UnknownHostException {
        if (url == null || url.isBlank() || url.length() > MAX_URL_LENGTH) {
            throw new RefusedDestinationException(INVALID_URL);
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new RefusedDestinationException(INVALID_URL);
        }
        if (!uri.isAbsolute() || uri.isOpaque() || uri.getHost() == null) {
            throw new RefusedDestinationException(INVALID_URL);
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !scheme.equals("http")) {
            throw new RefusedDestinationException(HTTPS_REQUIRED);
        }
        if (uri.getRawUserInfo() != null) {
            throw new RefusedDestinationException(USER_INFO);
        }
        if (uri.getRawFragment() != null) {
            throw new RefusedDestinationException(FRAGMENT);
        }

        InetAddress[] addresses = resolver.resolve(uri.getHost());
        if (!resolver.isLocalDevelopmentTarget(addresses)) {
            if (!scheme.equals("https")) {
                throw new RefusedDestinationException(HTTPS_REQUIRED);
            }
            int port = uri.getPort() == -1 ? 443 : uri.getPort();
            if (!allowedPorts.contains(port)) {
                throw new RefusedDestinationException(PORT_NOT_ALLOWED);
            }
        }
        return uri;
    }
}
