package com.swiftdrop.tracking.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.DispatcherType;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.swiftdrop.shared.web.ClientRateLimiter;

class TrackingRateLimitInterceptorTest {

    private final TrackingRateLimitInterceptor interceptor = new TrackingRateLimitInterceptor(new ClientRateLimiter(2));

    @Test
    void letsAClientThroughUpToTheLimitThenAnswers429() {
        assertThat(interceptor.preHandle(from("203.0.113.5"), new MockHttpServletResponse(), this)).isTrue();
        assertThat(interceptor.preHandle(from("203.0.113.5"), new MockHttpServletResponse(), this)).isTrue();

        MockHttpServletResponse refused = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(from("203.0.113.5"), refused, this)).isFalse();
        assertThat(refused.getStatus()).isEqualTo(429);
        assertThat(refused.getHeader("Retry-After")).isEqualTo("60");
        assertThat(refused.getContentAsByteArray()).isEmpty();
    }

    @Test
    void countsEachClientSeparately() {
        interceptor.preHandle(from("203.0.113.5"), new MockHttpServletResponse(), this);
        interceptor.preHandle(from("203.0.113.5"), new MockHttpServletResponse(), this);

        assertThat(interceptor.preHandle(from("203.0.113.6"), new MockHttpServletResponse(), this)).isTrue();
    }

    @Test
    void theEndOfAStreamIsNotCountedAsARequest() {
        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest asyncDispatch = from("203.0.113.5");
            asyncDispatch.setDispatcherType(DispatcherType.ASYNC);
            assertThat(interceptor.preHandle(asyncDispatch, new MockHttpServletResponse(), this)).isTrue();
        }
        assertThat(interceptor.preHandle(from("203.0.113.5"), new MockHttpServletResponse(), this)).isTrue();
    }

    private static MockHttpServletRequest from(String address) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tracked-parcels/SD7K3M9Q2XWB");
        request.setRemoteAddr(address);
        return request;
    }
}
