package com.recruiting.candidates.repository;

import com.recruiting.candidates.entity.CandidateStatusChange;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateStatusChangeRepository extends JpaRepository<CandidateStatusChange, UUID> {

    Page<CandidateStatusChange> findByCandidateId(UUID candidateId, Pageable pageable);
}
