package com.recruiting.candidates.dto.request;

import java.util.Set;

/** The profile fields shared by create and update requests, so both map to the domain profile the same way. */
public interface ProfileFields {

    /** Phone numbers: optional leading +, digits, spaces, dashes and parentheses; 7 to 20 characters. */
    String PHONE_PATTERN = "^\\+?[0-9][0-9 ()-]{5,18}[0-9]$";

    /** Stricter than the default {@code @Email}: requires a dot in the domain part. */
    String EMAIL_PATTERN = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    String firstName();

    String lastName();

    String email();

    String phone();

    String currentTitle();

    String currentCompany();

    String city();

    String country();

    Integer yearsOfExperience();

    Integer noticePeriodDays();

    Set<String> skills();
}
