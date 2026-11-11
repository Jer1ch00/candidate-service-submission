package com.recruiting.candidates.common.error;

import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;

/**
 * The complete, stable error contract of the API. Clients branch on {@link #name()} (exposed as {@code code} in every
 * error body), never on the human-readable message. Adding a value is backwards compatible; renaming one is not.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "Invalid parameter"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized"),
    CANDIDATE_NOT_FOUND(HttpStatus.NOT_FOUND, "Candidate not found"),
    ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "Route not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "Not acceptable"),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "Duplicate email"),
    VERSION_CONFLICT(HttpStatus.CONFLICT, "Version conflict"),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "Invalid status transition"),
    DATA_CONFLICT(HttpStatus.CONFLICT, "Data conflict"),
    PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "Payload too large"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable");

    private static final String TYPE_PREFIX = "urn:candidate-service:problem:";

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    /** RFC 9457 problem type, e.g. {@code urn:candidate-service:problem:duplicate-email}. */
    public URI type() {
        return URI.create(TYPE_PREFIX + name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }
}
