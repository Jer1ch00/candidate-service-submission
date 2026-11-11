package com.recruiting.candidates.candidate.domain;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * The editable profile of a candidate, used for both create and update.
 *
 * <p>Always normalized: whatever the caller passes, the instance holds trimmed text, a lower-case email, an
 * upper-case country code and de-duplicated lower-case skills. Shape validation (lengths, formats) happens at the API
 * boundary; this type guarantees the invariants the domain relies on.
 */
public record CandidateProfile(
        String firstName,
        String lastName,
        String email,
        String phone,
        String currentTitle,
        String currentCompany,
        String city,
        String country,
        int yearsOfExperience,
        Integer noticePeriodDays,
        Set<String> skills) {

    public CandidateProfile {
        firstName = Objects.requireNonNull(Normalizer.text(firstName), "firstName is required");
        lastName = Objects.requireNonNull(Normalizer.text(lastName), "lastName is required");
        email = Objects.requireNonNull(Normalizer.email(email), "email is required");
        phone = Normalizer.text(phone);
        currentTitle = Normalizer.text(currentTitle);
        currentCompany = Normalizer.text(currentCompany);
        city = Normalizer.text(city);
        country = Normalizer.countryCode(country);
        skills = Collections.unmodifiableSet(Normalizer.skills(skills));
    }
}
