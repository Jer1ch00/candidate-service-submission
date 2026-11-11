package com.recruiting.candidates.candidate.domain;

import com.recruiting.candidates.common.error.ApplicationException;
import com.recruiting.candidates.common.error.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Optimistic-locking failure: the client edited an outdated copy. Raised when the version sent by the client does not
 * match the stored one, and also (without a known current version) when two writes race at commit time.
 */
public class VersionConflictException extends ApplicationException {

    private static final String MESSAGE =
            "The candidate was modified by another request. Fetch the latest version and retry.";

    public VersionConflictException(long expectedVersion, long currentVersion) {
        super(ErrorCode.VERSION_CONFLICT, MESSAGE, versions(expectedVersion, currentVersion));
    }

    private static Map<String, Object> versions(long expectedVersion, long currentVersion) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("expectedVersion", expectedVersion);
        details.put("currentVersion", currentVersion);
        return details;
    }

    public static String defaultMessage() {
        return MESSAGE;
    }
}
