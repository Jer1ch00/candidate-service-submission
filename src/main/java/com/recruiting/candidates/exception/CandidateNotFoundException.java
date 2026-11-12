package com.recruiting.candidates.exception;

import java.util.Map;
import java.util.UUID;

public class CandidateNotFoundException extends ApplicationException {

    public CandidateNotFoundException(UUID id) {
        super(ErrorCode.CANDIDATE_NOT_FOUND, "No candidate exists with id " + id + ".", Map.of("candidateId", id));
    }
}
