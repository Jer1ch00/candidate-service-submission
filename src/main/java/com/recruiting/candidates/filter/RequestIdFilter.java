package com.recruiting.candidates.filter;

import static com.recruiting.candidates.util.RequestContext.REQUEST_ID_HEADER;
import static com.recruiting.candidates.util.RequestContext.REQUEST_ID_KEY;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request a correlation id: reuses a well-formed {@code X-Request-Id} from the caller (so ids flow across
 * services) or generates one. The id is put in the logging context (MDC), echoed in the response header and included
 * in every error body, so a client report can be matched to server logs.
 */
public class RequestIdFilter extends OncePerRequestFilter {

    /** Accept only short, log-safe ids; anything else is replaced to prevent log injection. */
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
        MDC.put(REQUEST_ID_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(REQUEST_ID_KEY);
        }
    }

    static String resolveRequestId(String candidate) {
        if (candidate != null && VALID_REQUEST_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }
}
