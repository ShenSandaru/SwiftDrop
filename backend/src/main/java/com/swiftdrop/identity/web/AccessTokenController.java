package com.swiftdrop.identity.web;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.identity.application.AccountService;
import com.swiftdrop.identity.application.GoogleExchangeService;
import com.swiftdrop.identity.security.AccessTokenIssuer;

/** Email/password login and Google OAuth code redemption: exchanges credentials for an access token. */
@RestController
class AccessTokenController {

    private final AccountService accounts;
    private final AccessTokenIssuer tokens;
    private final GoogleExchangeService googleExchange;

    AccessTokenController(AccountService accounts, AccessTokenIssuer tokens, GoogleExchangeService googleExchange) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.googleExchange = googleExchange;
    }

    @PostMapping("/api/v1/auth/tokens")
    AccessTokenResponse login(@Valid @RequestBody LoginRequest request) {
        AccessTokenIssuer.AccessToken token =
                tokens.issue(accounts.authenticate(request.email(), request.password()));
        return new AccessTokenResponse(token.value(), "Bearer", token.lifetime().toSeconds());
    }

    @PostMapping("/api/v1/auth/google/exchanges")
    AccessTokenResponse exchangeGoogleCode(@Valid @RequestBody GoogleExchangeRequest request) {
        return googleExchange.exchange(request.code());
    }
}
