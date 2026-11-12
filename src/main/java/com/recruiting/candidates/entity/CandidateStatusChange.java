package com.recruiting.candidates.entity;

import com.recruiting.candidates.model.CandidateStatus;
import com.recruiting.candidates.util.Normalizer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

/**
 * One entry of a candidate's pipeline history. Append-only: Hibernate never issues updates for it.
 *
 * <p>References the candidate by id rather than an association: history is written and read on its own and must not
 * be loaded with the candidate.
 */
@Entity
@Immutable
@Table(name = "candidate_status_history")
public class CandidateStatusChange {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "candidate_id", nullable = false, updatable = false)
    private UUID candidateId;

    /** {@code null} for the entry written when the candidate is created. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20, updatable = false)
    private CandidateStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20, updatable = false)
    private CandidateStatus toStatus;

    @Column(name = "note", length = 500, updatable = false)
    private String note;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected CandidateStatusChange() {
        // for JPA
    }

    public static CandidateStatusChange of(
            UUID candidateId, CandidateStatus fromStatus, CandidateStatus toStatus, String note, Instant changedAt) {
        CandidateStatusChange change = new CandidateStatusChange();
        change.changedAt = changedAt;
        change.candidateId = candidateId;
        change.fromStatus = fromStatus;
        change.toStatus = toStatus;
        change.note = Normalizer.text(note);
        return change;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCandidateId() {
        return candidateId;
    }

    public CandidateStatus getFromStatus() {
        return fromStatus;
    }

    public CandidateStatus getToStatus() {
        return toStatus;
    }

    public String getNote() {
        return note;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
