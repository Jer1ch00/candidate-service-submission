package com.recruiting.candidates.common.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base type for every error the application raises on purpose. Code throws a subclass; {@link GlobalExceptionHandler}
 * is the only place that turns it into an HTTP response.
 *
 * <p>Messages must be safe to show to API clients and must not contain personal data (names, emails, phone numbers),
 * because they are returned in the response and written to logs.
 */
public abstract class ApplicationException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Map<String, Object> details;
    private final transient List<FieldViolation> violations;

    protected ApplicationException(ErrorCode errorCode, String message) {
        this(errorCode, message, Map.of(), List.of());
    }

    protected ApplicationException(ErrorCode errorCode, String message, Map<String, Object> details) {
        this(errorCode, message, details, List.of());
    }

    protected ApplicationException(
            ErrorCode errorCode, String message, Map<String, Object> details, List<FieldViolation> violations) {
        super(message);
        this.errorCode = errorCode;
        this.details = Collections.unmodifiableMap(new LinkedHashMap<>(details));
        this.violations = List.copyOf(violations);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    /** Extra machine-readable fields merged into the error body (e.g. the allowed status transitions). */
    public Map<String, Object> details() {
        return details;
    }

    /** Field-level problems, rendered as the {@code errors} array of the error body. */
    public List<FieldViolation> violations() {
        return violations;
    }
}
