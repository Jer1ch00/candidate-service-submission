package com.recruiting.candidates.util;

import org.slf4j.MDC;

/**
 * Where the current request's correlation id lives. The request-id filter writes it; the exception handler and the log
 * pattern ({@code %X{requestId}} in application.yml) read it. Kept apart from both, so the filter and exception
 * packages do not depend on each other.
 */
public final class RequestContext {

    /** HTTP header that carries the correlation id in requests and responses. */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** Logging-context (MDC) key holding the correlation id for the current thread. */
    public static final String REQUEST_ID_KEY = "requestId";

    private RequestContext() {}

    /** The current request's correlation id, or {@code null} outside a request. */
    public static String currentRequestId() {
        return MDC.get(REQUEST_ID_KEY);
    }
}
