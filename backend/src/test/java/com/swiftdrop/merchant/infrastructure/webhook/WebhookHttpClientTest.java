package com.swiftdrop.merchant.infrastructure.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import com.swiftdrop.merchant.infrastructure.webhook.WebhookCallResult.Outcome;

/**
 * The five mandatory checks of ADR 0004 §7.3 for the Apache HttpClient 5 client, against real
 * loopback servers. Host names end in {@code .invalid}, which never resolves in real DNS, so a
 * successful request proves that the stub resolver supplied the address.
 */
class WebhookHttpClientTest {

    private static final InetAddress LOOPBACK = StubHostResolver.address("127.0.0.1");
    private static final char[] PASSWORD = "test-only-password".toCharArray();
    private static final byte[] BODY = "{\"eventId\":\"e-1\"}".getBytes(StandardCharsets.UTF_8);

    @TempDir
    static Path certificates;

    private static KeyStore localhostCertificate;
    private static KeyStore otherHostCertificate;

    private final StubHostResolver dns = new StubHostResolver();
    private final RecordingServer server = new RecordingServer();
    private HttpServer running;

    @BeforeAll
    static void createCertificates() throws Exception {
        localhostCertificate = selfSigned("localhost", "dns:localhost,ip:127.0.0.1");
        otherHostCertificate = selfSigned("other", "dns:other.invalid");
    }

    @AfterEach
    void stopServer() {
        if (running != null) {
            running.stop(0);
        }
    }

    // Check 1: the validating resolver is the only source of connection addresses.

    @Test
    @DisplayName("Connections use the address from the validating resolver, which is asked on every attempt")
    void resolverIsTheAddressSource() throws Exception {
        int port = startHttp();
        dns.answer("merchant.invalid", LOOPBACK);

        try (var client = client(true, Duration.ofSeconds(10), defaultTls())) {
            var result = client.post("http://merchant.invalid:" + port + "/hook",
                    Map.of("X-SwiftDrop-Event-Id", "e-1"), BODY);

            assertThat(result.outcome()).isEqualTo(Outcome.RESPONDED);
            assertThat(result.httpStatus()).isEqualTo(204);
        }
        assertThat(server.hits("/hook")).isEqualTo(1);
        assertThat(server.lastHeader("X-SwiftDrop-Event-Id")).isEqualTo("e-1");
        assertThat(server.lastHeader("Content-Type")).startsWith("application/json");
        assertThat(server.lastBody()).isEqualTo(BODY);
        // Once for the URL check, once by the connection manager.
        assertThat(dns.lookups("merchant.invalid")).isEqualTo(2);
    }

    @Test
    @DisplayName("IP literal hosts also go through the validating resolver")
    void ipLiteralGoesThroughResolver() throws Exception {
        int port = startHttp();
        dns.answer("127.0.0.1", LOOPBACK);

        try (var client = client(true, Duration.ofSeconds(10), defaultTls())) {
            assertThat(client.post("http://127.0.0.1:" + port + "/hook", Map.of(), BODY).outcome())
                    .isEqualTo(Outcome.RESPONDED);
        }
        assertThat(dns.lookups("127.0.0.1")).isEqualTo(2);
    }

    // Check 4: no avoidable bypass (second resolution, pooled connection, proxy).

    @Test
    @DisplayName("DNS rebinding: an address that turns private after the URL check is refused before connecting")
    void rebindingRefusedAtConnection() throws Exception {
        dns.answer("rebind.invalid", "93.184.216.34").answer("rebind.invalid", LOOPBACK);

        try (var client = client(false, Duration.ofSeconds(10), defaultTls())) {
            var result = client.post("https://rebind.invalid/hook", Map.of(), BODY);

            assertThat(result.outcome()).isEqualTo(Outcome.REFUSED_DESTINATION);
            assertThat(result.httpStatus()).isNull();
        }
        assertThat(dns.lookups("rebind.invalid")).isEqualTo(2);
    }

    @Test
    @DisplayName("Every request opens a new connection, so no pooled connection outlives a check")
    void noConnectionReuse() throws Exception {
        int port = startHttp();
        dns.answer("merchant.invalid", LOOPBACK);

        try (var client = client(true, Duration.ofSeconds(10), defaultTls())) {
            client.post("http://merchant.invalid:" + port + "/hook", Map.of(), BODY);
            client.post("http://merchant.invalid:" + port + "/hook", Map.of(), BODY);
        }
        assertThat(server.hits("/hook")).isEqualTo(2);
        assertThat(server.distinctClientPorts()).hasSize(2);
        assertThat(dns.lookups("merchant.invalid")).isEqualTo(4);
    }

    @Test
    @DisplayName("JVM HTTP and HTTPS proxy settings are ignored")
    void noProxy() throws Exception {
        // Fails if the client reads system proxy settings (checked by replacing the explicit route
        // planner with useSystemProperties()). SOCKS cannot be shown this way: the JDK connects
        // loopback sockets directly anyway, so the client's Proxy.NO_PROXY sockets are defense in depth.
        int port = startHttp();
        var proxy = new RecordingServer();
        HttpServer proxyServer = proxy.start(HttpServer.create(new InetSocketAddress(LOOPBACK, 0), 0));
        String proxyPort = String.valueOf(proxyServer.getAddress().getPort());
        dns.answer("merchant.invalid", LOOPBACK);

        // The JDK never proxies loopback by default, so the exclusions are replaced as well.
        Map<String, String> proxySettings = Map.of(
                "http.proxyHost", "127.0.0.1", "http.proxyPort", proxyPort,
                "https.proxyHost", "127.0.0.1", "https.proxyPort", proxyPort,
                "http.nonProxyHosts", "none.invalid");
        Map<String, String> previous = new ConcurrentHashMap<>();
        proxySettings.forEach((key, value) -> {
            String old = System.getProperty(key);
            if (old != null) {
                previous.put(key, old);
            }
            System.setProperty(key, value);
        });
        try (var client = client(true, Duration.ofSeconds(10), defaultTls())) {
            assertThat(client.post("http://merchant.invalid:" + port + "/hook", Map.of(), BODY).outcome())
                    .isEqualTo(Outcome.RESPONDED);
        } finally {
            proxySettings.keySet().forEach(key -> {
                if (previous.containsKey(key)) {
                    System.setProperty(key, previous.get(key));
                } else {
                    System.clearProperty(key);
                }
            });
            proxyServer.stop(0);
        }
        assertThat(server.hits("/hook")).isEqualTo(1);
        assertThat(proxy.totalHits()).isZero();
    }

    // Check 3: redirects are disabled.

    @Test
    @DisplayName("Redirects are reported as the response status and never followed")
    void redirectsNotFollowed() throws Exception {
        int port = startHttp();
        server.redirect("/start", "http://127.0.0.1:" + port + "/target");
        dns.answer("merchant.invalid", LOOPBACK);

        try (var client = client(true, Duration.ofSeconds(10), defaultTls())) {
            var result = client.post("http://merchant.invalid:" + port + "/start", Map.of(), BODY);

            assertThat(result.outcome()).isEqualTo(Outcome.RESPONDED);
            assertThat(result.httpStatus()).isEqualTo(302);
        }
        assertThat(server.hits("/start")).isEqualTo(1);
        assertThat(server.hits("/target")).isZero();
    }

    @Test
    @DisplayName("Retry-After is passed on for 429 and 503 only")
    void retryAfterOnlyFor429And503() throws Exception {
        int port = startHttp();
        server.status("/busy", 429);
        server.status("/down", 503);
        server.status("/broken", 500);
        dns.answer("merchant.invalid", LOOPBACK);

        try (var client = client(true, Duration.ofSeconds(10), defaultTls())) {
            String base = "http://merchant.invalid:" + port;
            assertThat(client.post(base + "/busy", Map.of(), BODY).retryAfter()).isEqualTo("120");
            assertThat(client.post(base + "/down", Map.of(), BODY).retryAfter()).isEqualTo("120");
            var broken = client.post(base + "/broken", Map.of(), BODY);
            assertThat(broken.httpStatus()).isEqualTo(500);
            assertThat(broken.retryAfter()).isNull();
        }
    }

    // Check 5: prohibited destinations are refused unless local mode allows loopback.

    @Test
    @DisplayName("Loopback is refused without local mode, even though a server is listening there")
    void loopbackRefusedOutsideLocalMode() throws Exception {
        int port = startHttp();
        dns.answer("merchant.invalid", LOOPBACK);

        try (var client = client(false, Duration.ofSeconds(10), defaultTls())) {
            assertThat(client.post("https://merchant.invalid:" + port + "/hook", Map.of(), BODY).outcome())
                    .isEqualTo(Outcome.REFUSED_DESTINATION);
            assertThat(client.post("http://merchant.invalid/hook", Map.of(), BODY).outcome())
                    .isEqualTo(Outcome.REFUSED_DESTINATION);
        }
        assertThat(server.totalHits()).isZero();
    }

    @Test
    @DisplayName("Private addresses stay refused in local mode")
    void privateRefusedInLocalMode() throws Exception {
        dns.answer("private.invalid", "10.0.0.5");

        try (var client = client(true, Duration.ofSeconds(10), defaultTls())) {
            assertThat(client.post("http://private.invalid:9000/hook", Map.of(), BODY).outcome())
                    .isEqualTo(Outcome.REFUSED_DESTINATION);
        }
    }

    // Check 2: TLS certificate validation and host name verification stay enabled.

    @Test
    @DisplayName("TLS works with a trusted certificate for the URL's host name (control case)")
    void trustedCertificate() throws Exception {
        int port = startHttps(localhostCertificate);
        dns.answer("localhost", LOOPBACK);

        try (var client = client(true, Duration.ofSeconds(10), trusting(localhostCertificate))) {
            var result = client.post("https://localhost:" + port + "/hook", Map.of(), BODY);
            assertThat(result.outcome()).isEqualTo(Outcome.RESPONDED);
            assertThat(result.httpStatus()).isEqualTo(204);
        }
    }

    @Test
    @DisplayName("An untrusted certificate is rejected and nothing is sent")
    void untrustedCertificate() throws Exception {
        int port = startHttps(localhostCertificate);
        dns.answer("localhost", LOOPBACK);

        try (var client = client(true, Duration.ofSeconds(10), defaultTls())) {
            assertThat(client.post("https://localhost:" + port + "/hook", Map.of(), BODY).outcome())
                    .isEqualTo(Outcome.NETWORK_ERROR);
        }
        assertThat(server.totalHits()).isZero();
    }

    @Test
    @DisplayName("A trusted certificate for another host name is rejected and nothing is sent")
    void wrongHostName() throws Exception {
        int port = startHttps(otherHostCertificate);
        dns.answer("localhost", LOOPBACK);

        try (var client = client(true, Duration.ofSeconds(10), trusting(otherHostCertificate))) {
            assertThat(client.post("https://localhost:" + port + "/hook", Map.of(), BODY).outcome())
                    .isEqualTo(Outcome.NETWORK_ERROR);
        }
        assertThat(server.totalHits()).isZero();
    }

    // Outcome categories.

    @Test
    @DisplayName("A response slower than the response timeout is a timeout")
    void responseTimeout() throws Exception {
        int port = startHttp();
        server.delay("/slow", Duration.ofSeconds(3));
        dns.answer("merchant.invalid", LOOPBACK);

        try (var client = client(true, Duration.ofMillis(300), defaultTls())) {
            assertThat(client.post("http://merchant.invalid:" + port + "/slow", Map.of(), BODY).outcome())
                    .isEqualTo(Outcome.TIMEOUT);
        }
    }

    @Test
    @DisplayName("A refused connection is a network error")
    void connectionRefused() throws Exception {
        int closedPort;
        try (var socket = new ServerSocket(0, 1, LOOPBACK)) {
            closedPort = socket.getLocalPort();
        }
        dns.answer("merchant.invalid", LOOPBACK);

        try (var client = client(true, Duration.ofSeconds(10), defaultTls())) {
            assertThat(client.post("http://merchant.invalid:" + closedPort + "/hook", Map.of(), BODY).outcome())
                    .isEqualTo(Outcome.NETWORK_ERROR);
        }
    }

    @Test
    @DisplayName("An invalid URL is refused without any lookup")
    void invalidUrl() throws Exception {
        try (var client = client(false, Duration.ofSeconds(10), defaultTls())) {
            assertThat(client.post("not a url", Map.of(), BODY).outcome()).isEqualTo(Outcome.REFUSED_DESTINATION);
        }
    }

    private WebhookHttpClient client(boolean allowLocal, Duration responseTimeout, SSLContext tls) {
        var properties = new WebhookClientProperties(allowLocal, null, null, Duration.ofSeconds(2), responseTimeout);
        var resolver = new ValidatingDnsResolver(dns, properties);
        return new WebhookHttpClient(new WebhookDestinationValidator(resolver, properties), resolver, properties, tls);
    }

    private int startHttp() throws IOException {
        running = server.start(HttpServer.create(new InetSocketAddress(LOOPBACK, 0), 0));
        return running.getAddress().getPort();
    }

    private int startHttps(KeyStore keyStore) throws Exception {
        var keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keyStore, PASSWORD);
        var serverTls = SSLContext.getInstance("TLS");
        serverTls.init(keyManagers.getKeyManagers(), null, null);
        var https = HttpsServer.create(new InetSocketAddress(LOOPBACK, 0), 0);
        https.setHttpsConfigurator(new HttpsConfigurator(serverTls));
        running = server.start(https);
        return running.getAddress().getPort();
    }

    private static SSLContext defaultTls() throws Exception {
        return SSLContext.getDefault();
    }

    /** A client context that trusts exactly the given self-signed certificate. */
    private static SSLContext trusting(KeyStore keyStore) throws Exception {
        var trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("server", keyStore.getCertificate(keyStore.aliases().nextElement()));
        var trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(trustStore);
        var tls = SSLContext.getInstance("TLS");
        tls.init(null, trustManagers.getTrustManagers(), null);
        return tls;
    }

    /** Generates a self-signed EC certificate with the JDK's keytool. */
    private static KeyStore selfSigned(String alias, String subjectAlternativeNames) throws Exception {
        Path file = certificates.resolve(alias + ".p12");
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        Process process = new ProcessBuilder(keytool, "-genkeypair", "-alias", alias,
                "-keyalg", "EC", "-groupname", "secp256r1", "-dname", "CN=" + alias,
                "-ext", "SAN=" + subjectAlternativeNames, "-validity", "2",
                "-storetype", "PKCS12", "-keystore", file.toString(),
                "-storepass", new String(PASSWORD), "-keypass", new String(PASSWORD))
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException("keytool failed: " + output);
        }
        var keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(file)) {
            keyStore.load(in, PASSWORD);
        }
        return keyStore;
    }

    /** Answers 204 by default and records every request it receives. */
    private static final class RecordingServer {

        private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
        private final List<Integer> clientPorts = new CopyOnWriteArrayList<>();
        private final Map<String, String> redirects = new ConcurrentHashMap<>();
        private final Map<String, Duration> delays = new ConcurrentHashMap<>();
        private final Map<String, Integer> statuses = new ConcurrentHashMap<>();
        private volatile HttpExchange last;
        private volatile byte[] lastBody;

        <T extends HttpServer> T start(T httpServer) {
            httpServer.createContext("/", this::handle);
            httpServer.start();
            return httpServer;
        }

        void redirect(String path, String location) {
            redirects.put(path, location);
        }

        void delay(String path, Duration delay) {
            delays.put(path, delay);
        }

        /** Answers with this status and {@code Retry-After: 120}. */
        void status(String path, int status) {
            statuses.put(path, status);
        }

        int hits(String path) {
            AtomicInteger count = hits.get(path);
            return count == null ? 0 : count.get();
        }

        int totalHits() {
            return hits.values().stream().mapToInt(AtomicInteger::get).sum();
        }

        Set<Integer> distinctClientPorts() {
            return Set.copyOf(clientPorts);
        }

        String lastHeader(String name) {
            return last.getRequestHeaders().getFirst(name);
        }

        byte[] lastBody() {
            return lastBody;
        }

        private void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            hits.computeIfAbsent(path, ignored -> new AtomicInteger()).incrementAndGet();
            clientPorts.add(exchange.getRemoteAddress().getPort());
            lastBody = exchange.getRequestBody().readAllBytes();
            last = exchange;
            try {
                Duration delay = delays.get(path);
                if (delay != null) {
                    Thread.sleep(delay.toMillis());
                }
                String location = redirects.get(path);
                Integer status = statuses.get(path);
                if (status != null) {
                    exchange.getResponseHeaders().set("Retry-After", "120");
                    exchange.sendResponseHeaders(status, -1);
                } else if (location != null) {
                    exchange.getResponseHeaders().set("Location", location);
                    exchange.sendResponseHeaders(302, -1);
                } else {
                    exchange.sendResponseHeaders(204, -1);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        }
    }
}
