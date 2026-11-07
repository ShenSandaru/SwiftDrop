package com.swiftdrop.dispatch.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.swiftdrop.test.security.TestPrincipal;

@SpringBootTest(properties = {
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id=",
        "swiftdrop.identity.bootstrap.admin-email=",
        "swiftdrop.identity.bootstrap.admin-password="
})
@AutoConfigureMockMvc
@Testcontainers
class DispatchQueriesIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @Autowired
    private MockMvc mvc;

    @Test
    void dispatcherParcelsEndpointReturnsAnEmptyPageWithNoParcelsAndOptionalSearch() throws Exception {
        assertEmptyResponse(get("/api/v1/dispatch/parcels"));
        assertEmptyResponse(get("/api/v1/dispatch/parcels").param("search", ""));
        assertEmptyResponse(get("/api/v1/dispatch/parcels").param("search", "SD7K3M9Q2XWB"));
    }

    @Test
    void activeAssignmentEndpointsReturnEmptyPagesForBothTypesAndPageSizes() throws Exception {
        for (String type : new String[] {"PICKUP", "DELIVERY"}) {
            for (String size : new String[] {"1", "100"}) {
                mvc.perform(get("/api/v1/dispatch/active-assignments")
                                .param("type", type)
                                .param("page", "0")
                                .param("size", size)
                                .with(TestPrincipal.dispatcherAdmin().postProcessor()))
                        .andExpect(status().isOk())
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.items").isEmpty())
                        .andExpect(jsonPath("$.page").value(0))
                        .andExpect(jsonPath("$.size").value(Integer.parseInt(size)))
                        .andExpect(jsonPath("$.totalItems").value(0))
                        .andExpect(jsonPath("$.totalPages").value(0));
            }
        }
    }

    private void assertEmptyResponse(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        mvc.perform(request
                        .param("page", "0")
                        .param("size", "20")
                        .with(TestPrincipal.dispatcherAdmin().postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }
}
