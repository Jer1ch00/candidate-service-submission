package com.recruiting.candidates.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.recruiting.candidates.exception.ErrorCode;
import com.recruiting.candidates.exception.InvalidStatusTransitionException;
import com.recruiting.candidates.model.CandidateProfile;
import com.recruiting.candidates.model.CandidateStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CandidateTest {

    private static final Instant CREATED = Instant.parse("2026-01-10T09:00:00Z");
    private static final Instant LATER = Instant.parse("2026-01-11T09:00:00Z");

    @Test
    void registerStartsInNewWithTimestamps() {
        Candidate candidate = Candidate.register(profile("java"), CREATED);

        assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.NEW);
        assertThat(candidate.getCreatedAt()).isEqualTo(CREATED);
        assertThat(candidate.getUpdatedAt()).isEqualTo(CREATED);
        assertThat(candidate.getEmail()).isEqualTo("priya.sharma@example.com");
    }

    @Test
    void updateProfileReplacesSkillsAndTouchesUpdatedAt() {
        Candidate candidate = Candidate.register(profile("java", "spring"), CREATED);

        candidate.updateProfile(profile("spring", "kotlin"), LATER);

        assertThat(candidate.getSkills()).containsExactlyInAnyOrder("spring", "kotlin");
        assertThat(candidate.getUpdatedAt()).isEqualTo(LATER);
        assertThat(candidate.getCreatedAt()).isEqualTo(CREATED);
    }

    @Test
    void changeStatusReturnsPreviousStatus() {
        Candidate candidate = Candidate.register(profile(), CREATED);

        CandidateStatus previous = candidate.changeStatus(CandidateStatus.SCREENING, LATER);

        assertThat(previous).isEqualTo(CandidateStatus.NEW);
        assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.SCREENING);
        assertThat(candidate.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    void invalidTransitionIsRejectedAndStateIsUnchanged() {
        Candidate candidate = Candidate.register(profile(), CREATED);

        assertThatThrownBy(() -> candidate.changeStatus(CandidateStatus.HIRED, LATER))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .satisfies(ex -> {
                    InvalidStatusTransitionException error = (InvalidStatusTransitionException) ex;
                    assertThat(error.errorCode()).isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
                    assertThat(error.details())
                            .containsEntry("currentStatus", CandidateStatus.NEW)
                            .containsEntry("requestedStatus", CandidateStatus.HIRED)
                            .containsKey("allowedTransitions");
                });
        assertThat(candidate.getStatus()).isEqualTo(CandidateStatus.NEW);
        assertThat(candidate.getUpdatedAt()).isEqualTo(CREATED);
    }

    @Test
    void skillsViewIsReadOnly() {
        Candidate candidate = Candidate.register(profile("java"), CREATED);

        assertThatThrownBy(() -> candidate.getSkills().add("hacked")).isInstanceOf(UnsupportedOperationException.class);
    }

    private static CandidateProfile profile(String... skills) {
        return new CandidateProfile(
                "Priya",
                "Sharma",
                "Priya.Sharma@Example.com",
                null,
                "Engineer",
                "Acme",
                "Bengaluru",
                "in",
                6,
                30,
                Set.copyOf(List.of(skills)));
    }
}
