package com.recruiting.candidates.service;

import com.recruiting.candidates.dto.request.CandidateSearchRequest;
import com.recruiting.candidates.dto.request.ChangeStatusRequest;
import com.recruiting.candidates.dto.request.CreateCandidateRequest;
import com.recruiting.candidates.dto.request.UpdateCandidateRequest;
import com.recruiting.candidates.dto.response.CandidateResponse;
import com.recruiting.candidates.dto.response.PageResponse;
import com.recruiting.candidates.dto.response.StatusChangeResponse;
import com.recruiting.candidates.exception.CandidateNotFoundException;
import com.recruiting.candidates.exception.DuplicateEmailException;
import com.recruiting.candidates.exception.InvalidStatusTransitionException;
import com.recruiting.candidates.exception.RequestValidationException;
import com.recruiting.candidates.exception.VersionConflictException;
import java.util.UUID;

/**
 * Use cases for managing candidates. The controller depends only on this contract, never on an implementation.
 *
 * <p>Works in API types (request and response DTOs), so callers never see entities. Every failure is a typed exception
 * that the global exception handler turns into an error response; no method returns {@code null} or an error flag.
 */
public interface CandidateService {

    /**
     * Registers a new candidate in status {@code NEW} and records the first status-history entry.
     *
     * @throws DuplicateEmailException if another candidate already uses the email
     */
    CandidateResponse create(CreateCandidateRequest request);

    /** @throws CandidateNotFoundException if no candidate has this id */
    CandidateResponse get(UUID id);

    /**
     * Replaces the profile (everything except status). The request must carry the version the client last read.
     *
     * @throws CandidateNotFoundException if no candidate has this id
     * @throws VersionConflictException if the candidate changed since the client read it
     * @throws DuplicateEmailException if the new email belongs to another candidate
     */
    CandidateResponse update(UUID id, UpdateCandidateRequest request);

    /**
     * Moves the candidate through the hiring pipeline and records the move in the status history.
     *
     * @throws CandidateNotFoundException if no candidate has this id
     * @throws VersionConflictException if the candidate changed since the client read it
     * @throws InvalidStatusTransitionException if the pipeline does not allow the move
     */
    CandidateResponse changeStatus(UUID id, ChangeStatusRequest request);

    /**
     * Status history of one candidate, newest first.
     *
     * @throws CandidateNotFoundException if no candidate has this id
     */
    PageResponse<StatusChangeResponse> statusHistory(UUID id, int page, int size);

    /**
     * Deletes the candidate permanently, including skills and status history.
     *
     * @throws CandidateNotFoundException if no candidate has this id
     */
    void delete(UUID id);

    /**
     * Searches and filters candidates; always paginated.
     *
     * @throws RequestValidationException if the criteria contradict each other (for example min above max) or the
     *     sort is not supported
     */
    PageResponse<CandidateResponse> search(CandidateSearchRequest request);
}
