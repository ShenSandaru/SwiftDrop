package com.swiftdrop.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.swiftdrop.identity.application.GoogleExchangeService;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCode;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.Role;
import com.swiftdrop.identity.domain.User;
import com.swiftdrop.identity.domain.UserRepository;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
class GoogleExchangeApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private GoogleExchangeService exchangeService;
    @MockitoBean
    private GoogleLoginCodeRepository codeRepository;
    @MockitoBean
    private UserRepository users;
    @MockitoBean
    private ExternalIdentityRepository identities;

    private final Map<String, GoogleLoginCode> storedCodes = new HashMap<>();

    @BeforeEach
    void setUp() {
        storedCodes.clear();
        when(codeRepository.save(any(GoogleLoginCode.class))).thenAnswer(call -> {
            GoogleLoginCode code = call.getArgument(0);
            storedCodes.put(code.getCodeHash(), code);
            return code;
        });
        when(codeRepository.markUsedIfValid(any(), any())).thenAnswer(call -> {
            String hash = call.getArgument(0);
            Instant now = call.getArgument(1);
            GoogleLoginCode code = storedCodes.get(hash);
            if (code != null && code.getUsedAt() == null && code.getExpiresAt().isAfter(now)) {
                ReflectionTestUtils.setField(code, "usedAt", now);
                return 1;
            }
            return 0;
        });
        when(codeRepository.findById(any())).thenAnswer(call -> {
            String hash = call.getArgument(0);
            return Optional.ofNullable(storedCodes.get(hash));
        });
    }

    @Test
    void successfulExchangeReturnsAccessTokenWithoutRequiringAuthentication() throws Exception {
        User user = withId(new User("bob@example.com", null, "Bob", Set.of(Role.CUSTOMER)));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        String rawCode = exchangeService.createExchangeCode(user.getId());

        String responseBody = mvc.perform(post("/api/v1/auth/google/exchanges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"%s\"}".formatted(rawCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(responseBody, "$.accessToken");

        // Verify the issued token works to authenticate against protected endpoints
        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value("bob@example.com"));
    }

    @Test
    void exchangeRejectsReplayOfAlreadyUsedCode() throws Exception {
        User user = withId(new User("bob@example.com", null, "Bob", Set.of(Role.CUSTOMER)));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        String rawCode = exchangeService.createExchangeCode(user.getId());

        // First exchange succeeds
        mvc.perform(post("/api/v1/auth/google/exchanges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"%s\"}".formatted(rawCode)))
                .andExpect(status().isOk());

        // Second exchange with identical code is rejected
        mvc.perform(post("/api/v1/auth/google/exchanges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"%s\"}".formatted(rawCode)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Invalid or expired exchange code"));
    }

    @Test
    void exchangeRejectsInvalidOrNonExistentCode() throws Exception {
        mvc.perform(post("/api/v1/auth/google/exchanges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"nonexistent-code-1234567890\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Invalid or expired exchange code"));
    }

    @Test
    void exchangeRejectsExpiredCode() throws Exception {
        User user = withId(new User("bob@example.com", null, "Bob", Set.of(Role.CUSTOMER)));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        String rawCode = "valid-format-expired-code-1234567890";
        String hash = GoogleExchangeService.hash(rawCode);
        Instant now = Instant.now();
        // Expired 10 seconds ago
        GoogleLoginCode expiredCode = new GoogleLoginCode(
                hash, user.getId(), now.minusSeconds(70), now.minusSeconds(10), null);
        storedCodes.put(hash, expiredCode);

        mvc.perform(post("/api/v1/auth/google/exchanges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"%s\"}".formatted(rawCode)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Invalid or expired exchange code"));
    }

    @Test
    void exchangeRejectsCodeForDisabledUser() throws Exception {
        User disabledUser = withId(new User("disabled@example.com", null, "Disabled", Set.of(Role.CUSTOMER)));
        ReflectionTestUtils.setField(disabledUser, "enabled", false);
        when(users.findById(disabledUser.getId())).thenReturn(Optional.of(disabledUser));

        String rawCode = exchangeService.createExchangeCode(disabledUser.getId());

        mvc.perform(post("/api/v1/auth/google/exchanges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"%s\"}".formatted(rawCode)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Invalid or expired exchange code"));
    }

    @Test
    void exchangeRejectsBlankOrMissingCodeWithBadRequest() throws Exception {
        mvc.perform(post("/api/v1/auth/google/exchanges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidFields", contains("code")));

        mvc.perform(post("/api/v1/auth/google/exchanges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidFields", contains("code")));

        mvc.perform(post("/api/v1/auth/google/exchanges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOnExchangeEndpointIsDeniedWithoutJwt() throws Exception {
        // GET method on exchange endpoint is not permitted publicly; falls back to authenticated
        mvc.perform(get("/api/v1/auth/google/exchanges"))
                .andExpect(status().isUnauthorized());
    }

    private static User withId(User user) {
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }
}
