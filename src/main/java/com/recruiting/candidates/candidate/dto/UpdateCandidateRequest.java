package com.recruiting.candidates.candidate.dto;

import com.recruiting.candidates.common.validation.CountryCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * Full replacement of the profile (PUT semantics). Status is changed only through the status endpoint, so sending
 * {@code status} here is rejected as an unknown field.
 */
@Schema(description = "Complete profile plus the version the client last read")
public record UpdateCandidateRequest(
        @Schema(example = "Priya") @NotBlank @Size(max = 100)
        String firstName,

        @Schema(example = "Sharma") @NotBlank @Size(max = 100)
        String lastName,

        @Schema(example = "priya.sharma@example.com")
        @NotBlank
        @Size(max = 254)
        @Email(regexp = EMAIL_PATTERN, message = "must be a well-formed email address")
        String email,

        @Schema(example = "+91 98765 43210")
        @Size(max = 20)
        @Pattern(regexp = PHONE_PATTERN, message = "must be a valid phone number, e.g. +91 98765 43210")
        String phone,

        @Schema(example = "Staff Engineer") @Size(max = 150) String currentTitle,
        @Schema(example = "Acme Corp") @Size(max = 150) String currentCompany,
        @Schema(example = "Pune") @Size(max = 100) String city,

        @Schema(example = "IN", description = "ISO-3166 alpha-2 code") @CountryCode
        String country,

        @Schema(example = "7") @NotNull @Min(0) @Max(60) Integer yearsOfExperience,
        @Schema(example = "60") @Min(0) @Max(365) Integer noticePeriodDays,

        @Schema(example = "[\"java\", \"kotlin\"]") @Size(max = 50, message = "must contain at most 50 skills")
        Set<@NotBlank @Size(max = 50) String> skills,

        @Schema(example = "0", description = "Version from the last read; a mismatch returns 409 VERSION_CONFLICT")
        @NotNull
        @PositiveOrZero
        Long version)
        implements ProfileFields {}
