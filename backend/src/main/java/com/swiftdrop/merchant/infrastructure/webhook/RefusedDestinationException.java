package com.swiftdrop.merchant.infrastructure.webhook;

import java.net.UnknownHostException;

/**
 * A webhook URL or destination that the rules of ADR 0004 §7.3 refuse. It extends
 * {@link UnknownHostException} because Apache HttpClient's {@code DnsResolver} may only throw that,
 * so a refusal at connection time stops the request before any socket is opened.
 *
 * <p>Messages are fixed, safe texts: merchants see outcome categories, never details about
 * internal addresses.
 */
public class RefusedDestinationException extends UnknownHostException {

    private static final long serialVersionUID = 1L;

    RefusedDestinationException(String message) {
        super(message);
    }
}
