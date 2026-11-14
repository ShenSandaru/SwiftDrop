package com.swiftdrop.merchant.infrastructure.webhook;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

/**
 * The address ranges a webhook must never reach (ADR 0004 §7.3). Checks work on the raw address
 * bytes, so every textual encoding of an address (decimal, octal, IPv4-mapped IPv6) ends up here
 * in the same form after resolution.
 */
final class ProhibitedAddresses {

    private static final List<Cidr> IPV4 = List.of(
            cidr("0.0.0.0", 8),
            cidr("10.0.0.0", 8),
            cidr("100.64.0.0", 10),
            cidr("127.0.0.0", 8),
            cidr("169.254.0.0", 16),
            cidr("172.16.0.0", 12),
            cidr("192.0.0.0", 24),
            cidr("192.0.2.0", 24),
            cidr("192.168.0.0", 16),
            cidr("198.18.0.0", 15),
            cidr("198.51.100.0", 24),
            cidr("203.0.113.0", 24),
            cidr("224.0.0.0", 4),
            cidr("240.0.0.0", 4));

    private static final List<Cidr> IPV6 = List.of(
            cidr("::", 128),
            cidr("::1", 128),
            cidr("fc00::", 7),
            cidr("fe80::", 10),
            cidr("fec0::", 10),
            cidr("ff00::", 8),
            cidr("2001:db8::", 32));

    // Built from bytes: the JDK turns the text "::ffff:0:0" into an IPv4 address.
    private static final Cidr IPV4_MAPPED = new Cidr(
            new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff, 0, 0, 0, 0}, 96);
    private static final Cidr NAT64 = cidr("64:ff9b::", 96);
    private static final Cidr SIX_TO_FOUR = cidr("2002::", 16);

    private static final byte[] LOOPBACK_V4 = {127, 0, 0, 1};
    private static final byte[] LOOPBACK_V6 = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};

    private ProhibitedAddresses() {
    }

    static boolean isProhibited(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return matchesAny(IPV4, bytes);
        }
        if (matchesAny(IPV6, bytes)) {
            return true;
        }
        byte[] embedded = embeddedIpv4(bytes);
        return embedded != null && matchesAny(IPV4, embedded);
    }

    /** The only addresses the local-development flag allows: {@code 127.0.0.1} and {@code ::1}. */
    static boolean isLocalDevelopmentLoopback(InetAddress address) {
        byte[] bytes = address.getAddress();
        return Arrays.equals(bytes, LOOPBACK_V4) || Arrays.equals(bytes, LOOPBACK_V6);
    }

    private static byte[] embeddedIpv4(byte[] ipv6) {
        if (IPV4_MAPPED.contains(ipv6) || NAT64.contains(ipv6)) {
            return Arrays.copyOfRange(ipv6, 12, 16);
        }
        if (SIX_TO_FOUR.contains(ipv6)) {
            return Arrays.copyOfRange(ipv6, 2, 6);
        }
        return null;
    }

    private static boolean matchesAny(List<Cidr> ranges, byte[] address) {
        return ranges.stream().anyMatch(range -> range.contains(address));
    }

    private static Cidr cidr(String literal, int prefixLength) {
        try {
            return new Cidr(InetAddress.getByName(literal).getAddress(), prefixLength);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Not an address literal: " + literal, e);
        }
    }

    private record Cidr(byte[] network, int prefixLength) {

        boolean contains(byte[] address) {
            if (address.length != network.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (address[i] != network[i]) {
                    return false;
                }
            }
            int remainingBits = prefixLength % 8;
            if (remainingBits == 0) {
                return true;
            }
            int mask = (0xFF << (8 - remainingBits)) & 0xFF;
            return (address[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
