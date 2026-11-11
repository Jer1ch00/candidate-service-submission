package com.recruiting.candidates.candidate.domain;

import com.recruiting.candidates.common.error.ApplicationException;
import com.recruiting.candidates.common.error.ErrorCode;
import java.util.Map;
import java.util.UUID;

public class CandidateNotFoundException extends ApplicationException {

    public CandidateNotFoundException(UUID id) {
        super(ErrorCode.CANDIDATE_NOT_FOUND, "No candidate exists with id " + id + ".", Map.of("candidateId", id));
    }
}
