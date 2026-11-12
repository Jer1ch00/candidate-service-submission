package com.recruiting.candidates.model;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * A validated, normalized search. Every criterion is optional; the ones present are combined with AND.
 *
 * @param textTokens lower-cased terms; each must appear in name, email, title or company
 * @param statuses candidate is in any of these statuses
 * @param skills normalized skill names, combined according to {@code skillMatch}
 * @param createdFrom inclusive lower bound
 * @param createdTo exclusive upper bound (half-open range, so consecutive windows never overlap)
 */
public record CandidateSearchCriteria(
        List<String> textTokens,
        Set<CandidateStatus> statuses,
        Set<String> skills,
        SkillMatch skillMatch,
        Integer minExperience,
        Integer maxExperience,
        String city,
        String country,
        Integer maxNoticePeriodDays,
        Instant createdFrom,
        Instant createdTo) {

    public CandidateSearchCriteria {
        textTokens = List.copyOf(textTokens);
        statuses = Set.copyOf(statuses);
        skills = Set.copyOf(skills);
        skillMatch = skillMatch == null ? SkillMatch.ALL : skillMatch;
    }

    public static CandidateSearchCriteria none() {
        return new CandidateSearchCriteria(
                List.of(), Set.of(), Set.of(), SkillMatch.ALL, null, null, null, null, null, null, null);
    }
}
