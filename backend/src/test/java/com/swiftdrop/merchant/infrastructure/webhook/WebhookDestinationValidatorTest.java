package com.swiftdrop.merchant.infrastructure.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.swiftdrop.merchant.api.WebhookUrlValidator.ValidationResult;

class WebhookDestinationValidatorTest {

    private static final String PUBLIC_V4 = "93.184.216.34";
    private static final String PUBLIC_V6 = "2606:4700:4700::1111";

    private final StubHostResolver dns = new StubHostResolver()
            .answer("merchant.example", PUBLIC_V4, PUBLIC_V6)
            .answer("api.internal.example", PUBLIC_V4);

    @Test
    @DisplayName("An https URL whose host resolves only to public addresses is valid")
    void publicHttpsUrl() {
        assertThat(validator(false).validate("https://merchant.example/hooks/swiftdrop?v=1"))
                .isEqualTo(ValidationResult.valid());
        assertThat(validator(false).validate("https://merchant.example:443/hooks")).isEqualTo(ValidationResult.valid());
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("Malformed, relative, non-http(s) and over-long URLs are invalid")
    @ValueSource(strings = {"", " ", "merchant.example/hook", "/hooks", "https://", "https:merchant.example",
            "ftp://merchant.example/hook", "file:///etc/passwd", "https://merchant example/hook",
            "javascript:alert(1)"})
    void malformedUrls(String url) {
        assertThat(validator(false).validate(url).isValid()).isFalse();
    }

    @Test
    @DisplayName("Null and URLs longer than 2048 characters are invalid")
    void nullAndTooLong() {
        assertThat(validator(false).validate(null).isValid()).isFalse();
        String tooLong = "https://merchant.example/" + "a".repeat(WebhookDestinationValidator.MAX_URL_LENGTH);
        assertThat(validator(false).validate(tooLong).message()).isEqualTo(WebhookDestinationValidator.INVALID_URL);
    }

    @Test
    @DisplayName("http is refused outside the local-development mode")
    void httpRefused() {
        assertThat(validator(false).validate("http://merchant.example/hook").message())
                .isEqualTo(WebhookDestinationValidator.HTTPS_REQUIRED);
    }

    @Test
    @DisplayName("User information and fragments are refused")
    void userInfoAndFragment() {
        assertThat(validator(false).validate("https://user:secret@merchant.example/hook").message())
                .isEqualTo(WebhookDestinationValidator.USER_INFO);
        assertThat(validator(false).validate("https://merchant.example/hook#part").message())
                .isEqualTo(WebhookDestinationValidator.FRAGMENT);
    }

    @Test
    @DisplayName("Ports other than 443 are refused unless configured")
    void ports() {
        assertThat(validator(false).validate("https://merchant.example:8443/hook").message())
                .isEqualTo(WebhookDestinationValidator.PORT_NOT_ALLOWED);
        var widened = new WebhookClientProperties(false, List.of(443, 8443), null, null, null);
        assertThat(new WebhookDestinationValidator(new ValidatingDnsResolver(dns, widened), widened)
                .validate("https://merchant.example:8443/hook").isValid()).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("A host resolving to any prohibited address is refused")
    @ValueSource(strings = {"127.0.0.1", "10.1.2.3", "172.16.5.4", "192.168.1.1", "169.254.169.254",
            "100.64.0.1", "::1", "fd00::1", "fe80::1", "64:ff9b::a00:1", "2002:a00:1::"})
    void prohibitedResolution(String literal) {
        dns.answer("evil.example", literal);
        assertThat(validator(false).validate("https://evil.example/hook").message())
                .isEqualTo(ValidatingDnsResolver.PROHIBITED);
    }

    @Test
    @DisplayName("A host with one public and one private address is refused")
    void mixedAnswers() {
        dns.answer("mixed.example", PUBLIC_V4, "10.0.0.7");
        assertThat(validator(false).validate("https://mixed.example/hook").message())
                .isEqualTo(ValidatingDnsResolver.PROHIBITED);
    }

    @Test
    @DisplayName("Configured blocked host names are refused regardless of their addresses")
    void blockedHosts() {
        var properties = new WebhookClientProperties(false, null, List.of("API.Internal.Example"), null, null);
        var validator = new WebhookDestinationValidator(new ValidatingDnsResolver(dns, properties), properties);
        assertThat(validator.validate("https://api.internal.example/hook").message())
                .isEqualTo(ValidatingDnsResolver.PROHIBITED);
        assertThat(validator.validate("https://api.internal.example./hook").message())
                .isEqualTo(ValidatingDnsResolver.PROHIBITED);
        assertThat(dns.lookups("api.internal.example")).isZero();
    }

    @Test
    @DisplayName("An unresolvable host is invalid with a generic message")
    void unresolvable() {
        assertThat(validator(false).validate("https://unknown.example/hook").message())
                .isEqualTo(WebhookDestinationValidator.UNRESOLVABLE);
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("IP literals in any encoding are checked by their real address (system resolver)")
    @ValueSource(strings = {"https://127.0.0.1/hook", "https://[::1]/hook", "https://[::ffff:127.0.0.1]/hook",
            "https://[::ffff:a9fe:a9fe]/hook", "https://169.254.169.254/latest/meta-data",
            "https://2130706433/hook", "https://0x7f000001/hook", "https://0177.0.0.1/hook",
            "https://017700000001/hook", "https://127.1/hook", "https://0.0.0.0/hook",
            "https://localhost/hook", "https://localhost./hook"})
    void literalEncodings(String url) {
        var properties = new WebhookClientProperties(false, null, null, null, null);
        var validator = new WebhookDestinationValidator(
                new ValidatingDnsResolver(HostResolver.system(), properties), properties);
        // Refused as prohibited, or not resolvable at all: never valid.
        assertThat(validator.validate(url).isValid()).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("Decimal, hex, octal and shortened IPv4 forms are refused before any lookup")
    @ValueSource(strings = {"2130706433", "0x7f000001", "0177.0.0.1", "017700000001", "127.1", "0x7f.1",
            "177.0.0.01", "1.2.3.4.5", "256.1.1.1"})
    void nonStandardNumericHosts(String host) {
        // Some forms are already rejected by java.net.URI as malformed, the rest by the resolver.
        assertThat(validator(false).validate("https://" + host + "/hook").message())
                .isIn(ValidatingDnsResolver.NON_STANDARD_ADDRESS, WebhookDestinationValidator.INVALID_URL);
        assertThat(dns.lookups(host)).isZero();
    }

    @Test
    @DisplayName("A standard public dotted-quad address is checked like any other host")
    void standardPublicIpv4Literal() {
        dns.answer("93.184.216.34", PUBLIC_V4);
        assertThat(validator(false).validate("https://93.184.216.34/hook").isValid()).isTrue();
    }

    @Test
    @DisplayName("DNS rebinding: a host that was public at registration is refused once it resolves privately")
    void rebinding() {
        dns.answer("rebind.example", PUBLIC_V4).answer("rebind.example", "127.0.0.1");
        var properties = new WebhookClientProperties(false, null, null, null, null);
        var resolver = new ValidatingDnsResolver(dns, properties);
        var validator = new WebhookDestinationValidator(resolver, properties);

        assertThat(validator.validate("https://rebind.example/hook").isValid()).isTrue();
        assertThatThrownBy(() -> resolver.resolve("rebind.example"))
                .isInstanceOf(RefusedDestinationException.class);
    }

    @Test
    @DisplayName("Local mode allows http and any port, but only to 127.0.0.1 and ::1")
    void localMode() {
        dns.answer("localhost", "127.0.0.1", "::1").answer("loopback2.example", "127.0.0.2");
        var local = validator(true);

        assertThat(local.validate("http://localhost:9000/hook").isValid()).isTrue();
        assertThat(local.validate("https://localhost:8443/hook").isValid()).isTrue();
        assertThat(local.validate("http://loopback2.example:9000/hook").message())
                .isEqualTo(ValidatingDnsResolver.PROHIBITED);

        dns.answer("private.example", "10.0.0.5");
        assertThat(local.validate("http://private.example:9000/hook").message())
                .isEqualTo(ValidatingDnsResolver.PROHIBITED);
        // Public hosts keep the normal rules in local mode.
        assertThat(local.validate("http://merchant.example/hook").message())
                .isEqualTo(WebhookDestinationValidator.HTTPS_REQUIRED);
        assertThat(local.validate("https://merchant.example:9000/hook").message())
                .isEqualTo(WebhookDestinationValidator.PORT_NOT_ALLOWED);
    }

    @Test
    @DisplayName("Loopback is refused when local mode is off")
    void loopbackOutsideLocalMode() {
        dns.answer("localhost", "127.0.0.1", "::1");
        assertThat(validator(false).validate("http://localhost:9000/hook").message())
                .isEqualTo(ValidatingDnsResolver.PROHIBITED);
    }

    private WebhookDestinationValidator validator(boolean allowLocal) {
        var properties = new WebhookClientProperties(allowLocal, null, null, null, null);
        return new WebhookDestinationValidator(new ValidatingDnsResolver(dns, properties), properties);
    }
}
