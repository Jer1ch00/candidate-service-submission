package com.recruiting.candidates.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.recruiting.candidates.candidate.domain.Candidate;
import com.recruiting.candidates.candidate.domain.CandidateNotFoundException;
import com.recruiting.candidates.candidate.domain.CandidateProfile;
import com.recruiting.candidates.candidate.domain.CandidateStatus;
import com.recruiting.candidates.candidate.domain.CandidateStatusChange;
import com.recruiting.candidates.candidate.domain.DuplicateEmailException;
import com.recruiting.candidates.candidate.domain.InvalidStatusTransitionException;
import com.recruiting.candidates.candidate.domain.VersionConflictException;
import com.recruiting.candidates.candidate.dto.ChangeStatusRequest;
import com.recruiting.candidates.candidate.dto.CreateCandidateRequest;
import com.recruiting.candidates.candidate.dto.UpdateCandidateRequest;
import com.recruiting.candidates.candidate.persistence.CandidateRepository;
import com.recruiting.candidates.candidate.persistence.CandidateStatusChangeRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** Business rules of the service in isolation. Persistence behaviour is covered against PostgreSQL elsewhere. */
@ExtendWith(MockitoExtension.class)
class CandidateServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:15:30.123456789Z");
    private static final UUID ID = UUID.fromString("0190a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b");

    @Mock
    private CandidateRepository candidates;

    @Mock
    private CandidateStatusChangeRepository statusChanges;

    private CandidateService service;

    @BeforeEach
    void setUp() {
        service = new CandidateService(candidates, statusChanges, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createRejectsDuplicateEmailBeforeWriting() {
        when(candidates.existsByEmail("priya.sharma@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest(" PRIYA.Sharma@example.com ")))
                .isInstanceOf(DuplicateEmailException.class);
        verify(candidates, never()).saveAndFlush(any());
    }

    @Test
    void createRecordsInitialHistoryEntryWithMicrosecondTimestamp() {
        when(candidates.saveAndFlush(any(Candidate.class)))
                .thenAnswer(invocation -> persisted(invocation.getArgument(0)));

        var response = service.create(createRequest("priya.sharma@example.com"));

        ArgumentCaptor<CandidateStatusChange> history = ArgumentCaptor.forClass(CandidateStatusChange.class);
        verify(statusChanges).save(history.capture());
        assertThat(history.getValue().getFromStatus()).isNull();
        assertThat(history.getValue().getToStatus()).isEqualTo(CandidateStatus.NEW);
        assertThat(response.status()).isEqualTo(CandidateStatus.NEW);
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-03-01T10:15:30.123456Z"));
    }

    @Test
    void getUnknownIdThrowsNotFound() {
        when(candidates.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(ID)).isInstanceOf(CandidateNotFoundException.class);
    }

    @Test
    void updateWithStaleVersionThrowsConflict() {
        when(candidates.findById(ID)).thenReturn(Optional.of(existing(3L)));

        assertThatThrownBy(() -> service.update(ID, updateRequest("priya.sharma@example.com", 2L)))
                .isInstanceOf(VersionConflictException.class)
                .satisfies(ex -> assertThat(((VersionConflictException) ex).details())
                        .containsEntry("expectedVersion", 2L)
                        .containsEntry("currentVersion", 3L));
    }

    @Test
    void updateToAnotherCandidatesEmailThrowsDuplicate() {
        when(candidates.findById(ID)).thenReturn(Optional.of(existing(0L)));
        when(candidates.existsByEmailAndIdNot("taken@example.com", ID)).thenReturn(true);

        assertThatThrownBy(() -> service.update(ID, updateRequest("taken@example.com", 0L)))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void updateKeepingOwnEmailSkipsDuplicateCheck() {
        Candidate candidate = existing(0L);
        when(candidates.findById(ID)).thenReturn(Optional.of(candidate));

        service.update(ID, updateRequest("priya.sharma@example.com", 0L));

        verify(candidates, never()).existsByEmailAndIdNot(any(), any());
        verify(candidates).saveAndFlush(candidate);
    }

    @Test
    void changeStatusRecordsHistory() {
        when(candidates.findById(ID)).thenReturn(Optional.of(existing(0L)));

        service.changeStatus(ID, new ChangeStatusRequest(CandidateStatus.SCREENING, 0L, "  phone screen booked "));

        ArgumentCaptor<CandidateStatusChange> history = ArgumentCaptor.forClass(CandidateStatusChange.class);
        verify(statusChanges).save(history.capture());
        assertThat(history.getValue().getFromStatus()).isEqualTo(CandidateStatus.NEW);
        assertThat(history.getValue().getToStatus()).isEqualTo(CandidateStatus.SCREENING);
        assertThat(history.getValue().getNote()).isEqualTo("phone screen booked");
    }

    @Test
    void invalidStatusChangeWritesNothing() {
        when(candidates.findById(ID)).thenReturn(Optional.of(existing(0L)));

        assertThatThrownBy(() -> service.changeStatus(ID, new ChangeStatusRequest(CandidateStatus.HIRED, 0L, null)))
                .isInstanceOf(InvalidStatusTransitionException.class);
        verify(statusChanges, never()).save(any());
        verify(candidates, never()).saveAndFlush(any());
    }

    @Test
    void deleteUnknownIdThrowsNotFound() {
        when(candidates.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(ID)).isInstanceOf(CandidateNotFoundException.class);
        verify(candidates, never()).delete(any(Candidate.class));
    }

    @Test
    void historyOfUnknownCandidateThrowsNotFound() {
        when(candidates.existsById(ID)).thenReturn(false);

        assertThatThrownBy(() -> service.statusHistory(ID, 0, 20)).isInstanceOf(CandidateNotFoundException.class);
    }

    private static CreateCandidateRequest createRequest(String email) {
        return new CreateCandidateRequest(
                "Priya", "Sharma", email, null, "Engineer", "Acme", "Bengaluru", "IN", 6, 30, Set.of("java"));
    }

    private static UpdateCandidateRequest updateRequest(String email, long version) {
        return new UpdateCandidateRequest(
                "Priya", "Sharma", email, null, "Engineer", "Acme", "Pune", "IN", 7, 30, Set.of("java"), version);
    }

    /** Simulates what JPA assigns on persist: id and initial version. */
    private static Candidate persisted(Candidate candidate) {
        ReflectionTestUtils.setField(candidate, "id", ID);
        ReflectionTestUtils.setField(candidate, "version", 0L);
        return candidate;
    }

    private static Candidate existing(long version) {
        Candidate candidate = Candidate.register(
                new CandidateProfile(
                        "Priya",
                        "Sharma",
                        "priya.sharma@example.com",
                        null,
                        "Engineer",
                        "Acme",
                        "Bengaluru",
                        "IN",
                        6,
                        30,
                        Set.of("java")),
                NOW);
        ReflectionTestUtils.setField(candidate, "id", ID);
        ReflectionTestUtils.setField(candidate, "version", version);
        return candidate;
    }
}
