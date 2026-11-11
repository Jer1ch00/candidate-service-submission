package com.recruiting.candidates.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Locale;
import java.util.Set;

public class CountryCodeValidator implements ConstraintValidator<CountryCode, String> {

    private static final Set<String> ISO_COUNTRIES = Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA2);

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || ISO_COUNTRIES.contains(value.strip().toUpperCase(Locale.ROOT));
    }
}
