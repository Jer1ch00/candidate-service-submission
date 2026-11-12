package com.recruiting.candidates.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.LinkedHashSet;
import org.junit.jupiter.api.Test;

class CandidateProfileTest {

    @Test
    void normalizesEveryField() {
        CandidateProfile profile = new CandidateProfile(
                "  Priya ",
                " Sharma",
                "  Priya.Sharma@Example.COM ",
                "  ",
                "  Senior   Engineer ",
                "",
                " Bengaluru ",
                " in ",
                6,
                null,
                new LinkedHashSet<>(Arrays.asList(" Java ", "java", "Spring   Boot", "  ", null)));

        assertThat(profile.firstName()).isEqualTo("Priya");
        assertThat(profile.lastName()).isEqualTo("Sharma");
        assertThat(profile.email()).isEqualTo("priya.sharma@example.com");
        assertThat(profile.phone()).isNull();
        assertThat(profile.currentTitle()).isEqualTo("Senior Engineer");
        assertThat(profile.currentCompany()).isNull();
        assertThat(profile.city()).isEqualTo("Bengaluru");
        assertThat(profile.country()).isEqualTo("IN");
        assertThat(profile.skills()).containsExactly("java", "spring boot");
    }

    @Test
    void nullSkillsBecomeEmptySet() {
        CandidateProfile profile =
                new CandidateProfile("A", "B", "a@b.co", null, null, null, null, null, 0, null, null);

        assertThat(profile.skills()).isEmpty();
    }

    @Test
    void requiredFieldsCannotBeBlank() {
        assertThatThrownBy(() -> new CandidateProfile("  ", "B", "a@b.co", null, null, null, null, null, 0, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("firstName");
    }
}
