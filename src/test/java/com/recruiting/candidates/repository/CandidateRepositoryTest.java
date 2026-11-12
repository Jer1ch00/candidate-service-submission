package com.recruiting.candidates.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recruiting.candidates.TestcontainersConfiguration;
import com.recruiting.candidates.entity.Candidate;
import com.recruiting.candidates.entity.CandidateStatusChange;
import com.recruiting.candidates.model.CandidateProfile;
import com.recruiting.candidates.model.CandidateSearchCriteria;
import com.recruiting.candidates.model.CandidateStatus;
import com.recruiting.candidates.model.SkillMatch;
import com.recruiting.candidates.repository.specification.CandidateSpecifications;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Search and persistence behaviour against real PostgreSQL (trigram index, generated column, constraints, cascades).
 * Each test starts from an empty table and runs in a transaction that is rolled back afterwards.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CandidateRepositoryTest {

    @Autowired
    private CandidateRepository repository;

    @Autowired
    private CandidateStatusChangeRepository statusChanges;

    @Autowired
    private TestEntityManager entityManager;

    private Candidate priya;
    private Candidate rahul;
    private Candidate anna;
    private Candidate li;

    @BeforeEach
    void setUp() {
        // The PostgreSQL container is shared with tests that commit data (the API integration test). Emptying the table
        // inside this test's transaction (rolled back afterwards) makes results independent of which tests ran first.
        repository.deleteAllInBatch();
        priya = save(
                profile(
                        "Priya",
                        "Sharma",
                        "priya.sharma@example.com",
                        "Backend Engineer",
                        "Acme",
                        "Bengaluru",
                        "IN",
                        6,
                        30,
                        "java",
                        "spring boot",
                        "postgresql"),
                "2026-01-10T09:00:00Z",
                CandidateStatus.NEW);
        rahul = save(
                profile(
                        "Rahul",
                        "Verma",
                        "rahul@globex.io",
                        "Frontend Developer",
                        "Globex",
                        "Pune",
                        "IN",
                        3,
                        60,
                        "javascript",
                        "react"),
                "2026-02-10T09:00:00Z",
                CandidateStatus.SCREENING);
        anna = save(
                profile(
                        "Anna",
                        "Müller",
                        "anna.mueller@example.de",
                        "Data Engineer",
                        "Acme",
                        "Berlin",
                        "DE",
                        9,
                        90,
                        "python",
                        "java",
                        "spark"),
                "2026-03-10T09:00:00Z",
                CandidateStatus.INTERVIEWING);
        li = save(
                profile(
                        "Li",
                        "Wei",
                        "li.wei@initech.com",
                        "QA Lead (100% automation)",
                        "Initech",
                        "Bengaluru",
                        "IN",
                        12,
                        null,
                        "selenium",
                        "java"),
                "2026-04-10T09:00:00Z",
                CandidateStatus.OFFERED);
        entityManager.clear();
    }

    @Test
    void textSearchMatchesPartialNamesEmailsTitlesAndCompanies() {
        assertThat(search(text("sharm"))).containsExactlyInAnyOrder(priya.getId());
        assertThat(search(text("GLOBEX"))).containsExactlyInAnyOrder(rahul.getId());
        assertThat(search(text("engineer"))).containsExactlyInAnyOrder(priya.getId(), anna.getId());
    }

    @Test
    void everyTextTokenMustMatch() {
        assertThat(search(text("acme", "engineer"))).containsExactlyInAnyOrder(priya.getId(), anna.getId());
        assertThat(search(text("acme", "data"))).containsExactlyInAnyOrder(anna.getId());
        assertThat(search(text("acme", "react"))).isEmpty();
    }

    @Test
    void likeWildcardsAreMatchedLiterally() {
        assertThat(search(text("%"))).containsExactlyInAnyOrder(li.getId());
        assertThat(search(text("100%"))).containsExactlyInAnyOrder(li.getId());
        assertThat(search(text("_"))).isEmpty();
    }

    @Test
    void statusFilterMatchesAnyListedStatus() {
        var criteria = criteria().statuses(Set.of(CandidateStatus.SCREENING, CandidateStatus.OFFERED));

        assertThat(search(criteria)).containsExactlyInAnyOrder(rahul.getId(), li.getId());
    }

    @Test
    void skillFilterSupportsAllAndAny() {
        assertThat(search(criteria().skills(SkillMatch.ALL, "java", "spring boot")))
                .containsExactlyInAnyOrder(priya.getId());
        assertThat(search(criteria().skills(SkillMatch.ALL, "java")))
                .containsExactlyInAnyOrder(priya.getId(), anna.getId(), li.getId());
        assertThat(search(criteria().skills(SkillMatch.ANY, "react", "spark")))
                .containsExactlyInAnyOrder(rahul.getId(), anna.getId());
    }

    @Test
    void candidateMatchingSeveralSkillsIsReturnedOnceAndCountedOnce() {
        Page<Candidate> page = repository.findAll(
                CandidateSpecifications.matching(criteria()
                        .skills(SkillMatch.ANY, "java", "python", "spark")
                        .build()),
                PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Candidate::getId).doesNotHaveDuplicates();
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void rangeAndLocationFilters() {
        assertThat(search(criteria().experience(5, 10))).containsExactlyInAnyOrder(priya.getId(), anna.getId());
        assertThat(search(criteria().city("bengaluru"))).containsExactlyInAnyOrder(priya.getId(), li.getId());
        assertThat(search(criteria().country("DE"))).containsExactlyInAnyOrder(anna.getId());
        assertThat(search(criteria().maxNoticePeriod(60))).containsExactlyInAnyOrder(priya.getId(), rahul.getId());
    }

    @Test
    void createdRangeIsHalfOpen() {
        var criteria = criteria().created(Instant.parse("2026-02-10T09:00:00Z"), Instant.parse("2026-04-10T09:00:00Z"));

        assertThat(search(criteria)).containsExactlyInAnyOrder(rahul.getId(), anna.getId());
    }

    @Test
    void criteriaCombineWithAnd() {
        var criteria = criteria().country("IN").skills(SkillMatch.ALL, "java").experience(5, null);
        assertThat(search(criteria)).containsExactlyInAnyOrder(priya.getId(), li.getId());

        criteria.statuses(Set.of(CandidateStatus.OFFERED));
        assertThat(search(criteria)).containsExactlyInAnyOrder(li.getId());
    }

    @Test
    void tieBreakerMakesPagesDeterministicWhenSortValuesAreEqual() {
        for (int i = 0; i < 7; i++) {
            save(
                    profile("Same", "Experience" + i, "same" + i + "@example.com", null, null, null, null, 4, null),
                    "2026-05-01T00:00:00Z",
                    CandidateStatus.NEW);
        }
        entityManager.clear();
        Sort sort = Sort.by(Sort.Direction.ASC, "yearsOfExperience").and(Sort.by(Sort.Direction.ASC, "id"));
        var spec = CandidateSpecifications.matching(criteria().experience(4, 4).build());

        List<UUID> seen = new ArrayList<>();
        for (int page = 0; page < 4; page++) {
            repository.findAll(spec, PageRequest.of(page, 2, sort)).forEach(c -> seen.add(c.getId()));
        }

        assertThat(seen).hasSize(7).doesNotHaveDuplicates();
    }

    @Test
    void searchTextIsGeneratedByTheDatabase() {
        Object searchText = entityManager
                .getEntityManager()
                .createNativeQuery("select search_text from candidate where id = :id")
                .setParameter("id", priya.getId())
                .getSingleResult();

        assertThat(searchText).isEqualTo("priya sharma priya.sharma@example.com backend engineer acme");
    }

    @Test
    void versionIncrementsOnUpdate() {
        Candidate candidate = repository.findById(priya.getId()).orElseThrow();
        long before = candidate.getVersion();

        candidate.updateProfile(
                profile(
                        "Priya",
                        "Sharma",
                        "priya.sharma@example.com",
                        "Staff Engineer",
                        "Acme",
                        "Pune",
                        "IN",
                        7,
                        30,
                        "java",
                        "kotlin"),
                Instant.parse("2026-06-01T00:00:00Z"));
        repository.saveAndFlush(candidate);

        assertThat(candidate.getVersion()).isEqualTo(before + 1);
    }

    @Test
    void deletingCandidateCascadesToSkillsAndHistory() {
        statusChanges.saveAndFlush(
                CandidateStatusChange.of(priya.getId(), null, CandidateStatus.NEW, null, Instant.now()));

        repository.delete(repository.findById(priya.getId()).orElseThrow());
        repository.flush();

        assertThat(countRows("candidate_skill", priya.getId())).isZero();
        assertThat(countRows("candidate_status_history", priya.getId())).isZero();
    }

    /** Guards the assumption GlobalExceptionHandler relies on to turn a concurrent duplicate into DUPLICATE_EMAIL. */
    @Test
    void duplicateEmailViolatesNamedUniqueConstraint() {
        Candidate duplicate = Candidate.register(
                profile("Other", "Person", "PRIYA.SHARMA@example.com", null, null, null, null, 1, null), Instant.now());

        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> assertThat(rootConstraintName(ex)).isEqualToIgnoringCase("uk_candidate_email"));
    }

    // ---------------------------------------------------------------------------------------------------------------

    private Candidate save(CandidateProfile profile, String createdAt, CandidateStatus status) {
        Instant created = Instant.parse(createdAt);
        Candidate candidate = Candidate.register(profile, created);
        if (status != CandidateStatus.NEW) {
            candidate.changeStatus(status, created);
        }
        return repository.saveAndFlush(candidate);
    }

    private List<UUID> search(CriteriaBuilder criteria) {
        return search(criteria.build());
    }

    private List<UUID> search(CandidateSearchCriteria criteria) {
        return repository.findAll(CandidateSpecifications.matching(criteria)).stream()
                .map(Candidate::getId)
                .toList();
    }

    private long countRows(String table, UUID candidateId) {
        Object count = entityManager
                .getEntityManager()
                .createNativeQuery("select count(*) from " + table + " where candidate_id = :id")
                .setParameter("id", candidateId)
                .getSingleResult();
        return ((Number) count).longValue();
    }

    private static String rootConstraintName(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
        }
        return null;
    }

    private static CandidateProfile profile(
            String firstName,
            String lastName,
            String email,
            String title,
            String company,
            String city,
            String country,
            int years,
            Integer noticeDays,
            String... skills) {
        return new CandidateProfile(
                firstName,
                lastName,
                email,
                null,
                title,
                company,
                city,
                country,
                years,
                noticeDays,
                new HashSet<>(List.of(skills)));
    }

    private static CriteriaBuilder criteria() {
        return new CriteriaBuilder();
    }

    private static CriteriaBuilder text(String... tokens) {
        return new CriteriaBuilder().text(tokens);
    }

    /** Small test builder so each test states only the criteria it cares about. */
    private static final class CriteriaBuilder {
        private List<String> tokens = List.of();
        private Set<CandidateStatus> statuses = Set.of();
        private Set<String> skills = Set.of();
        private SkillMatch skillMatch = SkillMatch.ALL;
        private Integer minExperience;
        private Integer maxExperience;
        private String city;
        private String country;
        private Integer maxNoticePeriod;
        private Instant createdFrom;
        private Instant createdTo;

        CriteriaBuilder text(String... values) {
            this.tokens = List.of(values);
            return this;
        }

        CriteriaBuilder statuses(Set<CandidateStatus> values) {
            this.statuses = values;
            return this;
        }

        CriteriaBuilder skills(SkillMatch match, String... values) {
            this.skillMatch = match;
            this.skills = Set.of(values);
            return this;
        }

        CriteriaBuilder experience(Integer min, Integer max) {
            this.minExperience = min;
            this.maxExperience = max;
            return this;
        }

        CriteriaBuilder city(String value) {
            this.city = value;
            return this;
        }

        CriteriaBuilder country(String value) {
            this.country = value;
            return this;
        }

        CriteriaBuilder maxNoticePeriod(Integer value) {
            this.maxNoticePeriod = value;
            return this;
        }

        CriteriaBuilder created(Instant from, Instant to) {
            this.createdFrom = from;
            this.createdTo = to;
            return this;
        }

        CandidateSearchCriteria build() {
            return new CandidateSearchCriteria(
                    tokens,
                    statuses,
                    skills,
                    skillMatch,
                    minExperience,
                    maxExperience,
                    city,
                    country,
                    maxNoticePeriod,
                    createdFrom,
                    createdTo);
        }
    }
}
