package com.swiftdrop.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.swiftdrop.identity.domain.GoogleLoginCode;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.Role;
import com.swiftdrop.identity.domain.User;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.identity.security.AccessTokenIssuer;
import com.swiftdrop.identity.web.AccessTokenResponse;

class GoogleExchangeServiceTest {

    private static final Pattern URL_SAFE_BASE64_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private static final String TEST_TOKEN = "jwt.test.token";
    private static final Duration TEST_TTL = Duration.ofMinutes(15);

    private GoogleLoginCodeRepository codeRepository;
    private UserRepository userRepository;
    private AccessTokenIssuer tokenIssuer;
    private GoogleExchangeService service;

    @BeforeEach
    void setUp() {
        codeRepository = mock(GoogleLoginCodeRepository.class);
        userRepository = mock(UserRepository.class);
        tokenIssuer = mock(AccessTokenIssuer.class);
        service = new GoogleExchangeService(codeRepository, userRepository, tokenIssuer);
    }

    @Test
    void createExchangeCodeGeneratesSecureRandomCodeAndPersistsSha256Digest() {
        UUID userId = UUID.randomUUID();
        Instant before = Instant.now().minusMillis(50);

        String rawCode = service.createExchangeCode(userId);

        Instant after = Instant.now().plusMillis(50);

        assertThat(rawCode).isNotBlank().matches(URL_SAFE_BASE64_PATTERN);

        ArgumentCaptor<GoogleLoginCode> captor = ArgumentCaptor.forClass(GoogleLoginCode.class);
        verify(codeRepository).save(captor.capture());

        GoogleLoginCode saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getCodeHash()).isEqualTo(GoogleExchangeService.hash(rawCode));
        assertThat(saved.isUsed()).isFalse();
        assertThat(saved.getCreatedAt()).isBetween(before, after);
        assertThat(saved.getExpiresAt()).isBetween(
                before.plus(GoogleExchangeService.CODE_TTL),
                after.plus(GoogleExchangeService.CODE_TTL));

        verify(codeRepository).deleteExpiredOrUsedBefore(any(), any(), eq(GoogleExchangeService.PURGE_BATCH_LIMIT));
    }

    @Test
    void consecutiveCodesAreUnique() {
        UUID userId = UUID.randomUUID();
        String code1 = service.createExchangeCode(userId);
        String code2 = service.createExchangeCode(userId);

        assertThat(code1).isNotEqualTo(code2);
        assertThat(GoogleExchangeService.hash(code1)).isNotEqualTo(GoogleExchangeService.hash(code2));
    }

    @Test
    void successfulExchangeIssuesAccessTokenAndEnforcesSingleUse() {
        UUID userId = UUID.randomUUID();
        User user = new User("customer@example.com", null, "Customer", Set.of(Role.CUSTOMER));
        String rawCode = "valid-exchange-code-01234567890123456789012";
        String codeHash = GoogleExchangeService.hash(rawCode);

        GoogleLoginCode codeRecord = new GoogleLoginCode(codeHash, userId, Instant.now().plusSeconds(60));
        when(codeRepository.markUsedIfValid(eq(codeHash), any(Instant.class))).thenReturn(1);
        when(codeRepository.findById(codeHash)).thenReturn(Optional.of(codeRecord));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(tokenIssuer.issue(user)).thenReturn(new AccessTokenIssuer.AccessToken(TEST_TOKEN, TEST_TTL));

        AccessTokenResponse response = service.exchange(rawCode);

        assertThat(response.accessToken()).isEqualTo(TEST_TOKEN);
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);

        verify(codeRepository).markUsedIfValid(eq(codeHash), any(Instant.class));
        verify(codeRepository).deleteExpiredOrUsedBefore(any(), any(), eq(GoogleExchangeService.PURGE_BATCH_LIMIT));
    }

    @Test
    void rejectsMissingBlankOrWhitespaceOnlyCode() {
        assertThatThrownBy(() -> service.exchange(null))
                .isInstanceOf(InvalidExchangeCodeException.class);
        assertThatThrownBy(() -> service.exchange(""))
                .isInstanceOf(InvalidExchangeCodeException.class);
        assertThatThrownBy(() -> service.exchange("   "))
                .isInstanceOf(InvalidExchangeCodeException.class);

        verify(codeRepository, never()).markUsedIfValid(any(), any());
        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    void rejectsWhenCodeNotFoundExpiredOrAlreadyUsed() {
        String rawCode = "expired-or-used-code";
        String codeHash = GoogleExchangeService.hash(rawCode);
        when(codeRepository.markUsedIfValid(eq(codeHash), any(Instant.class))).thenReturn(0);

        assertThatThrownBy(() -> service.exchange(rawCode))
                .isInstanceOf(InvalidExchangeCodeException.class);

        verify(userRepository, never()).findById(any());
        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    void rejectsWhenAssociatedUserDoesNotExist() {
        UUID userId = UUID.randomUUID();
        String rawCode = "code-for-missing-user";
        String codeHash = GoogleExchangeService.hash(rawCode);

        GoogleLoginCode codeRecord = new GoogleLoginCode(codeHash, userId, Instant.now().plusSeconds(60));
        when(codeRepository.markUsedIfValid(eq(codeHash), any(Instant.class))).thenReturn(1);
        when(codeRepository.findById(codeHash)).thenReturn(Optional.of(codeRecord));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exchange(rawCode))
                .isInstanceOf(InvalidExchangeCodeException.class);

        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    void rejectsWhenAssociatedUserIsDisabled() {
        UUID userId = UUID.randomUUID();
        User disabledUser = new User("disabled@example.com", null, "Disabled", Set.of(Role.CUSTOMER));
        org.springframework.test.util.ReflectionTestUtils.setField(disabledUser, "enabled", false);

        String rawCode = "code-for-disabled-user";
        String codeHash = GoogleExchangeService.hash(rawCode);

        GoogleLoginCode codeRecord = new GoogleLoginCode(codeHash, userId, Instant.now().plusSeconds(60));
        when(codeRepository.markUsedIfValid(eq(codeHash), any(Instant.class))).thenReturn(1);
        when(codeRepository.findById(codeHash)).thenReturn(Optional.of(codeRecord));
        when(userRepository.findById(userId)).thenReturn(Optional.of(disabledUser));

        assertThatThrownBy(() -> service.exchange(rawCode))
                .isInstanceOf(InvalidExchangeCodeException.class);

        verify(tokenIssuer, never()).issue(any());
    }

    @Test
    void failureDuringTokenIssuancePropagatesExceptionForTransactionRollback() {
        UUID userId = UUID.randomUUID();
        User user = new User("user@example.com", null, "User", Set.of(Role.CUSTOMER));
        String rawCode = "code-triggering-failure";
        String codeHash = GoogleExchangeService.hash(rawCode);

        GoogleLoginCode codeRecord = new GoogleLoginCode(codeHash, userId, Instant.now().plusSeconds(60));
        when(codeRepository.markUsedIfValid(eq(codeHash), any(Instant.class))).thenReturn(1);
        when(codeRepository.findById(codeHash)).thenReturn(Optional.of(codeRecord));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(tokenIssuer.issue(user)).thenThrow(new IllegalStateException("Crypto failure"));

        assertThatThrownBy(() -> service.exchange(rawCode))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Crypto failure");
    }

    @Test
    void opportunisticCleanupFailureDoesNotAbortExchange() {
        UUID userId = UUID.randomUUID();
        User user = new User("user@example.com", null, "User", Set.of(Role.CUSTOMER));
        String rawCode = "code-with-cleanup-failure";
        String codeHash = GoogleExchangeService.hash(rawCode);

        GoogleLoginCode codeRecord = new GoogleLoginCode(codeHash, userId, Instant.now().plusSeconds(60));
        when(codeRepository.markUsedIfValid(eq(codeHash), any(Instant.class))).thenReturn(1);
        when(codeRepository.findById(codeHash)).thenReturn(Optional.of(codeRecord));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(tokenIssuer.issue(user)).thenReturn(new AccessTokenIssuer.AccessToken(TEST_TOKEN, TEST_TTL));
        doThrow(new RuntimeException("Database cleanup lock contention"))
                .when(codeRepository).deleteExpiredOrUsedBefore(any(), any(), anyInt());

        AccessTokenResponse response = service.exchange(rawCode);

        assertThat(response.accessToken()).isEqualTo(TEST_TOKEN);
    }
}
