# Candidate Service

A Java 21 / Spring Boot 4 REST service to **create, view, update, delete, search and filter candidates** and move them
through a hiring pipeline. PostgreSQL persistence, typed error contract, API-key protection, and tests at every level.

| Document | What it covers |
|---|---|
| [docs/DESIGN.md](docs/DESIGN.md) | Architecture, object-oriented design principles (OOP, SOLID, patterns), design decisions and trade-offs, data model, error handling, security, performance evidence, scaling plan, future scope |
| [docs/AI_USAGE.md](docs/AI_USAGE.md) | How AI was used, how its output was validated, and where it was overruled |
| [http/candidates.http](http/candidates.http) | Ready-to-run sample requests (IntelliJ HTTP Client / VS Code REST Client) |

## Quick start

**Prerequisite:** Docker (Docker Desktop on Windows/macOS). Nothing else; Java and Maven are not needed to run it.

```bash
docker compose up --build
```

This starts PostgreSQL 17 and the service with the `dev` profile, which loads 30 sample candidates and enables
Swagger UI. When `docker compose ps` shows the app as healthy:

| URL | |
|---|---|
| http://localhost:8080/swagger-ui.html | Interactive API documentation (click **Authorize**, enter the API key) |
| http://localhost:8080/v3/api-docs | OpenAPI 3 contract |
| http://localhost:8081/actuator/health | Health (management port, bound to localhost) |

Default API key: `local-dev-api-key-change-me` (override with `API_KEYS`, see [Configuration](#configuration)).

```bash
# Search: Java AND Spring Boot, 4-10 years, India, newest first
curl -s -H "X-API-Key: local-dev-api-key-change-me" \
  "http://localhost:8080/api/v1/candidates?skills=java&skills=spring%20boot&minExperience=4&maxExperience=10&country=IN"

# Create
curl -s -i -H "X-API-Key: local-dev-api-key-change-me" -H "Content-Type: application/json" \
  -d '{"firstName":"Asha","lastName":"Kumar","email":"asha.kumar@example.com","yearsOfExperience":5,"skills":["Java"]}' \
  http://localhost:8080/api/v1/candidates
```

Data is kept in the `postgres-data` volume across restarts. `docker compose down -v` deletes it.

Port 8080 already taken (e.g. by a local Tomcat)? Choose other host ports:
`APP_PORT=8090 MANAGEMENT_PORT=8091 docker compose up --build` (or set them in `.env`).

## API at a glance

All endpoints are under `/api/v1/candidates` and require the `X-API-Key` header.

| Method | Path | Purpose | Success |
|---|---|---|---|
| `POST` | `/candidates` | Create (starts in status `NEW`) | `201` + `Location` |
| `GET` | `/candidates/{id}` | View | `200` |
| `PUT` | `/candidates/{id}` | Replace profile; requires the `version` you last read | `200` |
| `PATCH` | `/candidates/{id}/status` | Move through the pipeline (rules enforced) | `200` |
| `GET` | `/candidates/{id}/status-history` | Pipeline history, newest first, paginated | `200` |
| `DELETE` | `/candidates/{id}` | Delete permanently (skills and history included) | `204` |
| `GET` | `/candidates` | Search and filter, always paginated | `200` |

**Search parameters** (all optional, combined with AND): `q` (free text over name, email, title, company; up to 5
terms, all must match), `status` (repeatable, any of), `skills` (repeatable) with `skillsMatch=ALL|ANY`,
`minExperience`, `maxExperience`, `city`, `country`, `maxNoticePeriodDays`, `createdFrom` (inclusive), `createdTo`
(exclusive), `page` (from 0), `size` (1-100, default 20), `sort` (`createdAt|updatedAt|firstName|lastName|yearsOfExperience`
with optional `,asc|,desc`; default `createdAt,desc`).

**Pipeline:** `NEW → SCREENING → INTERVIEWING → OFFERED → HIRED`. Forward moves may skip stages; any open stage can
move to `REJECTED`; `REJECTED` can be re-opened to `NEW`; `HIRED` is final.

**Errors** are [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem documents with a stable `code`:

```json
{
  "type": "urn:candidate-service:problem:validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "Request contains invalid fields.",
  "instance": "/api/v1/candidates",
  "code": "VALIDATION_FAILED",
  "requestId": "4f6c1d0e-6a39-4a39-9d44-5d1f0f3b8a2e",
  "timestamp": "2026-11-11T14:00:00Z",
  "errors": [ { "field": "email", "message": "must be a well-formed email address" } ]
}
```

Codes: `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `INVALID_PARAMETER`, `UNAUTHORIZED`, `CANDIDATE_NOT_FOUND`,
`ROUTE_NOT_FOUND`, `METHOD_NOT_ALLOWED`, `NOT_ACCEPTABLE`, `DUPLICATE_EMAIL`, `VERSION_CONFLICT`,
`INVALID_STATUS_TRANSITION`, `DATA_CONFLICT`, `PAYLOAD_TOO_LARGE`, `UNSUPPORTED_MEDIA_TYPE`, `INTERNAL_ERROR`,
`SERVICE_UNAVAILABLE`. Every response carries an `X-Request-Id` header that matches the server logs.

## Running the tests

Requires Java 21 and Docker (integration tests start PostgreSQL with Testcontainers). 158 tests, about one minute;
94.5% line coverage.

```bash
./mvnw verify                 # all tests + coverage report (target/site/jacoco/index.html)
./mvnw spotless:check verify  # what CI runs (also checks formatting; fix with ./mvnw spotless:apply)
```

| Suite | Examples | Needs Docker |
|---|---|---|
| Unit | Status transition matrix (all 36 pairs), service rules, normalization, search translation, filters | No |
| Web slice | HTTP contract, validation, a parameterized test that renders **every** `ErrorCode` | No |
| Architecture | ArchUnit layering and coding rules | No |
| Repository | Every filter alone and combined against real PostgreSQL, wildcard escaping, cascades, constraints | Yes |
| Integration | Full lifecycle over HTTP, concurrent updates/creates (exactly one winner), actuator on the management port | Yes |

## Running without Docker for the app

With a local PostgreSQL 17 (database, user and password `candidates`):

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Or let Testcontainers provide the database (Docker needed, no local PostgreSQL):

```bash
./mvnw spring-boot:test-run -Dspring-boot.run.profiles=dev
```

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `API_KEYS` | none (`dev`: `local-dev-api-key-change-me`) | Comma-separated accepted keys, at least 16 characters each. The service refuses to start without one. Several keys allow zero-downtime rotation. |
| `DB_URL` | `jdbc:postgresql://localhost:5432/candidates` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `candidates` / `candidates` | Database credentials |
| `SPRING_PROFILES_ACTIVE` | none (compose: `dev`) | `dev`: sample data + Swagger UI. `prod`: JSON (ECS) logs. |
| `APP_PORT` / `MANAGEMENT_PORT` / `DB_PORT` | `8080` / `8081` / `5432` | Host ports published by docker compose |

Copy `.env.example` to `.env` to change them for docker compose.

## Project layout

```
src/main/java/com/recruiting/candidates
├── controller/              REST controller: HTTP mapping and validation only
├── service/                 service interfaces (the contracts the controller depends on)
│   └── impl/                implementations: transactions and business rules
├── repository/              Spring Data repositories
│   └── specification/       composable search criteria (JPA Specifications)
├── entity/                  JPA entities: Candidate, CandidateStatusChange
├── model/                   status workflow, skill-match mode, value objects
├── dto/request, dto/response   API request/response records with validation rules
├── mapper/                  entity ↔ DTO mapping, search-request translation
├── exception/               error codes, typed exceptions, the global exception handler
├── filter/                  servlet filters: request id, access log, security headers, size limit, API key
├── config/                  Spring configuration and typed properties
├── validation/              custom validation constraint (country code)
└── util/                    input normalization, request context
src/main/resources/db/migration   Flyway schema (V1)
src/main/resources/db/seed        dev-only sample data
scripts/                          1M-row data generator and EXPLAIN script behind the numbers in DESIGN.md
```
