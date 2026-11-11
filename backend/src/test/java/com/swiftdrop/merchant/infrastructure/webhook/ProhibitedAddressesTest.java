package com.swiftdrop.merchant.infrastructure.webhook;

import static com.swiftdrop.merchant.infrastructure.webhook.StubHostResolver.address;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProhibitedAddressesTest {

    @ParameterizedTest(name = "{0} is prohibited")
    @DisplayName("Every IPv4 range of ADR 0004 §7.3 is prohibited, at both ends")
    @ValueSource(strings = {
            "0.0.0.0", "0.255.255.255",
            "10.0.0.0", "10.255.255.255",
            "100.64.0.0", "100.127.255.255",
            "127.0.0.1", "127.255.255.255",
            "169.254.0.0", "169.254.169.254", "169.254.255.255",
            "172.16.0.0", "172.31.255.255",
            "192.0.0.0", "192.0.0.255",
            "192.0.2.0", "192.0.2.255",
            "192.168.0.0", "192.168.255.255",
            "198.18.0.0", "198.19.255.255",
            "198.51.100.0", "198.51.100.255",
            "203.0.113.0", "203.0.113.255",
            "224.0.0.0", "239.255.255.255",
            "240.0.0.0", "255.255.255.255"})
    void prohibitedIpv4(String literal) {
        assertThat(ProhibitedAddresses.isProhibited(address(literal))).isTrue();
    }

    @ParameterizedTest(name = "{0} is allowed")
    @DisplayName("Public IPv4 addresses next to the prohibited ranges are allowed")
    @ValueSource(strings = {
            "1.0.0.0", "8.8.8.8", "93.184.216.34",
            "9.255.255.255", "11.0.0.0",
            "100.63.255.255", "100.128.0.0",
            "126.255.255.255", "128.0.0.0",
            "169.253.255.255", "169.255.0.0",
            "172.15.255.255", "172.32.0.0",
            "192.0.1.0", "192.0.3.0",
            "192.167.255.255", "192.169.0.0",
            "198.17.255.255", "198.20.0.0",
            "198.51.99.255", "198.51.101.0",
            "203.0.112.255", "203.0.114.0",
            "223.255.255.255"})
    void allowedIpv4(String literal) {
        assertThat(ProhibitedAddresses.isProhibited(address(literal))).isFalse();
    }

    @ParameterizedTest(name = "{0} is prohibited")
    @DisplayName("Every IPv6 range and IPv4-embedding form of ADR 0004 §7.3 is prohibited")
    @ValueSource(strings = {
            "::", "::1",
            "fc00::", "fdff:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
            "fe80::1", "febf:ffff::1",
            "fec0::1", "feff:ffff::1",
            "ff00::", "ff02::1",
            "2001:db8::", "2001:db8:ffff:ffff::1",
            // NAT64 and 6to4 forms of 10.0.0.1, 127.0.0.1, 192.168.1.1 and 169.254.169.254
            "64:ff9b::a00:1", "64:ff9b::7f00:1",
            "2002:a00:1::", "2002:c0a8:101::1", "2002:a9fe:a9fe::"})
    void prohibitedIpv6(String literal) {
        assertThat(ProhibitedAddresses.isProhibited(address(literal))).isTrue();
    }

    @ParameterizedTest(name = "{0} is allowed")
    @DisplayName("Public IPv6 addresses, including ones embedding a public IPv4 address, are allowed")
    @ValueSource(strings = {
            "2606:4700:4700::1111", "2001:4860:4860::8888",
            "2001:db7:ffff::1", "2001:db9::1",
            "fbff:ffff::1",
            "64:ff9b::808:808", "2002:808:808::1"})
    void allowedIpv6(String literal) {
        assertThat(ProhibitedAddresses.isProhibited(address(literal))).isFalse();
    }

    @Test
    @DisplayName("IPv4-mapped IPv6 addresses are checked against the IPv4 list even when kept as IPv6")
    void ipv4MappedIpv6() throws UnknownHostException {
        assertThat(ProhibitedAddresses.isProhibited(mapped(10, 0, 0, 1))).isTrue();
        assertThat(ProhibitedAddresses.isProhibited(mapped(127, 0, 0, 1))).isTrue();
        assertThat(ProhibitedAddresses.isProhibited(mapped(169, 254, 169, 254))).isTrue();
        assertThat(ProhibitedAddresses.isProhibited(mapped(8, 8, 8, 8))).isFalse();
    }

    @Test
    @DisplayName("The local-development exception covers only 127.0.0.1 and ::1")
    void localDevelopmentLoopback() {
        assertThat(ProhibitedAddresses.isLocalDevelopmentLoopback(address("127.0.0.1"))).isTrue();
        assertThat(ProhibitedAddresses.isLocalDevelopmentLoopback(address("::1"))).isTrue();
        assertThat(ProhibitedAddresses.isLocalDevelopmentLoopback(address("127.0.0.2"))).isFalse();
        assertThat(ProhibitedAddresses.isLocalDevelopmentLoopback(address("10.0.0.1"))).isFalse();
        assertThat(ProhibitedAddresses.isLocalDevelopmentLoopback(address("::"))).isFalse();
    }

    private static InetAddress mapped(int a, int b, int c, int d) throws UnknownHostException {
        byte[] bytes = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff,
                (byte) a, (byte) b, (byte) c, (byte) d};
        // Inet6Address.getByAddress keeps the IPv6 form; InetAddress.getByAddress would convert it.
        return Inet6Address.getByAddress(null, bytes, -1);
    }
}
