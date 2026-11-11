package com.recruiting.candidates.candidate.dto;

import com.recruiting.candidates.candidate.domain.CandidateStatus;
import com.recruiting.candidates.candidate.domain.SkillMatch;
import com.recruiting.candidates.common.validation.CountryCode;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * Query parameters of {@code GET /api/v1/candidates}. All optional; present criteria are combined with AND.
 * Single-field rules are declared here; cross-field rules (ranges, sort syntax) are checked by the service.
 */
public record CandidateSearchRequest(
        @Parameter(
                description = "Free text, matched against name, email, title and company. Up to 5 terms; "
                        + "every term must match.",
                example = "priya java")
        @Size(max = 100)
        String q,

        @Parameter(description = "Pipeline status; repeat to match any of several", example = "SCREENING")
        List<CandidateStatus> status,

        @Parameter(description = "Skill; repeat for several (see skillsMatch)", example = "java")
        @Size(max = 20, message = "must contain at most 20 skills")
        List<@Size(max = 50) String> skills,

        @Parameter(description = "ALL (default): candidate has every skill. ANY: at least one.")
        SkillMatch skillsMatch,

        @Parameter(description = "Minimum years of experience (inclusive)", example = "3") @Min(0) @Max(60)
        Integer minExperience,

        @Parameter(description = "Maximum years of experience (inclusive)", example = "8") @Min(0) @Max(60)
        Integer maxExperience,

        @Parameter(description = "City, case-insensitive exact match", example = "Bengaluru") @Size(max = 100)
        String city,

        @Parameter(description = "ISO-3166 alpha-2 country code", example = "IN") @CountryCode
        String country,

        @Parameter(description = "Maximum notice period in days (inclusive)", example = "30") @Min(0) @Max(365)
        Integer maxNoticePeriodDays,

        @Parameter(description = "Created at or after this instant (inclusive)", example = "2026-01-01T00:00:00Z")
        Instant createdFrom,

        @Parameter(description = "Created before this instant (exclusive)", example = "2026-12-31T00:00:00Z")
        Instant createdTo,

        @Parameter(description = "Zero-based page index (default 0)", example = "0") @Min(0)
        Integer page,

        @Parameter(description = "Page size, 1-100 (default 20)", example = "20") @Min(1) @Max(100)
        Integer size,

        @Parameter(
                description = "Sort field and optional direction. Fields: createdAt, updatedAt, firstName, "
                        + "lastName, yearsOfExperience. Default: createdAt,desc",
                example = "yearsOfExperience,desc")
        String sort) {}
