package com.swiftdrop.merchant.infrastructure.webhook;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.Proxy;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Map;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SchemePortResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.DefaultHttpClientConnectionOperator;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.DefaultRoutePlanner;
import org.apache.hc.client5.http.io.DetachedSocketFactory;
import org.apache.hc.client5.http.io.HttpClientConnectionOperator;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * SSRF-safe client for outbound merchant webhooks (SD-409, ADR 0004 §7.3 option E). Each request:
 * <ul>
 * <li>is checked by {@link WebhookDestinationValidator} first, so URL rules apply to every attempt;</li>
 * <li>connects only to addresses returned by {@link ValidatingDnsResolver}, the only address source;</li>
 * <li>uses a new connection, so a pooled connection is never reused for a host that now fails the
 * checks;</li>
 * <li>never follows redirects, uses no proxy (neither HTTP nor the JVM's SOCKS settings) and makes no
 * automatic retries;</li>
 * <li>keeps standard TLS certificate and host name verification against the URL's host name.</li>
 * </ul>
 * The response body is not read. Signing, retries and delivery records belong to the sender.
 */
public class WebhookHttpClient implements AutoCloseable {

    private static final int MAX_CONNECTIONS = 20;

    // Defense in depth: a plain Socket() may consult the JVM's SOCKS settings; webhooks connect directly.
    private static final DetachedSocketFactory DIRECT_SOCKETS = new DetachedSocketFactory() {
        @Override
        public Socket create(Proxy socksProxy) {
            return new Socket(Proxy.NO_PROXY);
        }

        @Override
        public Socket create(String schemeName, Proxy socksProxy) {
            return new Socket(Proxy.NO_PROXY);
        }
    };

    private final WebhookDestinationValidator validator;
    private final CloseableHttpClient httpClient;
    private final RestClient restClient;

    WebhookHttpClient(WebhookDestinationValidator validator, DnsResolver dnsResolver,
            WebhookClientProperties properties, SSLContext sslContext) {
        this.validator = validator;
        this.httpClient = buildHttpClient(dnsResolver, properties, sslContext);
        this.restClient = RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient))
                .build();
    }

    /**
     * Sends {@code body} as JSON with the given headers.
     *
     * @return the outcome; never throws for network or destination problems
     */
    public WebhookCallResult post(String url, Map<String, String> headers, byte[] body) {
        long start = System.nanoTime();
        URI uri;
        try {
            uri = validator.check(url);
        } catch (RefusedDestinationException e) {
            return WebhookCallResult.failed(WebhookCallResult.Outcome.REFUSED_DESTINATION, since(start));
        } catch (UnknownHostException e) {
            return WebhookCallResult.failed(WebhookCallResult.Outcome.NETWORK_ERROR, since(start));
        }
        try {
            return restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(httpHeaders -> headers.forEach(httpHeaders::set))
                    .body(body)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        // Only these statuses ask the client to wait (RFC 9110 §10.2.3)
                        String retryAfter = status == 429 || status == 503
                                ? response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)
                                : null;
                        return WebhookCallResult.responded(status, since(start), retryAfter);
                    });
        } catch (RestClientException e) {
            return WebhookCallResult.failed(classify(e), since(start));
        }
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }

    private static WebhookCallResult.Outcome classify(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof RefusedDestinationException) {
                return WebhookCallResult.Outcome.REFUSED_DESTINATION;
            }
            // SocketTimeoutException and HttpClient's ConnectTimeoutException are both InterruptedIOException.
            if (cause instanceof InterruptedIOException) {
                return WebhookCallResult.Outcome.TIMEOUT;
            }
        }
        return WebhookCallResult.Outcome.NETWORK_ERROR;
    }

    private static Duration since(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }

    private static CloseableHttpClient buildHttpClient(DnsResolver dnsResolver,
            WebhookClientProperties properties, SSLContext sslContext) {
        Timeout connectTimeout = Timeout.ofMilliseconds(properties.connectTimeout().toMillis());
        Timeout responseTimeout = Timeout.ofMilliseconds(properties.responseTimeout().toMillis());

        var connectionManager = new PoolingHttpClientConnectionManagerBuilder() {
            @Override
            protected HttpClientConnectionOperator createConnectionOperator(SchemePortResolver schemePortResolver,
                    DnsResolver resolver, TlsSocketStrategy tlsSocketStrategy) {
                return new DefaultHttpClientConnectionOperator(DIRECT_SOCKETS, schemePortResolver, resolver,
                        RegistryBuilder.<TlsSocketStrategy>create()
                                .register(URIScheme.HTTPS.id, tlsSocketStrategy)
                                .build());
            }
        }
                .setDnsResolver(dnsResolver)
                // Default host name verifier; never a trust-all or no-op verifier.
                .setTlsSocketStrategy(new DefaultClientTlsStrategy(sslContext))
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(connectTimeout)
                        .setSocketTimeout(responseTimeout)
                        .build())
                .setMaxConnTotal(MAX_CONNECTIONS)
                .setMaxConnPerRoute(MAX_CONNECTIONS)
                .build();

        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                // Explicit direct routes: no proxy from system properties or a ProxySelector.
                .setRoutePlanner(new DefaultRoutePlanner(DefaultSchemePortResolver.INSTANCE))
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .disableCookieManagement()
                .disableAuthCaching()
                .setConnectionReuseStrategy((request, response, context) -> false)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(connectTimeout)
                        .setResponseTimeout(responseTimeout)
                        .setRedirectsEnabled(false)
                        .build())
                .setUserAgent("SwiftDrop-Webhooks/1")
                .build();
    }
}
