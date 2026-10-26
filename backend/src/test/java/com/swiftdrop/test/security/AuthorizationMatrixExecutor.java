package com.swiftdrop.test.security;

import java.util.Collection;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Executes authorization matrix assertions (TST-2) against MockMvc instances.
 * Provides both batch execution and JUnit 5 {@link DynamicTest} factory generation.
 */
public final class AuthorizationMatrixExecutor {

    private AuthorizationMatrixExecutor() {
    }

    public static MvcResult execute(MockMvc mvc, AuthorizationMatrixRow row) throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders
                .request(row.method(), row.path())
                .with(row.principal().postProcessor());

        if (row.requestCustomizer() != null) {
            row.requestCustomizer().accept(request);
        }

        MvcResult result = mvc.perform(request).andReturn();
        int actualStatus = result.getResponse().getStatus();
        int expectedStatus = row.expectedStatus().value();

        if (actualStatus != expectedStatus) {
            String responseBody = result.getResponse().getContentAsString();
            throw new AssertionError("""
                    Authorization matrix failure: %s
                      Route: %s %s
                      Principal: %s
                      Expected HTTP status: %d (%s)
                      Actual HTTP status: %d
                      Response body: %s
                    """.formatted(
                    row.description(),
                    row.method(),
                    row.path(),
                    row.principal().name(),
                    expectedStatus,
                    row.expectedStatus().name(),
                    actualStatus,
                    responseBody
            ));
        }

        return result;
    }

    public static void executeAll(MockMvc mvc, Collection<AuthorizationMatrixRow> rows) {
        for (AuthorizationMatrixRow row : rows) {
            try {
                execute(mvc, row);
            } catch (AssertionError e) {
                throw e;
            } catch (Exception e) {
                throw new AssertionError("Exception while executing matrix row: " + row.description(), e);
            }
        }
    }

    public static Stream<DynamicTest> toDynamicTests(MockMvc mvc, Collection<AuthorizationMatrixRow> rows) {
        return rows.stream().map(row -> DynamicTest.dynamicTest(
                "[%s %s] %s as %s -> %d".formatted(
                        row.method(),
                        row.path(),
                        row.description(),
                        row.principal().name(),
                        row.expectedStatus().value()),
                () -> execute(mvc, row)
        ));
    }
}
