package com.swiftdrop.tracking.web;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.swiftdrop.shared.web.ClientRateLimiter;

/**
 * Limits how often one client may call the public tracking endpoints, the lookup and the live
 * stream together. They need no login, so without a limit anyone could flood them or try tracking
 * numbers in bulk. Answered with a status and no body, like the other stream errors: the stream
 * client asked for {@code text/event-stream} and {@code EventSource} only looks at the status.
 */
@Component
class TrackingRateLimitInterceptor implements HandlerInterceptor {

    static final String RETRY_AFTER_SECONDS = "60";

    private final ClientRateLimiter limiter;

    @Autowired
    TrackingRateLimitInterceptor(@Value("${swiftdrop.tracking.rate-limit-per-minute:60}") int limitPerMinute) {
        this(new ClientRateLimiter(limitPerMinute));
    }

    TrackingRateLimitInterceptor(ClientRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // A stream is dispatched again when it ends; only the client's own request counts
        if (request.getDispatcherType() != DispatcherType.REQUEST || limiter.tryAcquire(request.getRemoteAddr())) {
            return true;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
        return false;
    }
}
