package com.recruiting.candidates.candidate.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Exhaustive check of the pipeline rules: all 36 (from, to) pairs. The expected table is written out independently of
 * the implementation so a change to the rules has to be made deliberately in both places.
 */
class CandidateStatusTest {

    private static final Set<String> ALLOWED = Set.of(
            "NEW->SCREENING",
            "NEW->INTERVIEWING",
            "NEW->OFFERED",
            "NEW->REJECTED",
            "SCREENING->INTERVIEWING",
            "SCREENING->OFFERED",
            "SCREENING->REJECTED",
            "INTERVIEWING->OFFERED",
            "INTERVIEWING->REJECTED",
            "OFFERED->HIRED",
            "OFFERED->REJECTED",
            "REJECTED->NEW");

    static Stream<Arguments> allTransitions() {
        return Arrays.stream(CandidateStatus.values())
                .flatMap(from -> Arrays.stream(CandidateStatus.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allTransitions")
    void transitionIsAllowedOnlyIfListed(CandidateStatus from, CandidateStatus to) {
        assertThat(from.canTransitionTo(to)).isEqualTo(ALLOWED.contains(from + "->" + to));
    }

    @Test
    void hiredIsTerminal() {
        assertThat(CandidateStatus.HIRED.allowedTransitions()).isEmpty();
    }

    @Test
    void noStatusCanTransitionToItself() {
        for (CandidateStatus status : CandidateStatus.values()) {
            assertThat(status.canTransitionTo(status)).as(status.name()).isFalse();
        }
    }
}
