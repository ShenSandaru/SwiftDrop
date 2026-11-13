package com.swiftdrop.merchant.infrastructure.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The demo receiver in {@code tools/webhook-receiver} (SD-413) as merchants would run it, with requests signed by
 * {@link WebhookSignature}: the reference behavior of ADR 0004 §7.2 (signature, tolerance, duplicates, sequence).
 */
class WebhookReceiverTest {

    private static final Path RECEIVER = Path.of("..", "tools", "webhook-receiver", "WebhookReceiver.java");
    private static final String SECRET = "whsec_receiver-test-secret";
    private static final Pattern LISTENING = Pattern.compile("Listening on (http://127\\.0\\.0\\.1:\\d+/hook)");

    private final HttpClient http = HttpClient.newHttpClient();
    private Process receiver;

    @AfterEach
    void stop() {
        if (receiver != null) {
            receiver.destroyForcibly();
        }
    }

    private String start(String... extraArgs) throws Exception {
        assertThat(Files.exists(RECEIVER)).as("receiver source at " + RECEIVER.toAbsolutePath()).isTrue();
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> command = new ArrayList<>(List.of(javaBin, RECEIVER.toString(), "--port", "0", "--secret", SECRET));
        command.addAll(List.of(extraArgs));
        receiver = new ProcessBuilder(command).redirectErrorStream(true).start();
        BufferedReader output = new BufferedReader(new InputStreamReader(receiver.getInputStream(),
                StandardCharsets.UTF_8));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        String line;
        while (System.nanoTime() < deadline && (line = output.readLine()) != null) {
            Matcher matcher = LISTENING.matcher(line);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        throw new AssertionError("receiver did not start");
    }

    private static String event(String eventId, String parcelId, String status, int sequence) {
        return """
                {"eventId":"%s","eventType":"parcel.status-changed","schemaVersion":1,"occurredAt":"2026-11-16T10:00:00Z",\
                "producer":"delivery","data":{"parcelId":"%s","newStatus":"%s","sequence":%d}}"""
                .formatted(eventId, parcelId, status, sequence);
    }

    private HttpResponse<String> send(String url, String eventId, String body, String secret, long timestamp)
            throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header(WebhookSignature.EVENT_ID_HEADER, eventId)
                .header(WebhookSignature.EVENT_TYPE_HEADER, "parcel.status-changed")
                .header(WebhookSignature.TIMESTAMP_HEADER, Long.toString(timestamp))
                .header(WebhookSignature.SIGNATURE_HEADER, WebhookSignature.sign(secret, timestamp, bytes))
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> send(String url, String eventId, String body) throws Exception {
        return send(url, eventId, body, SECRET, Instant.now().getEpochSecond());
    }

    @Test
    @DisplayName("Accepts a valid request, then reports the same event ID as a duplicate")
    void validThenDuplicate() throws Exception {
        String url = start();
        String id = UUID.randomUUID().toString();
        String body = event(id, "p-1", "CREATED", 1);

        assertThat(send(url, id, body)).satisfies(r -> {
            assertThat(r.statusCode()).isEqualTo(200);
            assertThat(r.body()).isEqualTo("ok");
        });
        assertThat(send(url, id, body).body()).isEqualTo("duplicate");
    }

    @Test
    @DisplayName("Rejects a changed body, a wrong secret and an old timestamp with 401")
    void rejects() throws Exception {
        String url = start();
        String id = UUID.randomUUID().toString();
        String body = event(id, "p-2", "CREATED", 1);
        long now = Instant.now().getEpochSecond();
        byte[] signedBody = body.getBytes(StandardCharsets.UTF_8);

        HttpRequest tampered = HttpRequest.newBuilder(URI.create(url))
                .header(WebhookSignature.EVENT_ID_HEADER, id)
                .header(WebhookSignature.TIMESTAMP_HEADER, Long.toString(now))
                .header(WebhookSignature.SIGNATURE_HEADER, WebhookSignature.sign(SECRET, now, signedBody))
                .POST(HttpRequest.BodyPublishers.ofString(body.replace("CREATED", "DELIVERED")))
                .build();
        assertThat(http.send(tampered, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        assertThat(send(url, id, body, "whsec_wrong-secret", now).statusCode()).isEqualTo(401);
        assertThat(send(url, id, body, SECRET, now - 600).statusCode()).isEqualTo(401);
        // None of them was recorded: the valid request is processed, not a duplicate
        assertThat(send(url, id, body).body()).isEqualTo("ok");
    }

    @Test
    @DisplayName("--fail-first fails before recording, so the retry of the same event is processed")
    void failFirstDoesNotRecord() throws Exception {
        String url = start("--fail-first", "2");
        String id = UUID.randomUUID().toString();
        String body = event(id, "p-3", "CREATED", 1);

        assertThat(send(url, id, body).statusCode()).isEqualTo(500);
        assertThat(send(url, id, body).statusCode()).isEqualTo(500);
        HttpResponse<String> third = send(url, id, body);
        assertThat(third.statusCode()).isEqualTo(200);
        assertThat(third.body()).isEqualTo("ok");
    }

    @Test
    @DisplayName("An older sequence of a parcel is acknowledged but not applied")
    void highestSequenceWins() throws Exception {
        String url = start();

        assertThat(send(url, "e-3", event("e-3", "p-4", "AT_HUB", 3)).body()).isEqualTo("ok");
        assertThat(send(url, "e-2", event("e-2", "p-4", "PICKED_UP", 2)).body()).isEqualTo("stale");
        assertThat(send(url, "e-4", event("e-4", "p-4", "IN_TRANSIT", 4)).body()).isEqualTo("ok");
    }
}
