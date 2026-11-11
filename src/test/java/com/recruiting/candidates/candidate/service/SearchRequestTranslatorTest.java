package com.recruiting.candidates.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recruiting.candidates.candidate.domain.CandidateSearchCriteria;
import com.recruiting.candidates.candidate.domain.CandidateStatus;
import com.recruiting.candidates.candidate.domain.SkillMatch;
import com.recruiting.candidates.candidate.dto.CandidateSearchRequest;
import com.recruiting.candidates.common.error.FieldViolation;
import com.recruiting.candidates.common.error.RequestValidationException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class SearchRequestTranslatorTest {

    @Test
    void emptyRequestMeansNoCriteriaAndDefaultPaging() {
        CandidateSearchRequest request = request(null, null, null, null, null, null, null, null);

        CandidateSearchCriteria criteria = SearchRequestTranslator.toCriteria(request);
        Pageable pageable = SearchRequestTranslator.toPageable(request);

        assertThat(criteria).isEqualTo(CandidateSearchCriteria.none());
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort()).containsExactly(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    }

    @Test
    void normalizesTextTokensSkillsAndLocation() {
        CandidateSearchRequest request = new CandidateSearchRequest(
                "  Priya   JAVA priya ",
                List.of(CandidateStatus.NEW, CandidateStatus.SCREENING),
                List.of(" Java ", "SPRING  boot", "java"),
                SkillMatch.ANY,
                2,
                9,
                "  Bengaluru ",
                "in",
                30,
                null,
                null,
                null,
                null,
                null);

        CandidateSearchCriteria criteria = SearchRequestTranslator.toCriteria(request);

        assertThat(criteria.textTokens()).containsExactly("priya", "java");
        assertThat(criteria.statuses()).containsExactlyInAnyOrder(CandidateStatus.NEW, CandidateStatus.SCREENING);
        assertThat(criteria.skills()).containsExactlyInAnyOrder("java", "spring boot");
        assertThat(criteria.skillMatch()).isEqualTo(SkillMatch.ANY);
        assertThat(criteria.city()).isEqualTo("Bengaluru");
        assertThat(criteria.country()).isEqualTo("IN");
    }

    @Test
    void reportsAllCrossFieldProblemsAtOnce() {
        CandidateSearchRequest request = request(
                "a b c d e f",
                10,
                5,
                Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                null,
                null);

        assertThatThrownBy(() -> SearchRequestTranslator.toCriteria(request))
                .isInstanceOf(RequestValidationException.class)
                .satisfies(ex -> assertThat(((RequestValidationException) ex).violations())
                        .extracting(FieldViolation::field)
                        .containsExactly("q", "minExperience", "createdFrom"));
    }

    @Test
    void sortIsParsedAndAlwaysEndsWithIdTieBreaker() {
        Pageable pageable = SearchRequestTranslator.toPageable(
                request(null, null, null, null, null, 2, 50, "yearsOfExperience,DESC"));

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(50);
        assertThat(pageable.getSort()).containsExactly(Sort.Order.desc("yearsOfExperience"), Sort.Order.desc("id"));
    }

    @Test
    void sortWithoutDirectionIsAscending() {
        Pageable pageable =
                SearchRequestTranslator.toPageable(request(null, null, null, null, null, null, null, "lastName"));

        assertThat(pageable.getSort()).containsExactly(Sort.Order.asc("lastName"), Sort.Order.asc("id"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"email", "lastName,sideways", "lastName,asc,extra", "searchText", ","})
    void unknownOrMalformedSortIsRejected(String sort) {
        CandidateSearchRequest request = request(null, null, null, null, null, null, null, sort);

        assertThatThrownBy(() -> SearchRequestTranslator.toPageable(request))
                .isInstanceOf(RequestValidationException.class)
                .satisfies(ex -> assertThat(((RequestValidationException) ex).violations())
                        .extracting(FieldViolation::field)
                        .containsExactly("sort"));
    }

    private static CandidateSearchRequest request(
            String q,
            Integer minExperience,
            Integer maxExperience,
            Instant createdFrom,
            Instant createdTo,
            Integer page,
            Integer size,
            String sort) {
        return new CandidateSearchRequest(
                q,
                null,
                null,
                null,
                minExperience,
                maxExperience,
                null,
                null,
                null,
                createdFrom,
                createdTo,
                page,
                size,
                sort);
    }
}
