package com.recruiting.candidates.candidate.dto;

import com.recruiting.candidates.candidate.domain.CandidateStatus;
import java.time.Instant;
import java.util.UUID;

/** {@code fromStatus} is {@code null} for the entry recorded when the candidate was created. */
public record StatusChangeResponse(
        UUID id, CandidateStatus fromStatus, CandidateStatus toStatus, String note, Instant changedAt) {}
