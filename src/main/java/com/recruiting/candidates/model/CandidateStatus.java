package com.recruiting.candidates.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Hiring pipeline stage of a candidate.
 *
 * <p>Transitions are explicit rather than derived from ordinal order so the rules are readable and can be changed in
 * one place:
 *
 * <ul>
 *   <li>Forward moves are allowed and may skip stages (e.g. a referral can go straight from NEW to INTERVIEWING).
 *   <li>Any open stage can move to REJECTED.
 *   <li>REJECTED can be re-opened to NEW.
 *   <li>HIRED is terminal.
 * </ul>
 */
public enum CandidateStatus {
    NEW,
    SCREENING,
    INTERVIEWING,
    OFFERED,
    HIRED,
    REJECTED;

    private static final Map<CandidateStatus, Set<CandidateStatus>> ALLOWED_TRANSITIONS =
            new EnumMap<>(CandidateStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(NEW, EnumSet.of(SCREENING, INTERVIEWING, OFFERED, REJECTED));
        ALLOWED_TRANSITIONS.put(SCREENING, EnumSet.of(INTERVIEWING, OFFERED, REJECTED));
        ALLOWED_TRANSITIONS.put(INTERVIEWING, EnumSet.of(OFFERED, REJECTED));
        ALLOWED_TRANSITIONS.put(OFFERED, EnumSet.of(HIRED, REJECTED));
        ALLOWED_TRANSITIONS.put(HIRED, EnumSet.noneOf(CandidateStatus.class));
        ALLOWED_TRANSITIONS.put(REJECTED, EnumSet.of(NEW));
    }

    public boolean canTransitionTo(CandidateStatus target) {
        return ALLOWED_TRANSITIONS.get(this).contains(target);
    }

    public Set<CandidateStatus> allowedTransitions() {
        return Collections.unmodifiableSet(ALLOWED_TRANSITIONS.get(this));
    }
}
