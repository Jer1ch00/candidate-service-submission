package com.recruiting.candidates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The whole stack (filters, validation, service, JPA, Flyway schema, PostgreSQL) exercised through HTTP. Every
 * request commits its own transaction, exactly as in production.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CandidateApiIntegrationTest {

    private static final String API_KEY = "test-api-key-0123456789";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE candidate CASCADE");
    }

    @Test
    void fullCandidateLifecycle() throws Exception {
        // create
        MvcResult created = mvc.perform(
                        json(post("/api/v1/candidates"), candidateJson("Priya.Sharma@Example.com", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("priya.sharma@example.com"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.skills", contains("java", "spring boot")))
                .andReturn();
        String id = read(created, "$.id");
        assertThat(created.getResponse().getHeader("Location")).endsWith("/api/v1/candidates/" + id);

        // view
        mvc.perform(key(get("/api/v1/candidates/{id}", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Priya"))
                .andExpect(jsonPath("$.createdAt").value(read(created, "$.createdAt")));

        // update (version 0 -> 1)
        mvc.perform(json(
                        put("/api/v1/candidates/{id}", id),
                        candidateJson("priya.sharma@example.com", 0)
                                .replace("Bengaluru", "Pune")
                                .replace("\" spring boot \"", "\"kotlin\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Pune"))
                .andExpect(jsonPath("$.skills", contains("java", "kotlin")))
                .andExpect(jsonPath("$.version").value(1));

        // stale update is rejected
        mvc.perform(json(put("/api/v1/candidates/{id}", id), candidateJson("priya.sharma@example.com", 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.currentVersion").value(1));

        // pipeline: NEW -> SCREENING allowed, SCREENING -> HIRED not
        mvc.perform(json(patch("/api/v1/candidates/{id}/status", id), "{\"status\":\"SCREENING\",\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCREENING"))
                .andExpect(jsonPath("$.version").value(2));
        mvc.perform(json(patch("/api/v1/candidates/{id}/status", id), "{\"status\":\"HIRED\",\"version\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

        // history, newest first
        mvc.perform(key(get("/api/v1/candidates/{id}/status-history", id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].fromStatus").value("NEW"))
                .andExpect(jsonPath("$.items[0].toStatus").value("SCREENING"))
                .andExpect(jsonPath("$.items[1].fromStatus").isEmpty())
                .andExpect(jsonPath("$.items[1].toStatus").value("NEW"));

        // search finds it
        mvc.perform(key(get("/api/v1/candidates")).param("q", "sharma").param("status", "SCREENING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id));

        // delete, then it is gone everywhere
        mvc.perform(key(delete("/api/v1/candidates/{id}", id))).andExpect(status().isNoContent());
        mvc.perform(key(get("/api/v1/candidates/{id}", id)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CANDIDATE_NOT_FOUND"));
        mvc.perform(key(get("/api/v1/candidates/{id}/status-history", id))).andExpect(status().isNotFound());
        mvc.perform(key(delete("/api/v1/candidates/{id}", id))).andExpect(status().isNotFound());
    }

    @Test
    void emailUniquenessIsCaseInsensitive() throws Exception {
        mvc.perform(json(post("/api/v1/candidates"), candidateJson("priya@example.com", null)))
                .andExpect(status().isCreated());

        mvc.perform(json(post("/api/v1/candidates"), candidateJson("PRIYA@Example.COM", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void paginationMetadataAndBoundaries() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(json(post("/api/v1/candidates"), candidateJson("person" + i + "@example.com", null)))
                    .andExpect(status().isCreated());
        }

        mvc.perform(key(get("/api/v1/candidates")).param("size", "2").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalItems").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.hasNext").value(false));

        mvc.perform(key(get("/api/v1/candidates")).param("size", "2").param("page", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalItems").value(5));
    }

    /**
     * Many clients update the same version at once: exactly one wins, the rest get 409, whether they lose at the
     * explicit version check or at the database's optimistic-lock check during commit.
     */
    @Test
    void concurrentUpdatesOfTheSameVersionProduceExactlyOneWinner() throws Exception {
        String id = read(
                mvc.perform(json(post("/api/v1/candidates"), candidateJson("race@example.com", null)))
                        .andReturn(),
                "$.id");

        List<Integer> statuses = runConcurrently(
                8,
                attempt -> () -> mvc.perform(json(
                                put("/api/v1/candidates/{id}", id),
                                candidateJson("race@example.com", 0).replace("Bengaluru", "City " + attempt)))
                        .andReturn()
                        .getResponse()
                        .getStatus());

        assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(7);
    }

    @Test
    void concurrentCreatesWithTheSameEmailProduceExactlyOneCandidate() throws Exception {
        List<Integer> statuses = runConcurrently(
                8,
                attempt -> () -> mvc.perform(json(post("/api/v1/candidates"), candidateJson("same@example.com", null)))
                        .andReturn()
                        .getResponse()
                        .getStatus());

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(7);
        assertThat(jdbc.queryForObject("select count(*) from candidate", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void requestsWithoutAValidKeyNeverReachTheDatabase() throws Exception {
        mvc.perform(post("/api/v1/candidates")
                        .header("X-API-Key", "not-a-real-key-0000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(candidateJson("intruder@example.com", null)))
                .andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForObject("select count(*) from candidate", Integer.class))
                .isZero();
    }

    // ---------------------------------------------------------------------------------------------------------------

    private interface Attempt {
        Callable<Integer> call(int attempt);
    }

    private static List<Integer> runConcurrently(int threads, Attempt attempt) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<Integer> call = attempt.call(i);
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<Integer> results = new ArrayList<>();
            for (Future<Integer> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static String candidateJson(String email, Integer version) {
        String versionField = version == null ? "" : ", \"version\": " + version;
        return """
                {"firstName": "Priya", "lastName": "Sharma", "email": "%s", "currentTitle": "Backend Engineer",
                 "currentCompany": "Acme", "city": "Bengaluru", "country": "IN", "yearsOfExperience": 6,
                 "noticePeriodDays": 30, "skills": ["Java", " spring boot "]%s}
                """.formatted(email, versionField);
    }

    private static String read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path).toString();
    }

    private static MockHttpServletRequestBuilder key(MockHttpServletRequestBuilder builder) {
        return builder.header("X-API-Key", API_KEY);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return key(builder).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
