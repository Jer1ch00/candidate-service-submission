package com.recruiting.candidates.util;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Canonical forms for values that are compared or de-duplicated. Applied on write and on search so that "Java ",
 * "java" and "JAVA" are the same skill and "A@B.com" collides with "a@b.com".
 */
public final class Normalizer {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private Normalizer() {}

    /** Trims and collapses inner whitespace; blank becomes {@code null}. */
    public static String text(String value) {
        if (value == null) {
            return null;
        }
        String collapsed = WHITESPACE.matcher(value.strip()).replaceAll(" ");
        return collapsed.isEmpty() ? null : collapsed;
    }

    public static String email(String value) {
        String text = text(value);
        return text == null ? null : text.toLowerCase(Locale.ROOT);
    }

    /** ISO-3166 alpha-2 codes are stored upper-case. */
    public static String countryCode(String value) {
        String text = text(value);
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

    public static String skill(String value) {
        String text = text(value);
        return text == null ? null : text.toLowerCase(Locale.ROOT);
    }

    /** Normalizes, drops blanks and de-duplicates while keeping the caller's order. */
    public static Set<String> skills(Collection<String> values) {
        Set<String> result = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                String skill = skill(value);
                if (skill != null) {
                    result.add(skill);
                }
            }
        }
        return result;
    }
}
