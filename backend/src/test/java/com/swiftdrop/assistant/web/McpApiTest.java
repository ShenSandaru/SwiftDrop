package com.swiftdrop.assistant.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.Zone;
import com.swiftdrop.pricing.domain.ZoneRepository;
import com.swiftdrop.test.security.TestPrincipal;

/** The MCP endpoint through HTTP with the real protocol handler and tools; repositories are mocked. */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
@MockitoBean(types = {
        com.swiftdrop.identity.domain.UserAddressRepository.class,
        com.swiftdrop.delivery.domain.ParcelAssignmentRepository.class,
        com.swiftdrop.delivery.domain.HubRepository.class,
        com.swiftdrop.merchant.domain.MerchantRepository.class,
        com.swiftdrop.merchant.domain.MerchantStatusHistoryRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookEndpointRepository.class,
        com.swiftdrop.merchant.domain.MerchantApiKeyRepository.class,
        com.swiftdrop.merchant.domain.MerchantEventStateRepository.class,
        com.swiftdrop.merchant.domain.MerchantProcessedEventRepository.class,
        com.swiftdrop.merchant.domain.MerchantPendingEventRepository.class,
        com.swiftdrop.merchant.domain.MerchantEventAnomalyRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookDeliveryRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookAttemptRepository.class,
        com.swiftdrop.merchant.domain.MerchantWebhookRedeliveryRepository.class,
        com.swiftdrop.notification.domain.NotificationRecordRepository.class
})
class McpApiTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserRepository users;
    @MockitoBean
    private ExternalIdentityRepository identities;
    @MockitoBean
    private GoogleLoginCodeRepository googleCodes;
    @MockitoBean
    private ZoneRepository zones;
    @MockitoBean
    private PricingRuleRepository pricingRules;
    @MockitoBean
    private ParcelRepository parcels;
    @MockitoBean
    private ParcelStatusHistoryRepository parcelHistory;
    @MockitoBean
    private DeliveryProofRepository deliveryProofs;
    @MockitoBean
    private OutboxEventRepository outboxEvents;

    private static final String MODERN = "2026-07-28";
    private static final String META = """
            "_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}}""";

    private MockHttpServletRequestBuilder mcp(String json) {
        return post("/mcp").with(TestPrincipal.customer().postProcessor())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .content(json);
    }

    @Test
    void legacyClientsCanInitializeAndListTheThreeTools() throws Exception {
        mvc.perform(mcp("""
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},
                 "clientInfo":{"name":"test","version":"1"}}}"""))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.result.protocolVersion").value("2025-11-25"))
                .andExpect(jsonPath("$.result.serverInfo.name").value("swiftdrop"));

        mvc.perform(mcp("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"))
                .andExpect(status().isAccepted());

        mvc.perform(mcp("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}").header("MCP-Protocol-Version", "2025-11-25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tools.length()").value(3))
                .andExpect(jsonPath("$.result.tools[0].name").value("estimate_fee"))
                .andExpect(jsonPath("$.result.tools[1].name").value("list_service_areas"))
                .andExpect(jsonPath("$.result.tools[2].name").value("track_parcel"))
                .andExpect(jsonPath("$.result.tools[2].inputSchema.required[0]").value("trackingNumber"));
    }

    @Test
    void modernClientsCallARealToolWithTheRoutingHeaders() throws Exception {
        when(zones.findByActiveTrue()).thenReturn(List.of(zone("CMB", "Colombo"), zone("KDY", "Kandy")));

        mvc.perform(mcp("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"list_service_areas\","
                + "\"arguments\":{}," + META + "}}")
                .header("MCP-Protocol-Version", MODERN).header("Mcp-Method", "tools/call").header("Mcp-Name", "list_service_areas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.resultType").value("complete"))
                .andExpect(jsonPath("$.result.isError").value(false))
                .andExpect(jsonPath("$.result.structuredContent.areas[0]").value("Colombo"))
                .andExpect(jsonPath("$.result.content[0].type").value("text"));
    }

    @Test
    void modernRequestsWithoutTheRoutingHeadersAreRejected() throws Exception {
        mvc.perform(mcp("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"server/discover\",\"params\":{" + META + "}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(-32020));

        mvc.perform(mcp("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"server/discover\",\"params\":{" + META + "}}")
                .header("MCP-Protocol-Version", MODERN).header("Mcp-Method", "server/discover"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.capabilities.tools").exists());
    }

    @Test
    void invalidJsonIsAParseError() throws Exception {
        mvc.perform(mcp("{not json")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(-32700));
        mvc.perform(mcp("")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(-32700));
    }

    @Test
    void foreignBrowserOriginsAreRefused() throws Exception {
        mvc.perform(mcp("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}").header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());
        mvc.perform(mcp("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}").header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk());
    }

    @Test
    void mcpClientsNeedNoLogin() throws Exception {
        mvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON)
                .content("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-11-25\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.capabilities.tools").exists());
        mvc.perform(get("/mcp")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void pathsNextToMcpStayProtected() throws Exception {
        mvc.perform(post("/mcp/other").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAndDeleteAreNotAllowed() throws Exception {
        mvc.perform(get("/mcp").with(TestPrincipal.customer().postProcessor()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"));
    }

    private static Zone zone(String code, String name) {
        Zone zone = new Zone(code, name);
        ReflectionTestUtils.setField(zone, "id", UUID.randomUUID());
        return zone;
    }
}
