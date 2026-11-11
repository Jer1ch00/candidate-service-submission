package com.recruiting.candidates.candidate.api;

import com.recruiting.candidates.candidate.dto.CandidateResponse;
import com.recruiting.candidates.candidate.dto.CandidateSearchRequest;
import com.recruiting.candidates.candidate.dto.ChangeStatusRequest;
import com.recruiting.candidates.candidate.dto.CreateCandidateRequest;
import com.recruiting.candidates.candidate.dto.StatusChangeResponse;
import com.recruiting.candidates.candidate.dto.UpdateCandidateRequest;
import com.recruiting.candidates.candidate.service.CandidateService;
import com.recruiting.candidates.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * HTTP adapter only: maps requests, triggers validation and delegates to {@link CandidateService}. No business logic
 * and no error handling here (errors are thrown and rendered by the global handler).
 */
@RestController
@RequestMapping(path = "/api/v1/candidates", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Candidates", description = "Manage, search and filter candidates")
@ApiResponse(
        responseCode = "4XX",
        description = "Client error; see `code` (VALIDATION_FAILED, CANDIDATE_NOT_FOUND, UNAUTHORIZED, ...)",
        content =
                @Content(
                        mediaType = "application/problem+json",
                        schema = @Schema(implementation = ProblemDetail.class)))
public class CandidateController {

    private final CandidateService candidateService;

    public CandidateController(CandidateService candidateService) {
        this.candidateService = candidateService;
    }

    @Operation(summary = "Create a candidate", description = "New candidates start in status NEW.")
    @ApiResponse(responseCode = "201", description = "Created; Location header points to the new candidate")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CandidateResponse> create(@Valid @RequestBody CreateCandidateRequest request) {
        CandidateResponse created = candidateService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "Get a candidate by id")
    @GetMapping("/{id}")
    public CandidateResponse get(@PathVariable UUID id) {
        return candidateService.get(id);
    }

    @Operation(
            summary = "Search and filter candidates",
            description = "All criteria are optional and combined with AND. Always paginated.")
    @GetMapping
    public PageResponse<CandidateResponse> search(@Valid @ParameterObject CandidateSearchRequest request) {
        return candidateService.search(request);
    }

    @Operation(
            summary = "Replace a candidate's profile",
            description = "Send the version from your last read; a stale version returns 409 VERSION_CONFLICT. "
                    + "Status is changed via PATCH /{id}/status.")
    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CandidateResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateCandidateRequest request) {
        return candidateService.update(id, request);
    }

    @Operation(
            summary = "Move a candidate through the hiring pipeline",
            description = "Allowed: forward moves (stages may be skipped), any open stage to REJECTED, REJECTED to "
                    + "NEW. HIRED is final. Other moves return 409 INVALID_STATUS_TRANSITION.")
    @PatchMapping(path = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CandidateResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeStatusRequest request) {
        return candidateService.changeStatus(id, request);
    }

    @Operation(summary = "Status history of a candidate, newest first")
    @GetMapping("/{id}/status-history")
    public PageResponse<StatusChangeResponse> statusHistory(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return candidateService.statusHistory(id, page, size);
    }

    @Operation(summary = "Delete a candidate", description = "Permanent; also removes skills and status history.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        candidateService.delete(id);
    }
}
