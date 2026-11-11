package com.recruiting.candidates.candidate.persistence;

import com.recruiting.candidates.candidate.domain.CandidateStatusChange;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateStatusChangeRepository extends JpaRepository<CandidateStatusChange, UUID> {

    Page<CandidateStatusChange> findByCandidateId(UUID candidateId, Pageable pageable);
}
