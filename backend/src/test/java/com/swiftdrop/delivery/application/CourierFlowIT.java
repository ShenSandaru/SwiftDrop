package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.swiftdrop.delivery.api.ParcelAssignment;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.api.FeeQuote;

/**
 * A parcel's whole journey over real HTTP with real login tokens, against real PostgreSQL: booking,
 * assignment, every courier step, proof upload (including the servlet's upload limit) and the
 * customer's receipt and photo. Also the brief's TST-2 example: a courier cannot access another
 * courier's deliveries. Pricing is mocked; assignment is called directly until dispatch exists.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password="
})
@Testcontainers
class CourierFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Test-Password-123";
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};

    @LocalServerPort
    private int port;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ParcelAssignment assignment;

    @MockitoBean
    private FeeCalculator feeCalculator;

    private RestClient http;
    private UUID customerId;
    private String customer;
    private UUID courierAId;
    private String courierA;
    private String courierB;

    @BeforeEach
    void setUp() {
        when(feeCalculator.calculateFee(any(), any(), any(), anyInt())).thenReturn(new FeeQuote(
                new BigDecimal("850.00"), "LKR", new BigDecimal("550.00"), new BigDecimal("200.00"),
                new BigDecimal("100.00"), UUID.randomUUID()));
        // Report every status instead of throwing, so tests can assert on 4xx answers
        http = RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
        customerId = insertUser("customer", "CUSTOMER");
        customer = login(customerId);
        courierAId = insertUser("courier-a", "COURIER");
        courierA = login(courierAId);
        courierB = login(insertUser("courier-b", "COURIER"));
    }

    @Test
    void parcelTravelsFromBookingToDeliveredAndTheReceiptShowsTheRecipient() {
        JsonNode booked = body(post(customer, "/api/v1/parcels", bookingJson()), HttpStatus.CREATED);
        String parcelId = booked.get("id").asString();
        String trackingNumber = booked.get("trackingNumber").asString();
        assignment.assignCourier(UUID.fromString(parcelId), courierAId, UUID.randomUUID());

        JsonNode today = body(get(courierA, "/api/v1/courier/parcels"), HttpStatus.OK);
        assertThat(today).hasSize(1);
        assertThat(today.get(0).get("nextStatuses").get(0).asString()).isEqualTo("PICKED_UP");

        for (String step : new String[] {"PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY"}) {
            assertThat(changeStatus(courierA, parcelId, "{\"toStatus\":\"" + step + "\"}").getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }
        // DELIVERED needs proof first (ADR 0006 §5)
        assertThat(changeStatus(courierA, parcelId, "{\"toStatus\":\"DELIVERED\"}").getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        JsonNode proof = body(uploadProof(courierA, parcelId, "Kumari Silva", JPEG), HttpStatus.OK);
        assertThat(proof.get("hasPhoto").asBoolean()).isTrue();
        JsonNode delivered = body(changeStatus(courierA, parcelId, "{\"toStatus\":\"DELIVERED\",\"note\":\"Handed over\"}"),
                HttpStatus.OK);
        assertThat(delivered.get("status").asString()).isEqualTo("DELIVERED");
        assertThat(delivered.get("history")).hasSize(5);

        JsonNode receipt = body(get(customer, "/api/v1/parcels/" + parcelId + "/receipt"), HttpStatus.OK);
        assertThat(receipt.get("recipientName").asString()).isEqualTo("Kumari Silva");
        assertThat(receipt.get("deliveredAt").isNull()).isFalse();

        ResponseEntity<byte[]> photo = http.get().uri("/api/v1/parcels/" + parcelId + "/proof/photo")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customer).retrieve().toEntity(byte[].class);
        assertThat(photo.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(photo.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(photo.getBody()).isEqualTo(JPEG);

        // Public tracking: no login, no personal data
        String tracked = http.get().uri("/api/v1/tracked-parcels/" + trackingNumber.toLowerCase())
                .retrieve().body(String.class);
        assertThat(tracked).contains("\"status\":\"DELIVERED\"").doesNotContain("Kumari", "Nimal", "+9477", "Handed");

        // Finished today, so still on today's list, with no further steps
        JsonNode stillToday = body(get(courierA, "/api/v1/courier/parcels"), HttpStatus.OK);
        assertThat(stillToday.get(0).get("nextStatuses")).isEmpty();
        assertThat(changeStatus(courierA, parcelId, "{\"toStatus\":\"FAILED\",\"failureReason\":\"OTHER\"}")
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM parcel_outbox_events WHERE parcel_id = ?::uuid",
                Integer.class, parcelId)).isEqualTo(5);
    }

    @Test
    void courierCannotSeeOrChangeAnotherCouriersDelivery() {
        String parcelId = body(post(customer, "/api/v1/parcels", bookingJson()), HttpStatus.CREATED).get("id").asString();
        assignment.assignCourier(UUID.fromString(parcelId), courierAId, UUID.randomUUID());

        assertThat(body(get(courierB, "/api/v1/courier/parcels"), HttpStatus.OK)).isEmpty();
        assertThat(get(courierB, "/api/v1/parcels/" + parcelId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(changeStatus(courierB, parcelId, "{\"toStatus\":\"PICKED_UP\"}").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(uploadProof(courierB, parcelId, "Someone", JPEG).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(courierB, "/api/v1/parcels/" + parcelId + "/proof/photo").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT status FROM parcels WHERE id = ?::uuid", String.class, parcelId))
                .isEqualTo("CREATED");
    }

    @Test
    void failedDeliveryNeedsAReasonAndTheReceiptShowsIt() {
        String parcelId = body(post(customer, "/api/v1/parcels", bookingJson()), HttpStatus.CREATED).get("id").asString();
        assignment.assignCourier(UUID.fromString(parcelId), courierAId, UUID.randomUUID());
        changeStatus(courierA, parcelId, "{\"toStatus\":\"PICKED_UP\"}");

        assertThat(changeStatus(courierA, parcelId, "{\"toStatus\":\"FAILED\"}").getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(changeStatus(courierA, parcelId,
                "{\"toStatus\":\"FAILED\",\"failureReason\":\"DAMAGED\",\"note\":\"Box crushed\"}").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        JsonNode receipt = body(get(customer, "/api/v1/parcels/" + parcelId + "/receipt"), HttpStatus.OK);
        assertThat(receipt.get("failureReason").asString()).isEqualTo("DAMAGED");
        assertThat(receipt.get("recipientName").isNull()).isTrue();
        // Customers do not see courier notes
        assertThat(get(customer, "/api/v1/parcels/" + parcelId).getBody()).doesNotContain("Box crushed");
    }

    @Test
    void uploadsAreCheckedByContentAndSize() {
        String parcelId = body(post(customer, "/api/v1/parcels", bookingJson()), HttpStatus.CREATED).get("id").asString();
        assignment.assignCourier(UUID.fromString(parcelId), courierAId, UUID.randomUUID());
        assertThat(uploadProof(courierA, parcelId, "Kumari", JPEG).getStatusCode())
                .as("proof before out for delivery").isEqualTo(HttpStatus.CONFLICT);
        for (String step : new String[] {"PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY"}) {
            changeStatus(courierA, parcelId, "{\"toStatus\":\"" + step + "\"}");
        }

        assertThat(uploadProof(courierA, parcelId, "Kumari", "<html>not a photo</html>".getBytes()).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        byte[] tooLarge = new byte[2 * 1024 * 1024 + 1];
        System.arraycopy(JPEG, 0, tooLarge, 0, JPEG.length);
        ResponseEntity<String> rejected = uploadProof(courierA, parcelId, "Kumari", tooLarge);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rejected.getBody()).contains("at most 2 MB");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delivery_proofs WHERE parcel_id = ?::uuid", Integer.class,
                parcelId)).isZero();
    }

    private ResponseEntity<String> get(String token, String path) {
        return http.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve().toEntity(String.class);
    }

    private ResponseEntity<String> post(String token, String path, String json) {
        return http.post().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).body(json).retrieve().toEntity(String.class);
    }

    private ResponseEntity<String> changeStatus(String token, String parcelId, String json) {
        return post(token, "/api/v1/parcels/" + parcelId + "/status-changes", json);
    }

    private ResponseEntity<String> uploadProof(String token, String parcelId, String recipientName, byte[] photo) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("recipientName", recipientName);
        form.add("photo", new ByteArrayResource(photo) {
            @Override
            public String getFilename() {
                return "door.jpg";
            }
        });
        return http.put().uri("/api/v1/parcels/" + parcelId + "/proof")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().toEntity(String.class);
    }

    private static JsonNode body(ResponseEntity<String> response, HttpStatus expected) {
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(expected);
        return JSON.readTree(response.getBody());
    }

    private UUID insertUser(String name, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, password_hash, full_name, enabled, created_at) "
                + "VALUES (?, ?, ?, ?, TRUE, now())", id, email(id), passwordEncoder.encode(PASSWORD), "Test " + name);
        jdbc.update("INSERT INTO user_roles (user_id, role_name) VALUES (?, ?)", id, role);
        return id;
    }

    private String login(UUID userId) {
        String response = http.post().uri("/api/v1/auth/tokens").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email(userId), "password", PASSWORD)).retrieve().body(String.class);
        return JSON.readTree(response).get("accessToken").asString();
    }

    private static String email(UUID userId) {
        return "user-" + userId + "@example.com";
    }

    private static String bookingJson() {
        String pickup = "{\"contactName\":\"Nimal Perera\",\"phone\":\"+94771234567\",\"line1\":\"12 Temple Rd\","
                + "\"city\":\"Colombo\",\"zoneId\":\"" + UUID.randomUUID() + "\"}";
        String dropoff = "{\"contactName\":\"Kumari Silva\",\"phone\":\"+94711234567\",\"line1\":\"4 Lake View\","
                + "\"city\":\"Kandy\",\"zoneId\":\"" + UUID.randomUUID() + "\"}";
        return "{\"pickup\":" + pickup + ",\"dropoff\":" + dropoff + ",\"size\":\"MEDIUM\",\"weightGrams\":2000}";
    }
}
