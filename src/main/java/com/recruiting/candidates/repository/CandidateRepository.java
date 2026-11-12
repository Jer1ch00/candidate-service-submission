package com.recruiting.candidates.repository;

import com.recruiting.candidates.entity.Candidate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CandidateRepository extends JpaRepository<Candidate, UUID>, JpaSpecificationExecutor<Candidate> {

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, UUID id);
}
