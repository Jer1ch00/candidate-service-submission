package com.recruiting.candidates.candidate.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.recruiting.candidates.candidate.domain.CandidateStatus;
import com.recruiting.candidates.candidate.domain.SkillMatch;
import com.recruiting.candidates.candidate.dto.CandidateResponse;
import com.recruiting.candidates.candidate.dto.CandidateSearchRequest;
import com.recruiting.candidates.candidate.dto.CreateCandidateRequest;
import com.recruiting.candidates.candidate.service.CandidateService;
import com.recruiting.candidates.common.config.WebFilterConfig;
import com.recruiting.candidates.common.web.PageResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** HTTP contract of the controller: status codes, headers, binding and validation. The service is mocked. */
@WebMvcTest(CandidateController.class)
@Import(WebFilterConfig.class)
@ActiveProfiles("test")
class CandidateControllerTest {

    static final String API_KEY = "test-api-key-0123456789";
    static final String ROTATED_API_KEY = "rotated-test-key-0123456789";
    static final UUID ID = UUID.fromString("0190a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b");

    static final String VALID_CREATE_BODY = """
            {
              "firstName": "Priya", "lastName": "Sharma", "email": "priya.sharma@example.com",
              "phone": "+91 98765 43210", "currentTitle": "Backend Engineer", "currentCompany": "Acme",
              "city": "Bengaluru", "country": "IN", "yearsOfExperience": 6, "noticePeriodDays": 30,
              "skills": ["java", "spring boot"]
            }
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CandidateService service;

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        when(service.create(any())).thenReturn(response());

        mvc.perform(withKey(post("/api/v1/candidates"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/candidates/" + ID)))
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.createdAt").value("2026-03-01T10:15:30Z"));

        ArgumentCaptor<CreateCandidateRequest> captor = ArgumentCaptor.forClass(CreateCandidateRequest.class);
        verify(service).create(captor.capture());
        assertThat(captor.getValue().skills()).containsExactlyInAnyOrder("java", "spring boot");
    }

    @Test
    void createReportsEveryInvalidField() throws Exception {
        String body = """
                {
                  "firstName": " ", "lastName": "Sharma", "email": "not-an-email", "phone": "12",
                  "country": "XX", "yearsOfExperience": 61, "noticePeriodDays": -1, "skills": ["java", ""]
                }
                """;

        mvc.perform(withKey(post("/api/v1/candidates"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field")
                        .value(containsInAnyOrder(
                                "firstName",
                                "email",
                                "phone",
                                "country",
                                "yearsOfExperience",
                                "noticePeriodDays",
                                "skills[]")));
        verify(service, never()).create(any());
    }

    @Test
    void statusCannotBeSmuggledIntoAnUpdate() throws Exception {
        String body = VALID_CREATE_BODY.replace("\"skills\"", "\"version\": 0, \"status\": \"HIRED\", \"skills\"");

        mvc.perform(withKey(put("/api/v1/candidates/{id}", ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.detail").value("Unknown field 'status'."));
    }

    @Test
    void wrongJsonTypeNamesTheField() throws Exception {
        String body = VALID_CREATE_BODY.replace("\"yearsOfExperience\": 6", "\"yearsOfExperience\": \"six\"");

        mvc.perform(withKey(post("/api/v1/candidates"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.detail").value("Field 'yearsOfExperience' must be a whole number."));
    }

    @Test
    void searchBindsRepeatedAndTypedParameters() throws Exception {
        when(service.search(any())).thenReturn(new PageResponse<>(List.of(response()), 1, 10, 11, 2, false));

        mvc.perform(withKey(get("/api/v1/candidates"))
                        .param("q", "priya java")
                        .param("status", "NEW", "SCREENING")
                        .param("skills", "java", "spring")
                        .param("skillsMatch", "ANY")
                        .param("minExperience", "2")
                        .param("country", "IN")
                        .param("createdFrom", "2026-01-01T00:00:00Z")
                        .param("page", "1")
                        .param("size", "10")
                        .param("sort", "lastName,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(ID.toString()))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalItems").value(11))
                .andExpect(jsonPath("$.hasNext").value(false));

        ArgumentCaptor<CandidateSearchRequest> captor = ArgumentCaptor.forClass(CandidateSearchRequest.class);
        verify(service).search(captor.capture());
        CandidateSearchRequest request = captor.getValue();
        assertThat(request.status()).containsExactly(CandidateStatus.NEW, CandidateStatus.SCREENING);
        assertThat(request.skills()).containsExactly("java", "spring");
        assertThat(request.skillsMatch()).isEqualTo(SkillMatch.ANY);
        assertThat(request.createdFrom()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(request.sort()).isEqualTo("lastName,asc");
    }

    @Test
    void oversizedPageIsRejectedNotClamped() throws Exception {
        mvc.perform(withKey(get("/api/v1/candidates")).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
        verify(service, never()).search(any());
    }

    @Test
    void unknownEnumInQueryListsAllowedValues() throws Exception {
        mvc.perform(withKey(get("/api/v1/candidates")).param("status", "NEW", "PROMOTED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.errors[0].message", startsWith("must be one of [NEW, SCREENING")));
    }

    @Test
    void historyPageSizeIsValidated() throws Exception {
        mvc.perform(withKey(get("/api/v1/candidates/{id}/status-history", ID)).param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mvc.perform(withKey(delete("/api/v1/candidates/{id}", ID))).andExpect(status().isNoContent());

        verify(service).delete(eq(ID));
    }

    @Test
    void rotatedKeyIsAcceptedAndResponsesCarrySecurityAndTracingHeaders() throws Exception {
        when(service.get(ID)).thenReturn(response());

        mvc.perform(get("/api/v1/candidates/{id}", ID)
                        .header("X-API-Key", ROTATED_API_KEY)
                        .header("X-Request-Id", "trace-abc-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "trace-abc-123"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void missingKeyIsRejectedBeforeReachingTheController() throws Exception {
        mvc.perform(get("/api/v1/candidates/{id}", ID))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "ApiKey header=\"X-API-Key\""))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        verify(service, never()).get(any());
    }

    @Test
    void validationMessagesNeverEchoSubmittedValues() throws Exception {
        String body = VALID_CREATE_BODY.replace("priya.sharma@example.com", "secret-value-not-an-email");

        mvc.perform(withKey(post("/api/v1/candidates"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$..message", hasItem("must be a well-formed email address")))
                .andExpect(result ->
                        assertThat(result.getResponse().getContentAsString()).doesNotContain("secret-value"));
    }

    static MockHttpServletRequestBuilder withKey(MockHttpServletRequestBuilder builder) {
        return builder.header("X-API-Key", API_KEY);
    }

    static CandidateResponse response() {
        Instant created = Instant.parse("2026-03-01T10:15:30Z");
        return new CandidateResponse(
                ID,
                "Priya",
                "Sharma",
                "priya.sharma@example.com",
                "+91 98765 43210",
                "Backend Engineer",
                "Acme",
                "Bengaluru",
                "IN",
                6,
                30,
                List.of("java", "spring boot"),
                CandidateStatus.NEW,
                created,
                created,
                0);
    }
}
