package com.swiftdrop.assistant.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.assistant.application.AssistantAnswer;
import com.swiftdrop.assistant.application.AssistantService;
import com.swiftdrop.assistant.application.ChatTurn;
import com.swiftdrop.assistant.application.ClientRateLimiter;

/**
 * The AI delivery assistant (ADR 0010 §4). The server keeps no conversation: the browser sends the
 * history with every message. Callers are limited per remote address.
 */
@RestController
class AssistantController {

    private static final String RETRY_AFTER_SECONDS = "60";

    private final AssistantService assistant;
    private final ClientRateLimiter rateLimiter;

    AssistantController(AssistantService assistant, ClientRateLimiter rateLimiter) {
        this.assistant = assistant;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/api/v1/assistant/messages")
    ResponseEntity<?> send(@Valid @RequestBody AssistantRequest request, HttpServletRequest http) {
        if (!rateLimiter.tryAcquire(http.getRemoteAddr())) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                    "You are sending messages too fast. Please wait a minute.");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                    .body(problem);
        }
        AssistantAnswer answer = assistant.reply(request.message(), request.historyOrEmpty().stream()
                .map(turn -> new ChatTurn(turn.role(), turn.text()))
                .toList());
        return ResponseEntity.ok(AssistantResponse.from(answer));
    }
}
