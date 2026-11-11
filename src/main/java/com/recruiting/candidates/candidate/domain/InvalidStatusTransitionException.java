package com.recruiting.candidates.candidate.domain;

import com.recruiting.candidates.common.error.ApplicationException;
import com.recruiting.candidates.common.error.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;

/** Thrown by the domain model itself, so no caller can bypass the pipeline rules. */
public class InvalidStatusTransitionException extends ApplicationException {

    public InvalidStatusTransitionException(CandidateStatus from, CandidateStatus to) {
        super(
                ErrorCode.INVALID_STATUS_TRANSITION,
                "A candidate cannot move from " + from + " to " + to + ".",
                details(from, to));
    }

    private static Map<String, Object> details(CandidateStatus from, CandidateStatus to) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("currentStatus", from);
        details.put("requestedStatus", to);
        details.put("allowedTransitions", from.allowedTransitions());
        return details;
    }
}
