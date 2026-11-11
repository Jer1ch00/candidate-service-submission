package com.recruiting.candidates.candidate.service;

import com.recruiting.candidates.candidate.domain.Candidate;
import com.recruiting.candidates.candidate.domain.CandidateProfile;
import com.recruiting.candidates.candidate.domain.CandidateStatusChange;
import com.recruiting.candidates.candidate.dto.CandidateResponse;
import com.recruiting.candidates.candidate.dto.ProfileFields;
import com.recruiting.candidates.candidate.dto.StatusChangeResponse;
import java.util.List;

/**
 * Hand-written mapping between API types and the domain. Explicit code over a mapping library: three types do not
 * justify a code generator, and every field is visible when debugging.
 */
final class CandidateMapper {

    private CandidateMapper() {}

    static CandidateProfile toProfile(ProfileFields fields) {
        return new CandidateProfile(
                fields.firstName(),
                fields.lastName(),
                fields.email(),
                fields.phone(),
                fields.currentTitle(),
                fields.currentCompany(),
                fields.city(),
                fields.country(),
                fields.yearsOfExperience(),
                fields.noticePeriodDays(),
                fields.skills());
    }

    /** Must be called inside the transaction: it reads the lazily loaded skills. */
    static CandidateResponse toResponse(Candidate candidate) {
        List<String> skills = candidate.getSkills().stream().sorted().toList();
        return new CandidateResponse(
                candidate.getId(),
                candidate.getFirstName(),
                candidate.getLastName(),
                candidate.getEmail(),
                candidate.getPhone(),
                candidate.getCurrentTitle(),
                candidate.getCurrentCompany(),
                candidate.getCity(),
                candidate.getCountry(),
                candidate.getYearsOfExperience(),
                candidate.getNoticePeriodDays(),
                skills,
                candidate.getStatus(),
                candidate.getCreatedAt(),
                candidate.getUpdatedAt(),
                candidate.getVersion());
    }

    static StatusChangeResponse toResponse(CandidateStatusChange change) {
        return new StatusChangeResponse(
                change.getId(), change.getFromStatus(), change.getToStatus(), change.getNote(), change.getChangedAt());
    }
}
