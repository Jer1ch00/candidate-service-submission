package com.recruiting.candidates.mapper;

import com.recruiting.candidates.dto.request.ProfileFields;
import com.recruiting.candidates.dto.response.CandidateResponse;
import com.recruiting.candidates.dto.response.StatusChangeResponse;
import com.recruiting.candidates.entity.Candidate;
import com.recruiting.candidates.entity.CandidateStatusChange;
import com.recruiting.candidates.model.CandidateProfile;
import java.util.List;

/**
 * Hand-written mapping between API types and the domain. Explicit code over a mapping library: three types do not
 * justify a code generator, and every field is visible when debugging.
 *
 * <p>Static, stateless functions rather than an injected bean: there are no dependencies to inject and no alternative
 * implementation to swap in. It becomes a Spring bean the day it needs a collaborator.
 */
public final class CandidateMapper {

    private CandidateMapper() {}

    public static CandidateProfile toProfile(ProfileFields fields) {
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
    public static CandidateResponse toResponse(Candidate candidate) {
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

    public static StatusChangeResponse toResponse(CandidateStatusChange change) {
        return new StatusChangeResponse(
                change.getId(), change.getFromStatus(), change.getToStatus(), change.getNote(), change.getChangedAt());
    }
}
