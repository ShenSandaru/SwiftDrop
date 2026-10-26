package com.swiftdrop.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TestFixtureController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void validationErrorsReturnBadRequestWithSortedFieldsAndNoValuesEchoed() throws Exception {
        mvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": " ", "count": -5, "secret": "leaked-secret-value"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Invalid request"))
                .andExpect(jsonPath("$.invalidFields", contains("count", "name")))
                .andExpect(result -> {
                    String content = result.getResponse().getContentAsString();
                    assertThat(content).doesNotContain("leaked-secret-value", "-5");
                });
    }

    @Test
    void malformedBodyReturnsBadRequest() throws Exception {
        mvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json-content"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Malformed request body"));
    }

    @Test
    void optimisticLockingFailureReturnsConflict() throws Exception {
        mvc.perform(get("/test/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("The resource was updated concurrently; please reload and try again"));
    }

    @Test
    void accessDeniedReturnsForbidden() throws Exception {
        mvc.perform(get("/test/access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("Access denied"));
    }

    @Test
    void responseStatusExceptionPreservesStatusAndDetail() throws Exception {
        mvc.perform(get("/test/response-status"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Resource not found"));
    }

    @Test
    void unexpectedExceptionReturnsGeneric500WithoutLeakingStackOrMessage() throws Exception {
        mvc.perform(get("/test/unexpected-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("Unexpected error"))
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body).doesNotContain("sensitive internal db password failure", "java.lang.RuntimeException");
                });
    }

    record SampleRequest(
            @NotBlank String name,
            @Positive Integer count,
            String secret) {
    }

    @RestController
    @RequestMapping("/test")
    static class TestFixtureController {

        @PostMapping("/validation")
        String validate(@Valid @RequestBody SampleRequest request) {
            return "ok";
        }

        @GetMapping("/optimistic-lock")
        String optimisticLock() {
            throw new OptimisticLockingFailureException("concurrent update");
        }

        @GetMapping("/access-denied")
        String accessDenied() {
            throw new AccessDeniedException("denied");
        }

        @GetMapping("/response-status")
        String responseStatus() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");
        }

        @GetMapping("/unexpected-error")
        String unexpected() {
            throw new RuntimeException("sensitive internal db password failure at line 42");
        }
    }
}
