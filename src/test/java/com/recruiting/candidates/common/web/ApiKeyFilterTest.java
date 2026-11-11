package com.recruiting.candidates.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.recruiting.candidates.common.error.UnauthorizedException;
import jakarta.servlet.FilterChain;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

class ApiKeyFilterTest {

    private static final String CURRENT_KEY = "current-key-0123456789";
    private static final String ROTATED_KEY = "previous-key-0123456789";

    private final HandlerExceptionResolver resolver = mock(HandlerExceptionResolver.class);
    private final ApiKeyFilter filter = new ApiKeyFilter(List.of(CURRENT_KEY, ROTATED_KEY), resolver);

    @ParameterizedTest
    @ValueSource(strings = {CURRENT_KEY, ROTATED_KEY})
    void everyConfiguredKeyIsAccepted(String key) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/candidates");
        request.addHeader(ApiKeyFilter.HEADER, key);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(any(), any());
        verify(resolver, never()).resolveException(any(), any(), any(), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "wrong-key-0123456789", "current-key-012345678", "current-key-0123456789 "})
    void missingOrWrongKeyIsRejectedThroughTheExceptionResolver(String key) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/candidates");
        if (key != null) {
            request.addHeader(ApiKeyFilter.HEADER, key);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        verify(resolver).resolveException(eq(request), eq(response), isNull(), any(UnauthorizedException.class));
        assertThat(response.getHeader("WWW-Authenticate")).contains(ApiKeyFilter.HEADER);
    }

    @Test
    void keysAreCaseSensitive() {
        assertThat(filter.isAccepted(CURRENT_KEY.toUpperCase())).isFalse();
    }
}
