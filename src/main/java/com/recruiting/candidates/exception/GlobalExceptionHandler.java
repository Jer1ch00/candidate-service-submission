package com.recruiting.candidates.exception;

import com.recruiting.candidates.util.RequestContext;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * The single place where exceptions become HTTP responses. Every failure, whether thrown by our code, by Spring MVC,
 * by the persistence layer or by a servlet filter, is rendered as an RFC 9457 problem document:
 *
 * <pre>{@code
 * { "type": "urn:candidate-service:problem:duplicate-email", "title": "Duplicate email", "status": 409,
 *   "detail": "A candidate with this email already exists.", "instance": "/api/v1/candidates",
 *   "code": "DUPLICATE_EMAIL", "requestId": "...", "timestamp": "...", "errors": [ {"field": "...", "message": "..."} ] }
 * }</pre>
 *
 * <p>Client errors (4xx) are logged as one WARN line; server errors (5xx) are logged at ERROR with the stack trace.
 * Responses never contain stack traces, SQL or class names.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Unique constraints that map to a dedicated error code when two concurrent writes pass the pre-check. */
    private static final Map<String, ErrorCode> CONSTRAINT_CODES =
            Map.of("uk_candidate_email", ErrorCode.DUPLICATE_EMAIL);

    private static final String RETRY_AFTER_SECONDS = "5";

    // ---------------------------------------------------------------------------------------------------------------
    // Errors raised on purpose by the application
    // ---------------------------------------------------------------------------------------------------------------

    @ExceptionHandler(ApplicationException.class)
    ResponseEntity<Object> handleApplicationException(ApplicationException ex, WebRequest request) {
        return problem(ex.errorCode(), ex.getMessage(), ex.violations(), ex.details(), new HttpHeaders(), request, ex);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Persistence and infrastructure
    // ---------------------------------------------------------------------------------------------------------------

    /** Two writes raced on the same candidate and the loser's version check failed at commit time. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Object> handleOptimisticLock(OptimisticLockingFailureException ex, WebRequest request) {
        return problem(
                ErrorCode.VERSION_CONFLICT,
                "The candidate was modified by another request. Fetch the latest version and retry.",
                List.of(),
                Map.of(),
                new HttpHeaders(),
                request,
                ex);
    }

    /** A database constraint rejected the write, typically a concurrent duplicate that passed the pre-check. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        ErrorCode code = violatedConstraint(ex)
                .map(name -> CONSTRAINT_CODES.get(name.toLowerCase(Locale.ROOT)))
                .orElse(ErrorCode.DATA_CONFLICT);
        String detail = code == ErrorCode.DUPLICATE_EMAIL
                ? "A candidate with this email already exists."
                : "The request conflicts with existing data.";
        return problem(code, detail, List.of(), Map.of(), new HttpHeaders(), request, ex);
    }

    /**
     * The database is unreachable, the pool is exhausted or a statement timed out. The process stays up, readiness
     * reports DOWN and requests succeed again as soon as the database recovers.
     */
    @ExceptionHandler({
        CannotCreateTransactionException.class,
        DataAccessResourceFailureException.class,
        TransientDataAccessResourceException.class,
        QueryTimeoutException.class
    })
    ResponseEntity<Object> handleUnavailable(Exception ex, WebRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
        return problem(
                ErrorCode.SERVICE_UNAVAILABLE,
                "The service is temporarily unable to process the request. Retry later.",
                List.of(),
                Map.of(),
                headers,
                request,
                ex);
    }

    /** Last resort: anything unexpected is a 500 with a generic message; details go to the log only. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        return problem(
                ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred. Quote the requestId when reporting this problem.",
                List.of(),
                Map.of(),
                new HttpHeaders(),
                request,
                ex);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Spring MVC request errors (overrides of ResponseEntityExceptionHandler)
    // ---------------------------------------------------------------------------------------------------------------

    /** Bean Validation failures on a request body or a bound query-parameter object. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            violations.add(new FieldViolation(error.getField(), fieldMessage(error)));
        }
        for (ObjectError error : ex.getBindingResult().getGlobalErrors()) {
            violations.add(new FieldViolation(error.getObjectName(), error.getDefaultMessage()));
        }
        return problem(
                ErrorCode.VALIDATION_FAILED,
                "Request contains invalid fields.",
                violations,
                Map.of(),
                headers,
                request,
                ex);
    }

    /** Constraint violations on individual handler parameters, e.g. {@code @Max(100) int size}. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String parameter = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                violations.add(new FieldViolation(parameter, error.getDefaultMessage()));
            }
        });
        return problem(
                ErrorCode.VALIDATION_FAILED,
                "Request contains invalid fields.",
                violations,
                Map.of(),
                headers,
                request,
                ex);
    }

    /** Missing body, invalid JSON, unknown fields or values of the wrong type. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problem(ErrorCode.MALFORMED_REQUEST, describeUnreadable(ex), List.of(), Map.of(), headers, request, ex);
    }

    /** A path variable or simple request parameter could not be converted, e.g. a malformed UUID. */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String name = ex instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName()
                : String.valueOf(ex.getPropertyName());
        String expectation = "must be " + describeType(requiredType(ex));
        return problem(
                ErrorCode.INVALID_PARAMETER,
                "Parameter '" + name + "' " + expectation + ".",
                List.of(new FieldViolation(name, expectation)),
                Map.of(),
                headers,
                request,
                ex);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detail = "No endpoint " + ex.getHttpMethod() + " /" + ex.getResourcePath() + ".";
        return problem(ErrorCode.ROUTE_NOT_FOUND, detail, List.of(), Map.of(), headers, request, ex);
    }

    /**
     * Funnel for every remaining Spring MVC exception (405, 406, 415, missing parameters, ...). Spring's own detail
     * messages for these are client-safe, so they are kept; only the body shape and the {@code code} are ours.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ErrorCode code = switch (ex) {
            case HttpRequestMethodNotSupportedException e -> ErrorCode.METHOD_NOT_ALLOWED;
            case HttpMediaTypeNotSupportedException e -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case HttpMediaTypeNotAcceptableException e -> ErrorCode.NOT_ACCEPTABLE;
            default -> fromStatus(statusCode);
        };
        return problem(code, springDetail(ex, body, code), List.of(), Map.of(), headers, request, ex);
    }

    /** Spring passes its ProblemDetail as {@code body}, or exposes it on the exception ({@link ErrorResponse}). */
    private static String springDetail(Exception ex, Object body, ErrorCode code) {
        ProblemDetail springProblem = body instanceof ProblemDetail problemDetail
                ? problemDetail
                : ex instanceof ErrorResponse errorResponse ? errorResponse.getBody() : null;
        return springProblem != null && springProblem.getDetail() != null
                ? springProblem.getDetail()
                : code.title() + ".";
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------------------------------------------

    private ResponseEntity<Object> problem(
            ErrorCode code,
            String detail,
            List<FieldViolation> violations,
            Map<String, Object> details,
            HttpHeaders headers,
            WebRequest request,
            Exception ex) {
        if (request instanceof ServletWebRequest servletRequest
                && servletRequest.getResponse() != null
                && servletRequest.getResponse().isCommitted()) {
            log.warn("Response already committed; cannot render error code={}", code, ex);
            return null;
        }
        logFailure(code, detail, ex);

        ProblemDetail body = ProblemDetail.forStatusAndDetail(code.status(), detail);
        body.setType(code.type());
        body.setTitle(code.title());
        requestPath(request).map(URI::create).ifPresent(body::setInstance);
        body.setProperty("code", code.name());
        body.setProperty("requestId", RequestContext.currentRequestId());
        body.setProperty("timestamp", Instant.now());
        if (!violations.isEmpty()) {
            body.setProperty("errors", violations);
        }
        details.forEach(body::setProperty);
        return ResponseEntity.status(code.status()).headers(headers).body(body);
    }

    private static void logFailure(ErrorCode code, String detail, Exception ex) {
        if (code.status().is5xxServerError()) {
            log.error("Request failed: code={} status={}", code, code.status().value(), ex);
        } else {
            log.warn(
                    "Request rejected: code={} status={} detail={}",
                    code,
                    code.status().value(),
                    detail);
        }
    }

    private static ErrorCode fromStatus(HttpStatusCode status) {
        if (status.value() == 404) {
            return ErrorCode.ROUTE_NOT_FOUND;
        }
        if (status.is4xxClientError()) {
            return ErrorCode.MALFORMED_REQUEST;
        }
        return status.value() == 503 ? ErrorCode.SERVICE_UNAVAILABLE : ErrorCode.INTERNAL_ERROR;
    }

    private static Optional<String> requestPath(WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest) {
            return Optional.of(servletRequest.getRequest().getRequestURI());
        }
        return Optional.empty();
    }

    private static Optional<String> violatedConstraint(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                return Optional.of(violation.getConstraintName());
            }
        }
        return Optional.empty();
    }

    private static String fieldMessage(FieldError error) {
        if (error.isBindingFailure()) {
            TypeMismatchException mismatch = unwrapTypeMismatch(error);
            return "must be " + describeType(mismatch == null ? null : requiredType(mismatch));
        }
        return error.getDefaultMessage();
    }

    private static TypeMismatchException unwrapTypeMismatch(FieldError error) {
        try {
            return error.unwrap(TypeMismatchException.class);
        } catch (IllegalArgumentException notATypeMismatch) {
            return null;
        }
    }

    /** For collection parameters (e.g. {@code ?status=NEW&status=FOO}) report the element type, not the list. */
    private static Class<?> requiredType(TypeMismatchException ex) {
        if (ex.getCause() instanceof ConversionFailedException conversion) {
            TypeDescriptor target = conversion.getTargetType();
            if ((target.isCollection() || target.isArray()) && target.getElementTypeDescriptor() != null) {
                return target.getElementTypeDescriptor().getType();
            }
            return target.getType();
        }
        return ex.getRequiredType();
    }

    static String describeType(Class<?> type) {
        if (type == null) {
            return "a valid value";
        }
        if (type.isEnum()) {
            return "one of " + Arrays.toString(type.getEnumConstants());
        }
        if (UUID.class.equals(type)) {
            return "a valid UUID";
        }
        if (Temporal.class.isAssignableFrom(type)) {
            return "an ISO-8601 timestamp such as 2026-01-31T09:30:00Z";
        }
        if (type == Integer.class || type == int.class || type == Long.class || type == long.class) {
            return "a whole number";
        }
        if (type == Boolean.class || type == boolean.class) {
            return "true or false";
        }
        if (Collection.class.isAssignableFrom(type) || type.isArray()) {
            return "a list";
        }
        if (type == String.class) {
            return "a string";
        }
        return "a valid " + type.getSimpleName();
    }

    private static String describeUnreadable(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof UnrecognizedPropertyException unknown) {
            return "Unknown field '" + unknown.getPropertyName() + "'.";
        }
        if (cause instanceof MismatchedInputException mismatch
                && !mismatch.getPath().isEmpty()) {
            return "Field '" + jsonPath(mismatch) + "' must be " + describeType(mismatch.getTargetType()) + ".";
        }
        if (cause instanceof JacksonException) {
            return "Request body is not valid JSON.";
        }
        return "Request body is missing or unreadable.";
    }

    private static String jsonPath(JacksonException ex) {
        StringBuilder path = new StringBuilder();
        for (JacksonException.Reference reference : ex.getPath()) {
            if (reference.getPropertyName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(reference.getPropertyName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }
}
