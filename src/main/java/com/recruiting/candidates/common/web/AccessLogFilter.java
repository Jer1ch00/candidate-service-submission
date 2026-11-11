package com.recruiting.candidates.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * One log line per request: method, route, status and latency.
 *
 * <p>Logs the route template ({@code /api/v1/candidates/{id}}) when one matched, which keeps lines aggregatable. It
 * never logs the query string or bodies: search terms are candidate names and bodies contain personal data.
 */
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("http.access");

    /** Pattern of the static-resource fallback handler, which "matches" every unknown path. */
    private static final String CATCH_ALL_PATTERN = "/**";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long startNanos = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
            log.info("{} {} -> {} ({} ms)", request.getMethod(), route(request), response.getStatus(), elapsedMillis);
        }
    }

    /** The matched route template, or the raw path when no endpoint matched (404s, rejected requests). */
    private static String route(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        boolean matchedEndpoint = pattern != null && !CATCH_ALL_PATTERN.equals(pattern);
        return matchedEndpoint ? pattern.toString() : request.getRequestURI();
    }
}
