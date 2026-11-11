package com.recruiting.candidates;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * Real HTTP server: operational endpoints live on the separate management port, not on the public API port, and do
 * not require the API key (orchestrators call them).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
@AutoConfigureMetrics // metrics export is off in tests unless requested
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class OperationalEndpointsTest {

    @LocalServerPort
    private int serverPort;

    @LocalManagementPort
    private int managementPort;

    private final RestClient http = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> {})
            .build();

    @Test
    void livenessAndReadinessIncludingDatabaseAreUp() {
        assertThat(getManagement("/actuator/health/liveness").getBody()).contains("\"status\":\"UP\"");
        assertThat(getManagement("/actuator/health/readiness").getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void prometheusMetricsIncludeHttpServerRequests() {
        http.get()
                .uri("http://localhost:" + serverPort + "/api/v1/candidates")
                .header("X-API-Key", "test-api-key-0123456789")
                .retrieve()
                .toBodilessEntity();

        ResponseEntity<String> metrics = getManagement("/actuator/prometheus");

        assertThat(metrics.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(metrics.getBody()).contains("http_server_requests_seconds_count");
    }

    @Test
    void actuatorIsNotExposedOnThePublicPort() {
        ResponseEntity<String> response = http.get()
                .uri("http://localhost:" + serverPort + "/actuator/health")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void swaggerIsDisabledOutsideTheDevProfile() {
        ResponseEntity<String> response = http.get()
                .uri("http://localhost:" + serverPort + "/v3/api-docs")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    private ResponseEntity<String> getManagement(String path) {
        return http.get()
                .uri("http://localhost:" + managementPort + path)
                .retrieve()
                .toEntity(String.class);
    }
}
