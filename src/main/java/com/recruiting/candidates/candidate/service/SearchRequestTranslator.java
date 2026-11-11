package com.recruiting.candidates.candidate.service;

import com.recruiting.candidates.candidate.domain.CandidateSearchCriteria;
import com.recruiting.candidates.candidate.domain.CandidateStatus;
import com.recruiting.candidates.candidate.domain.Normalizer;
import com.recruiting.candidates.candidate.dto.CandidateSearchRequest;
import com.recruiting.candidates.common.error.FieldViolation;
import com.recruiting.candidates.common.error.RequestValidationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Turns raw query parameters into a {@link CandidateSearchCriteria} and a {@link Pageable}, enforcing the rules that
 * span several fields. All problems are collected and reported together as one {@code VALIDATION_FAILED} error.
 *
 * <p>Pagination is never silently adjusted: an out-of-range page size is rejected (by Bean Validation on the request)
 * rather than clamped, so a client always knows whether it received what it asked for.
 */
final class SearchRequestTranslator {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_SEARCH_TERMS = 5;

    /** API sort names mapped to entity attributes. Only these are sortable (each is cheap to sort on). */
    private static final Map<String, String> SORTABLE_FIELDS = sortableFields();

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private SearchRequestTranslator() {}

    static CandidateSearchCriteria toCriteria(CandidateSearchRequest request) {
        List<FieldViolation> violations = new ArrayList<>();

        List<String> tokens = tokenize(request.q());
        if (tokens.size() > MAX_SEARCH_TERMS) {
            violations.add(new FieldViolation("q", "must contain at most " + MAX_SEARCH_TERMS + " search terms"));
        }
        if (request.minExperience() != null
                && request.maxExperience() != null
                && request.minExperience() > request.maxExperience()) {
            violations.add(new FieldViolation("minExperience", "must be less than or equal to maxExperience"));
        }
        if (request.createdFrom() != null
                && request.createdTo() != null
                && !request.createdFrom().isBefore(request.createdTo())) {
            violations.add(new FieldViolation("createdFrom", "must be before createdTo"));
        }
        if (!violations.isEmpty()) {
            throw new RequestValidationException(violations);
        }

        Set<CandidateStatus> statuses =
                request.status() == null || request.status().isEmpty() ? Set.of() : EnumSet.copyOf(request.status());
        return new CandidateSearchCriteria(
                tokens,
                statuses,
                Normalizer.skills(request.skills()),
                request.skillsMatch(),
                request.minExperience(),
                request.maxExperience(),
                Normalizer.text(request.city()),
                Normalizer.countryCode(request.country()),
                request.maxNoticePeriodDays(),
                request.createdFrom(),
                request.createdTo());
    }

    static Pageable toPageable(CandidateSearchRequest request) {
        int page = request.page() == null ? 0 : request.page();
        int size = request.size() == null ? DEFAULT_PAGE_SIZE : request.size();
        return PageRequest.of(page, size, withTieBreaker(parseSort(request.sort())));
    }

    /**
     * Appends {@code id} in the same direction. Without a unique last sort key, rows with equal values (e.g. same
     * experience) can swap places between page requests, producing duplicates and gaps across pages.
     */
    static Sort withTieBreaker(Sort sort) {
        Sort.Direction direction =
                sort.stream().findFirst().map(Sort.Order::getDirection).orElse(Sort.Direction.DESC);
        return sort.and(Sort.by(direction, "id"));
    }

    private static Sort parseSort(String sort) {
        String value = Normalizer.text(sort);
        if (value == null) {
            return DEFAULT_SORT;
        }
        String[] parts = value.split(",", -1);
        String field = SORTABLE_FIELDS.get(parts[0].strip());
        Sort.Direction direction = parts.length == 2 ? parseDirection(parts[1]) : null;
        boolean malformed = parts.length > 2 || (parts.length == 2 && direction == null);
        if (field == null || malformed) {
            throw new RequestValidationException(
                    "sort", "must be one of " + SORTABLE_FIELDS.keySet() + " optionally followed by ,asc or ,desc");
        }
        return Sort.by(direction == null ? Sort.Direction.ASC : direction, field);
    }

    private static Sort.Direction parseDirection(String value) {
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> null;
        };
    }

    private static List<String> tokenize(String q) {
        String text = Normalizer.text(q);
        if (text == null) {
            return List.of();
        }
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split(" "))
                .distinct()
                .toList();
    }

    private static Map<String, String> sortableFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("createdAt", "createdAt");
        fields.put("updatedAt", "updatedAt");
        fields.put("firstName", "firstName");
        fields.put("lastName", "lastName");
        fields.put("yearsOfExperience", "yearsOfExperience");
        return fields;
    }
}
