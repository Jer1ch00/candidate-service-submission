package com.recruiting.candidates.repository.specification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CandidateSpecificationsTest {

    @ParameterizedTest(name = "[{0}] -> [{1}]")
    @CsvSource(
            delimiter = '|',
            value = {"java|java", "50%|50\\%", "c_sharp|c\\_sharp", "back\\slash|back\\\\slash", "%_\\|\\%\\_\\\\"})
    void likeWildcardsInUserInputAreEscaped(String input, String expected) {
        assertThat(CandidateSpecifications.escapeLike(input)).isEqualTo(expected);
    }
}
