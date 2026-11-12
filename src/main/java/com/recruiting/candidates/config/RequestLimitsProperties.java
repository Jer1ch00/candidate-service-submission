package com.recruiting.candidates.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/** @param maxRequestBodySize largest accepted request body; larger requests get 413 before the body is read */
@Validated
@ConfigurationProperties(prefix = "app.web")
public record RequestLimitsProperties(@NotNull DataSize maxRequestBodySize) {}
