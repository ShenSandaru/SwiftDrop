import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Demo receiver for SwiftDrop webhooks (SD-413). Shows what a merchant's endpoint must do with each request:
 *
 * <ol>
 * <li>Verify {@code X-SwiftDrop-Signature} over the raw body before parsing it: HMAC-SHA256 of
 *     {@code "<timestamp>." + body}, keyed with the signing secret, compared in constant time; reject timestamps
 *     more than 5 minutes from the local clock. Otherwise answer 401.</li>
 * <li>Deduplicate by {@code X-SwiftDrop-Event-Id}: a repeated event answers 200 without being applied again.</li>
 * <li>Apply only the highest {@code sequence} per parcel: an older event (retries can reorder) is acknowledged but
 *     does not overwrite a newer status.</li>
 * </ol>
 *
 * <p>Run on JDK 21 without building anything:
 * <pre>java tools/webhook-receiver/WebhookReceiver.java --port 9000 --secret whsec_... [--fail-first 3]</pre>
 * {@code --fail-first N} answers 500 to the first N valid requests <em>before</em> recording them, so the event is
 * not marked processed and SwiftDrop's retry or a manual redelivery is handled normally. Listens on loopback only.
 * The JSON parsing here is deliberately minimal; use a JSON library in a real integration.
 */
public final class WebhookReceiver {

    private static final long TOLERANCE_SECONDS = 300;
    private static final Pattern EVENT_ID = Pattern.compile("\"eventId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern PARCEL_ID = Pattern.compile("\"parcelId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern NEW_STATUS = Pattern.compile("\"newStatus\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SEQUENCE = Pattern.compile("\"sequence\"\\s*:\\s*(\\d+)");

    private final byte[] secret;
    private final AtomicInteger failuresLeft;
    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> highestSequence = new ConcurrentHashMap<>();

    private WebhookReceiver(String secret, int failFirst) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.failuresLeft = new AtomicInteger(failFirst);
    }

    public static void main(String[] args) throws IOException {
        int port = 9000;
        String secret = null;
        int failFirst = 0;
        String path = "/hook";
        for (int i = 0; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[i + 1]);
                case "--secret" -> secret = args[i + 1];
                case "--fail-first" -> failFirst = Integer.parseInt(args[i + 1]);
                case "--path" -> path = args[i + 1];
                default -> usage("unknown option " + args[i]);
            }
        }
        if (secret == null || secret.isBlank()) {
            usage("--secret is required (the endpoint's signing secret, whsec_...)");
        }
        WebhookReceiver receiver = new WebhookReceiver(secret, failFirst);
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext(path, receiver::handle);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.printf("Listening on http://127.0.0.1:%d%s%s%n", server.getAddress().getPort(), path,
                failFirst > 0 ? " (first " + failFirst + " valid requests answer 500)" : "");
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"POST".equals(exchange.getRequestMethod())) {
                respond(exchange, 405, "POST only");
                return;
            }
            byte[] body = exchange.getRequestBody().readAllBytes();
            String eventId = exchange.getRequestHeaders().getFirst("X-SwiftDrop-Event-Id");
            String timestamp = exchange.getRequestHeaders().getFirst("X-SwiftDrop-Timestamp");
            String signature = exchange.getRequestHeaders().getFirst("X-SwiftDrop-Signature");

            // 1. Authenticity first, on the raw bytes; nothing else is trusted before this.
            if (!verify(signature, timestamp, body)) {
                System.out.println("REJECTED   invalid signature or timestamp");
                respond(exchange, 401, "invalid signature");
                return;
            }
            // 2. Simulated outage: before anything is recorded, so a retry of this event is processed normally.
            if (failuresLeft.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
                System.out.println("FAILING    " + eventId + " (simulated 500)");
                respond(exchange, 500, "simulated failure");
                return;
            }
            // 3. Duplicates (redelivery or retry after a lost response) are acknowledged, not applied again.
            if (eventId == null || !processedEventIds.add(eventId)) {
                System.out.println("DUPLICATE  " + eventId);
                respond(exchange, 200, "duplicate");
                return;
            }
            // 4. Apply only the newest status per parcel.
            String json = new String(body, StandardCharsets.UTF_8);
            String parcelId = find(PARCEL_ID, json);
            String status = find(NEW_STATUS, json);
            int sequence = Integer.parseInt(find(SEQUENCE, json));
            int previous = highestSequence.merge(parcelId, sequence, Math::max);
            if (previous != sequence) {
                System.out.printf("STALE      %s parcel=%s status=%s sequence=%d (already at %d)%n", eventId, parcelId,
                        status, sequence, previous);
                respond(exchange, 200, "stale");
                return;
            }
            System.out.printf("RECEIVED   %s parcel=%s status=%s sequence=%d%n", find(EVENT_ID, json), parcelId,
                    status, sequence);
            respond(exchange, 200, "ok");
        }
    }

    private boolean verify(String signature, String timestamp, byte[] body) {
        if (signature == null || !signature.startsWith("v1=") || timestamp == null) {
            return false;
        }
        long seconds;
        try {
            seconds = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(Instant.now().getEpochSecond() - seconds) > TOLERANCE_SECONDS) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            mac.update((seconds + ".").getBytes(StandardCharsets.UTF_8));
            byte[] expected = mac.doFinal(body);
            byte[] presented = HexFormat.of().parseHex(signature.substring(3));
            return MessageDigest.isEqual(expected, presented);
        } catch (IllegalArgumentException e) {
            return false;
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static String find(Pattern pattern, String json) {
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : "0";
    }

    private static void respond(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void usage(String problem) {
        System.err.println(problem);
        System.err.println("usage: java WebhookReceiver.java --secret whsec_... [--port 9000] [--path /hook] "
                + "[--fail-first N]");
        System.exit(2);
    }
}
