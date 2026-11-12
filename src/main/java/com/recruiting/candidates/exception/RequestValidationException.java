package com.recruiting.candidates.exception;

import java.util.List;
import java.util.Map;

/**
 * Input that passed per-field validation but is invalid as a whole, e.g. {@code minExperience > maxExperience} or an
 * unknown sort field. Rendered exactly like Bean Validation failures so clients handle a single shape.
 */
public class RequestValidationException extends ApplicationException {

    public RequestValidationException(String field, String message) {
        this(List.of(new FieldViolation(field, message)));
    }

    public RequestValidationException(List<FieldViolation> violations) {
        super(ErrorCode.VALIDATION_FAILED, "Request contains invalid fields.", Map.of(), violations);
    }
}
