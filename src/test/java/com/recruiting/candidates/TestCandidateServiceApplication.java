package com.recruiting.candidates;

import org.springframework.boot.SpringApplication;

/**
 * Runs the application against a throw-away PostgreSQL container: {@code ./mvnw spring-boot:test-run}. Needs only
 * Docker, no local database.
 */
public class TestCandidateServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(CandidateServiceApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
