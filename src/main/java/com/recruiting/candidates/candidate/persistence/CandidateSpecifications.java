package com.recruiting.candidates.candidate.persistence;

import com.recruiting.candidates.candidate.domain.Candidate;
import com.recruiting.candidates.candidate.domain.CandidateSearchCriteria;
import com.recruiting.candidates.candidate.domain.CandidateStatus;
import com.recruiting.candidates.candidate.domain.SkillMatch;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;

/**
 * Translates {@link CandidateSearchCriteria} into one parameterized SQL query (no string concatenation, so no SQL
 * injection). Each criterion is a small composable {@link Specification}; absent criteria add no predicate.
 *
 * <p>Skill filters use correlated {@code EXISTS} subqueries rather than joins, so a candidate with many skills is
 * never duplicated in the result and the count query stays correct without {@code DISTINCT}.
 */
public final class CandidateSpecifications {

    static final String SEARCH_TEXT = "searchText";
    static final String STATUS = "status";
    static final String SKILLS = "skills";
    static final String YEARS_OF_EXPERIENCE = "yearsOfExperience";
    static final String CITY = "city";
    static final String COUNTRY = "country";
    static final String NOTICE_PERIOD_DAYS = "noticePeriodDays";
    static final String CREATED_AT = "createdAt";

    private static final char LIKE_ESCAPE = '\\';

    private CandidateSpecifications() {}

    public static Specification<Candidate> matching(CandidateSearchCriteria criteria) {
        List<Specification<Candidate>> specs = new ArrayList<>();
        criteria.textTokens().forEach(token -> specs.add(searchTextContains(token)));
        if (!criteria.statuses().isEmpty()) {
            specs.add(statusIn(criteria.statuses()));
        }
        if (!criteria.skills().isEmpty()) {
            specs.add(
                    criteria.skillMatch() == SkillMatch.ANY
                            ? hasAnySkill(criteria.skills())
                            : hasAllSkills(criteria.skills()));
        }
        if (criteria.minExperience() != null) {
            specs.add(experienceAtLeast(criteria.minExperience()));
        }
        if (criteria.maxExperience() != null) {
            specs.add(experienceAtMost(criteria.maxExperience()));
        }
        if (criteria.city() != null) {
            specs.add(cityEquals(criteria.city()));
        }
        if (criteria.country() != null) {
            specs.add(countryEquals(criteria.country()));
        }
        if (criteria.maxNoticePeriodDays() != null) {
            specs.add(noticePeriodAtMost(criteria.maxNoticePeriodDays()));
        }
        if (criteria.createdFrom() != null) {
            specs.add(createdAtOrAfter(criteria.createdFrom()));
        }
        if (criteria.createdTo() != null) {
            specs.add(createdBefore(criteria.createdTo()));
        }
        return specs.isEmpty() ? Specification.unrestricted() : Specification.allOf(specs);
    }

    /** {@code search_text LIKE '%token%'}, served by the GIN trigram index. Wildcards in user input are escaped. */
    static Specification<Candidate> searchTextContains(String token) {
        String pattern = "%" + escapeLike(token.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.like(root.get(SEARCH_TEXT), pattern, LIKE_ESCAPE);
    }

    static Specification<Candidate> statusIn(Set<CandidateStatus> statuses) {
        return (root, query, cb) -> root.get(STATUS).in(statuses);
    }

    /** One EXISTS per skill: the candidate must have every one of them. */
    static Specification<Candidate> hasAllSkills(Set<String> skills) {
        return Specification.allOf(
                skills.stream().map(skill -> hasAnySkill(Set.of(skill))).toList());
    }

    /** {@code EXISTS (SELECT 1 FROM candidate_skill s WHERE s.candidate_id = c.id AND s.skill IN (...))}. */
    static Specification<Candidate> hasAnySkill(Collection<String> skills) {
        return (root, query, cb) -> {
            Subquery<Integer> subquery = query.subquery(Integer.class);
            Root<Candidate> candidate = subquery.correlate(root);
            Join<Candidate, String> skill = candidate.join(SKILLS);
            subquery.select(cb.literal(1)).where(skill.in(skills));
            return cb.exists(subquery);
        };
    }

    static Specification<Candidate> experienceAtLeast(int years) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get(YEARS_OF_EXPERIENCE), years);
    }

    static Specification<Candidate> experienceAtMost(int years) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get(YEARS_OF_EXPERIENCE), years);
    }

    static Specification<Candidate> cityEquals(String city) {
        String normalized = city.toLowerCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(cb.lower(root.get(CITY)), normalized);
    }

    static Specification<Candidate> countryEquals(String country) {
        return (root, query, cb) -> cb.equal(root.get(COUNTRY), country);
    }

    static Specification<Candidate> noticePeriodAtMost(int days) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get(NOTICE_PERIOD_DAYS), days);
    }

    static Specification<Candidate> createdAtOrAfter(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get(CREATED_AT), from);
    }

    static Specification<Candidate> createdBefore(Instant to) {
        return (root, query, cb) -> cb.lessThan(root.get(CREATED_AT), to);
    }

    /** Makes {@code %}, {@code _} and the escape character itself match literally. */
    static String escapeLike(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            if (c == LIKE_ESCAPE || c == '%' || c == '_') {
                escaped.append(LIKE_ESCAPE);
            }
            escaped.append(c);
        }
        return escaped.toString();
    }
}
