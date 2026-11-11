package com.recruiting.candidates.common.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Validated at startup: the application refuses to start without at least one strong API key. This is the one
 * deliberate fail-fast in the service; running unprotected would expose personal data.
 *
 * @param apiKeys accepted keys (env {@code API_KEYS}, comma-separated); more than one allows zero-downtime rotation
 */
@Validated
@ConfigurationProperties(prefix = "app.security")
public record ApiSecurityProperties(
        @NotEmpty(message = "at least one API key must be configured (environment variable API_KEYS)")
        List<@NotBlank @Size(min = 16, message = "every API key must be at least 16 characters long") String>
                apiKeys) {}
