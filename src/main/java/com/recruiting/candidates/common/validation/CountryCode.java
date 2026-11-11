package com.recruiting.candidates.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** An ISO-3166 alpha-2 country code such as {@code IN} or {@code us} (case-insensitive). {@code null} is valid. */
@Documented
@Constraint(validatedBy = CountryCodeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface CountryCode {

    String message() default "must be an ISO-3166 alpha-2 country code, e.g. IN or US";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
