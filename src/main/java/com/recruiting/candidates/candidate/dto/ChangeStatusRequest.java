package com.recruiting.candidates.candidate.dto;

import com.recruiting.candidates.candidate.domain.CandidateStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Schema(description = "Move a candidate to another pipeline stage")
public record ChangeStatusRequest(
        @Schema(example = "SCREENING") @NotNull CandidateStatus status,

        @Schema(example = "0", description = "Version from the last read; a mismatch returns 409 VERSION_CONFLICT")
        @NotNull
        @PositiveOrZero
        Long version,

        @Schema(example = "Strong referral from the platform team") @Size(max = 500)
        String note) {}
