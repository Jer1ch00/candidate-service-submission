package com.recruiting.candidates.common.web;

import com.recruiting.candidates.common.error.PayloadTooLargeException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Rejects oversized request bodies before they are read. A candidate document is a few KB; the limit protects memory
 * and parsing time. Checks the declared {@code Content-Length}; see DESIGN.md for the chunked-encoding caveat.
 */
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;
    private final HandlerExceptionResolver exceptionResolver;

    public RequestSizeLimitFilter(long maxBytes, HandlerExceptionResolver exceptionResolver) {
        this.maxBytes = maxBytes;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            // Delegate to the MVC exception handling so the error body is identical to every other error.
            exceptionResolver.resolveException(request, response, null, new PayloadTooLargeException(maxBytes));
            return;
        }
        chain.doFilter(request, response);
    }
}
