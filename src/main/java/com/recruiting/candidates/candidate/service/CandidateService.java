package com.recruiting.candidates.candidate.service;

import com.recruiting.candidates.candidate.domain.Candidate;
import com.recruiting.candidates.candidate.domain.CandidateNotFoundException;
import com.recruiting.candidates.candidate.domain.CandidateProfile;
import com.recruiting.candidates.candidate.domain.CandidateSearchCriteria;
import com.recruiting.candidates.candidate.domain.CandidateStatus;
import com.recruiting.candidates.candidate.domain.CandidateStatusChange;
import com.recruiting.candidates.candidate.domain.DuplicateEmailException;
import com.recruiting.candidates.candidate.domain.VersionConflictException;
import com.recruiting.candidates.candidate.dto.CandidateResponse;
import com.recruiting.candidates.candidate.dto.CandidateSearchRequest;
import com.recruiting.candidates.candidate.dto.ChangeStatusRequest;
import com.recruiting.candidates.candidate.dto.CreateCandidateRequest;
import com.recruiting.candidates.candidate.dto.StatusChangeResponse;
import com.recruiting.candidates.candidate.dto.UpdateCandidateRequest;
import com.recruiting.candidates.candidate.persistence.CandidateRepository;
import com.recruiting.candidates.candidate.persistence.CandidateSpecifications;
import com.recruiting.candidates.candidate.persistence.CandidateStatusChangeRepository;
import com.recruiting.candidates.common.web.PageResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for candidates. Owns transactions and business rules; never returns entities (responses are mapped inside
 * the transaction, so nothing lazy escapes it).
 *
 * <p>Failures are thrown as typed exceptions ({@link CandidateNotFoundException}, {@link DuplicateEmailException},
 * {@link VersionConflictException}, ...) and rendered centrally. Checks here give precise errors; the database
 * constraints and {@code @Version} still guard against concurrent requests that slip between check and write.
 *
 * <p>Logs business events with ids only: never names, emails or other personal data.
 */
@Service
@Transactional(readOnly = true)
public class CandidateService {

    private static final Logger log = LoggerFactory.getLogger(CandidateService.class);

    private static final Sort HISTORY_ORDER = Sort.by(Sort.Direction.DESC, "changedAt", "id");

    private final CandidateRepository candidates;
    private final CandidateStatusChangeRepository statusChanges;
    private final Clock clock;

    public CandidateService(
            CandidateRepository candidates, CandidateStatusChangeRepository statusChanges, Clock clock) {
        this.candidates = candidates;
        this.statusChanges = statusChanges;
        this.clock = clock;
    }

    @Transactional
    public CandidateResponse create(CreateCandidateRequest request) {
        CandidateProfile profile = CandidateMapper.toProfile(request);
        if (candidates.existsByEmail(profile.email())) {
            throw new DuplicateEmailException();
        }
        Instant now = now();
        // Flush immediately so a concurrent duplicate surfaces here and the response carries the stored version.
        Candidate candidate = candidates.saveAndFlush(Candidate.register(profile, now));
        statusChanges.save(CandidateStatusChange.of(candidate.getId(), null, candidate.getStatus(), null, now));
        log.info("candidate.created id={}", candidate.getId());
        return CandidateMapper.toResponse(candidate);
    }

    public CandidateResponse get(UUID id) {
        return CandidateMapper.toResponse(load(id));
    }

    @Transactional
    public CandidateResponse update(UUID id, UpdateCandidateRequest request) {
        Candidate candidate = load(id);
        requireVersion(candidate, request.version());
        CandidateProfile profile = CandidateMapper.toProfile(request);
        if (!candidate.getEmail().equals(profile.email()) && candidates.existsByEmailAndIdNot(profile.email(), id)) {
            throw new DuplicateEmailException();
        }
        candidate.updateProfile(profile, now());
        candidates.saveAndFlush(candidate);
        log.info("candidate.updated id={} version={}", id, candidate.getVersion());
        return CandidateMapper.toResponse(candidate);
    }

    @Transactional
    public CandidateResponse changeStatus(UUID id, ChangeStatusRequest request) {
        Candidate candidate = load(id);
        requireVersion(candidate, request.version());
        Instant now = now();
        CandidateStatus previous = candidate.changeStatus(request.status(), now);
        statusChanges.save(CandidateStatusChange.of(id, previous, candidate.getStatus(), request.note(), now));
        candidates.saveAndFlush(candidate);
        log.info("candidate.status_changed id={} from={} to={}", id, previous, candidate.getStatus());
        return CandidateMapper.toResponse(candidate);
    }

    public PageResponse<StatusChangeResponse> statusHistory(UUID id, int page, int size) {
        if (!candidates.existsById(id)) {
            throw new CandidateNotFoundException(id);
        }
        Pageable pageable = PageRequest.of(page, size, HISTORY_ORDER);
        return PageResponse.of(statusChanges.findByCandidateId(id, pageable).map(CandidateMapper::toResponse));
    }

    /** Hard delete (personal data is erased, GDPR). Skills and status history are removed by FK cascade. */
    @Transactional
    public void delete(UUID id) {
        candidates.delete(load(id));
        log.info("candidate.deleted id={}", id);
    }

    public PageResponse<CandidateResponse> search(CandidateSearchRequest request) {
        CandidateSearchCriteria criteria = SearchRequestTranslator.toCriteria(request);
        Pageable pageable = SearchRequestTranslator.toPageable(request);
        return PageResponse.of(candidates
                .findAll(CandidateSpecifications.matching(criteria), pageable)
                .map(CandidateMapper::toResponse));
    }

    private Candidate load(UUID id) {
        return candidates.findById(id).orElseThrow(() -> new CandidateNotFoundException(id));
    }

    /** Setting the version on a managed entity does not make JPA compare it, so the check is explicit. */
    private static void requireVersion(Candidate candidate, long expectedVersion) {
        if (candidate.getVersion() != expectedVersion) {
            throw new VersionConflictException(expectedVersion, candidate.getVersion());
        }
    }

    /** PostgreSQL stores microseconds; truncating keeps responses identical to what a later read returns. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
