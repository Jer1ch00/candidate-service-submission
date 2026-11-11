package com.recruiting.candidates.candidate.dto;

import com.recruiting.candidates.candidate.domain.CandidateStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code version} must be sent back on update and status change (optimistic locking). Skills are sorted. */
public record CandidateResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String currentTitle,
        String currentCompany,
        String city,
        String country,
        int yearsOfExperience,
        Integer noticePeriodDays,
        List<String> skills,
        CandidateStatus status,
        Instant createdAt,
        Instant updatedAt,
        long version) {}
