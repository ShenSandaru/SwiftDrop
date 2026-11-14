package com.swiftdrop.merchant.infrastructure.webhook;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Name resolution behind {@link ValidatingDnsResolver}; tests replace it with a stub. */
@FunctionalInterface
interface HostResolver {

    InetAddress[] resolve(String host) throws UnknownHostException;

    static HostResolver system() {
        return host -> {
            try {
                return InetAddress.getAllByName(host);
            } catch (IllegalArgumentException e) {
                // The JDK rejects ambiguous literals such as "0177.0.0.1" this way.
                throw new UnknownHostException("Webhook host is not a valid name");
            }
        };
    }
}
