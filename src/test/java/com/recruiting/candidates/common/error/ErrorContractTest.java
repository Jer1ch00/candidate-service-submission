package com.recruiting.candidates.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruiting.candidates.candidate.api.CandidateController;
import com.recruiting.candidates.candidate.domain.CandidateNotFoundException;
import com.recruiting.candidates.candidate.domain.CandidateStatus;
import com.recruiting.candidates.candidate.domain.DuplicateEmailException;
import com.recruiting.candidates.candidate.domain.InvalidStatusTransitionException;
import com.recruiting.candidates.candidate.domain.VersionConflictException;
import com.recruiting.candidates.candidate.service.CandidateService;
import com.recruiting.candidates.common.config.WebFilterConfig;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * Every {@link ErrorCode} is produced by a real request and rendered with the same RFC 9457 shape. The coverage check
 * fails the build when a new error code is added without a scenario here.
 */
@WebMvcTest(CandidateController.class)
@Import(WebFilterConfig.class)
@ActiveProfiles("test")
class ErrorContractTest {

    private static final String API_KEY = "test-api-key-0123456789";
    private static final UUID ID = UUID.fromString("0190a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b");
    private static final String CANDIDATE_PATH = "/api/v1/candidates/" + ID;
    private static final String VALID_BODY = """
            {"firstName": "Priya", "lastName": "Sharma", "email": "priya@example.com", "yearsOfExperience": 6}
            """;
    private static final String VALID_UPDATE_BODY = """
            {"firstName": "Priya", "lastName": "Sharma", "email": "priya@example.com", "yearsOfExperience": 6,
             "version": 1}
            """;
    private static final String VALID_STATUS_BODY = """
            {"status": "HIRED", "version": 1}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CandidateService service;

    record Scenario(
            ErrorCode code, String path, MockHttpServletRequestBuilder request, Consumer<CandidateService> stubbing) {

        @Override
        public String toString() {
            return code.name();
        }
    }

    static Stream<Scenario> scenarios() {
        return Stream.of(
                scenario(ErrorCode.VALIDATION_FAILED, "/api/v1/candidates", json(post("/api/v1/candidates"), "{}")),
                scenario(
                        ErrorCode.MALFORMED_REQUEST,
                        "/api/v1/candidates",
                        json(post("/api/v1/candidates"), "{\"firstName\": ")),
                scenario(
                        ErrorCode.INVALID_PARAMETER,
                        "/api/v1/candidates/not-a-uuid",
                        key(get("/api/v1/candidates/not-a-uuid"))),
                scenario(ErrorCode.UNAUTHORIZED, CANDIDATE_PATH, get(CANDIDATE_PATH)),
                new Scenario(
                        ErrorCode.CANDIDATE_NOT_FOUND,
                        CANDIDATE_PATH,
                        key(get(CANDIDATE_PATH)),
                        service -> when(service.get(ID)).thenThrow(new CandidateNotFoundException(ID))),
                scenario(ErrorCode.ROUTE_NOT_FOUND, "/api/v1/nothing-here", key(get("/api/v1/nothing-here"))),
                scenario(
                        ErrorCode.METHOD_NOT_ALLOWED,
                        "/api/v1/candidates",
                        json(patch("/api/v1/candidates"), VALID_BODY)),
                scenario(
                        ErrorCode.NOT_ACCEPTABLE,
                        CANDIDATE_PATH,
                        key(get(CANDIDATE_PATH)).accept(MediaType.APPLICATION_XML)),
                new Scenario(
                        ErrorCode.DUPLICATE_EMAIL,
                        "/api/v1/candidates",
                        json(post("/api/v1/candidates"), VALID_BODY),
                        service -> when(service.create(any())).thenThrow(new DuplicateEmailException())),
                new Scenario(
                        ErrorCode.VERSION_CONFLICT,
                        CANDIDATE_PATH,
                        json(put(CANDIDATE_PATH), VALID_UPDATE_BODY),
                        service -> when(service.update(any(), any())).thenThrow(new VersionConflictException(1, 2))),
                new Scenario(
                        ErrorCode.INVALID_STATUS_TRANSITION,
                        CANDIDATE_PATH + "/status",
                        json(patch(CANDIDATE_PATH + "/status"), VALID_STATUS_BODY),
                        service -> when(service.changeStatus(any(), any()))
                                .thenThrow(new InvalidStatusTransitionException(
                                        CandidateStatus.NEW, CandidateStatus.HIRED))),
                new Scenario(
                        ErrorCode.DATA_CONFLICT,
                        "/api/v1/candidates",
                        json(post("/api/v1/candidates"), VALID_BODY),
                        service -> when(service.create(any()))
                                .thenThrow(new DataIntegrityViolationException("check constraint"))),
                scenario(
                        ErrorCode.PAYLOAD_TOO_LARGE,
                        "/api/v1/candidates",
                        key(post("/api/v1/candidates"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(new byte[1024 * 1024 + 1])),
                scenario(
                        ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                        "/api/v1/candidates",
                        key(post("/api/v1/candidates"))
                                .contentType(MediaType.TEXT_PLAIN)
                                .content("hello")),
                new Scenario(
                        ErrorCode.INTERNAL_ERROR,
                        CANDIDATE_PATH,
                        key(get(CANDIDATE_PATH)),
                        service ->
                                when(service.get(ID)).thenThrow(new IllegalStateException("secret internal detail"))),
                new Scenario(
                        ErrorCode.SERVICE_UNAVAILABLE,
                        CANDIDATE_PATH,
                        key(get(CANDIDATE_PATH)),
                        service -> when(service.get(ID))
                                .thenThrow(new CannotCreateTransactionException("connection refused"))));
    }

    @Test
    void everyErrorCodeHasAScenario() {
        Set<ErrorCode> covered = scenarios().map(Scenario::code).collect(Collectors.toSet());
        Set<ErrorCode> missing = EnumSet.allOf(ErrorCode.class);
        missing.removeAll(covered);

        assertThat(missing).as("error codes without a contract test").isEmpty();
        assertThat(scenarios().count()).isEqualTo(ErrorCode.values().length);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void errorIsRenderedAsProblemDetailWithStableCode(Scenario scenario) throws Exception {
        scenario.stubbing().accept(service);

        var result = mvc.perform(scenario.request())
                .andExpect(status().is(scenario.code().status().value()))
                .andExpect(jsonPath("$.code").value(scenario.code().name()))
                .andExpect(jsonPath("$.type").value(scenario.code().type().toString()))
                .andExpect(jsonPath("$.title").value(scenario.code().title()))
                .andExpect(jsonPath("$.status").value(scenario.code().status().value()))
                .andExpect(jsonPath("$.instance").value(scenario.path()))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(header().exists("X-Request-Id"))
                // never leak internals
                .andExpect(content().string(not(containsString("secret internal detail"))))
                .andExpect(content().string(not(containsString("Exception"))))
                .andReturn();

        assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
    }

    @Test
    void invalidTransitionListsAllowedTargets() throws Exception {
        when(service.changeStatus(any(), any()))
                .thenThrow(new InvalidStatusTransitionException(CandidateStatus.NEW, CandidateStatus.HIRED));

        mvc.perform(json(patch(CANDIDATE_PATH + "/status"), VALID_STATUS_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.currentStatus").value("NEW"))
                .andExpect(jsonPath("$.requestedStatus").value("HIRED"))
                .andExpect(jsonPath("$.allowedTransitions.length()").value(4));
    }

    @Test
    void concurrentDuplicateCaughtByDatabaseConstraintIsStillDuplicateEmail() throws Exception {
        var constraint =
                new ConstraintViolationException("duplicate key", new SQLException("23505"), "uk_candidate_email");
        when(service.create(any())).thenThrow(new DataIntegrityViolationException("duplicate", constraint));

        mvc.perform(json(post("/api/v1/candidates"), VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void concurrentUpdateCaughtAtCommitIsStillVersionConflict() throws Exception {
        when(service.update(any(), any())).thenThrow(new ObjectOptimisticLockingFailureException("Candidate", ID));

        mvc.perform(json(put(CANDIDATE_PATH), VALID_UPDATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void serviceUnavailableTellsClientsWhenToRetry() throws Exception {
        when(service.get(ID)).thenThrow(new CannotCreateTransactionException("connection refused"));

        mvc.perform(key(get(CANDIDATE_PATH)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"));
    }

    @Test
    void validationErrorsListFieldsWithoutValues() throws Exception {
        mvc.perform(json(post("/api/v1/candidates"), "{}"))
                .andExpect(jsonPath("$.errors.length()").value(4))
                .andExpect(jsonPath("$.errors[0].field").exists())
                .andExpect(jsonPath("$.errors[0].message").exists());
    }

    private static Scenario scenario(ErrorCode code, String path, MockHttpServletRequestBuilder request) {
        return new Scenario(code, path, request, service -> {});
    }

    private static MockHttpServletRequestBuilder key(MockHttpServletRequestBuilder builder) {
        return builder.header("X-API-Key", API_KEY);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return key(builder).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
