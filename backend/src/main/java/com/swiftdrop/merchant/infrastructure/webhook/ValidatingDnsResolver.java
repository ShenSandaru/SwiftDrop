package com.swiftdrop.merchant.infrastructure.webhook;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.hc.client5.http.DnsResolver;

/**
 * The only source of connection addresses for webhook requests (ADR 0004 §7.3, option E). It
 * resolves a host once, refuses it if <em>any</em> address is prohibited, and returns exactly the
 * checked addresses, so the connection cannot go to an address that a second lookup produced (DNS
 * rebinding).
 */
final class ValidatingDnsResolver implements DnsResolver {

    static final String PROHIBITED = "Webhook URL points to a prohibited destination";
    static final String NON_STANDARD_ADDRESS = "Webhook URL host must be a host name or a standard IP address";

    // Hosts made only of numeric or hex labels ("2130706433", "0x7f000001", "0177.0.0.1", "127.1")
    // are read differently by different resolvers, so only the dotted-quad form is accepted.
    private static final Pattern NUMERIC_LABELS =
            Pattern.compile("(0x[0-9a-f]*|[0-9]+)(\\.(0x[0-9a-f]*|[0-9]+))*");
    private static final Pattern DOTTED_QUAD =
            Pattern.compile("(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}");

    private final HostResolver hostResolver;
    private final boolean allowLocal;
    private final Set<String> blockedHosts;

    ValidatingDnsResolver(HostResolver hostResolver, WebhookClientProperties properties) {
        this.hostResolver = hostResolver;
        this.allowLocal = properties.allowLocal();
        this.blockedHosts = properties.blockedHosts().stream()
                .map(ValidatingDnsResolver::normalize)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        String name = normalize(host);
        if (name.isEmpty() || blockedHosts.contains(name)) {
            throw new RefusedDestinationException(PROHIBITED);
        }
        if (NUMERIC_LABELS.matcher(name).matches() && !DOTTED_QUAD.matcher(name).matches()) {
            throw new RefusedDestinationException(NON_STANDARD_ADDRESS);
        }
        InetAddress[] addresses = hostResolver.resolve(name);
        // Never return nothing: HttpClient would then let the socket resolve the name itself.
        if (addresses == null || addresses.length == 0) {
            throw new UnknownHostException("Webhook host has no addresses");
        }
        for (InetAddress address : addresses) {
            boolean localException = allowLocal && ProhibitedAddresses.isLocalDevelopmentLoopback(address);
            if (!localException && ProhibitedAddresses.isProhibited(address)) {
                throw new RefusedDestinationException(PROHIBITED);
            }
        }
        return addresses.clone();
    }

    @Override
    public List<InetSocketAddress> resolve(String host, int port) throws UnknownHostException {
        return Arrays.stream(resolve(host))
                .map(address -> new InetSocketAddress(address, port))
                .toList();
    }

    /** No reverse lookup; HttpClient asks for this only for Kerberos/SPNEGO, which webhooks do not use. */
    @Override
    public String resolveCanonicalHostname(String host) {
        return host;
    }

    /** True when the local-development flag is on and every address is an allowed loopback. */
    boolean isLocalDevelopmentTarget(InetAddress[] addresses) {
        return allowLocal && Arrays.stream(addresses).allMatch(ProhibitedAddresses::isLocalDevelopmentLoopback);
    }

    private static String normalize(String host) {
        String name = host == null ? "" : host.strip().toLowerCase(Locale.ROOT);
        if (name.startsWith("[") && name.endsWith("]")) {
            name = name.substring(1, name.length() - 1);
        }
        return name.endsWith(".") ? name.substring(0, name.length() - 1) : name;
    }
}
