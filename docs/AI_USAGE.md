# AI-assisted development

I built this service with an AI coding assistant (Claude Code, running in my terminal with access to the repository).
I used it as a fast pair programmer: it drafted designs and code, I made the decisions, and nothing was accepted
without being checked by a compiler, a test, the actual library source, or a running system.

## Where and how I used AI

| Phase | What the AI did | What I did |
|---|---|---|
| Requirements | Summarized the assignment and mapped each requirement to a design element and a test | Checked the mapping against the PDF; kept a traceability table as the acceptance checklist |
| Design | Proposed architecture, data model, API, error contract and scaling plan, with alternatives | Set the bar ("SDE-2: rational decisions, no over-engineering"), chose between alternatives, changed several proposals (below) |
| Version selection | Proposed library versions | Had it verify against start.spring.io and Maven Central instead of trusting model memory |
| Implementation | Wrote the code in small steps, ran the compiler and tests after each | Reviewed each step; asked for explanations of anything non-obvious so I can defend it |
| Tests | Wrote unit, web-slice, repository, integration and architecture tests | Defined what must be proven (every error code, concurrency, real PostgreSQL behaviour) |
| Documentation | Drafted README and DESIGN | Edited for accuracy; performance numbers come from real measurements only |

## How AI output was validated

- **Compiler and tests as the arbiter.** Every change was followed by a build. 155 tests in total: 131 unit, web-slice
  and architecture tests run without Docker; 24 repository and integration tests run against real PostgreSQL via
  Testcontainers.
- **Running the real system, not just the tests.** I started the stack with docker compose and exercised every
  endpoint and error path by hand, stopped PostgreSQL mid-run to confirm the API answered 503 and recovered without a
  restart, and checked the logs for personal data (none).
- **Measuring instead of asserting performance.** The performance section of DESIGN.md comes from `EXPLAIN ANALYZE`
  on the exact SQL Hibernate generated (captured from the running app) over 1,000,000 generated rows, plus timed API
  calls.
- **Checking real APIs instead of trusting memory.** Spring Boot 4, Hibernate 7 and Jackson 3 changed packages and
  APIs. Before writing code against them, the assistant inspected the actual jars (`javap`), for example to confirm
  Jackson 3's `tools.jackson` exception classes, Spring Data 4's `Specification` API, and that Hibernate 7 supports
  UUIDv7 natively.
- **Versions from the source of truth.** start.spring.io reported Spring Boot 4.1.2 as current; Maven Central showed
  springdoc 3.2.0 is built against exactly Boot 4.1.2, so they are compatible by construction.
- **Tests designed to catch AI mistakes,** not to confirm them: the status workflow test compares against an
  independently written table of all 36 transitions; the error-contract test fails if any error code lacks a scenario;
  concurrency tests assert exactly one winner instead of assuming locking works.
- **History hygiene.** Every commit was compiled on its own; this caught one commit that depended on a later one, and
  the history was rebuilt so each commit builds.

## Where I changed or rejected AI output

1. **Decision style.** The first draft asked me to pick between options for every choice. I redirected it to make
   and justify decisions at an SDE-2 level (reason plus rejected alternative for each), which became the decision
   table in DESIGN.md.
2. **H2 for tests: rejected.** An early draft proposed PostgreSQL with an H2 fallback for local runs and tests. Rejected
   because H2's SQL dialect differs: the trigram search and generated column could not be tested, giving false
   confidence. All tests use real PostgreSQL.
3. **API conventions: my proposal, then my reversal.** I proposed POST-only endpoints, mandatory request bodies and an
   `X-MD5` body-hash header for security. The AI implemented the plan but pointed out that MD5 without a secret only
   detects corruption: anyone can recompute it, so it does not prove who sent the request. Since request signing only
   makes sense when tied to a client identity, and this service has no login, I moved these to future scope, where
   the design is HMAC-SHA256 with per-client secrets and replay protection.
4. **Authentication: Basic auth with roles → API key.** The AI proposed HTTP Basic with VIEWER/RECRUITER roles. I
   pointed out we are not building a login model; it switched to API keys (constant-time comparison, rotation support,
   fail-fast at startup) and moved identities and roles to future scope.
5. **Errors must be exceptions, never crashes.** I required that failures be thrown as typed exceptions and handled
   centrally. This produced the `ApplicationException` hierarchy, the single handler (also used by servlet filters),
   503 handling for database outages, and the test that renders every error code.
6. **Pagination is mandatory.** I made server-side pagination a hard requirement; the design rejects out-of-range page
   sizes instead of silently clamping them, and always adds a tie-breaker so pages are stable.

## Where the AI corrected itself (and I verified)

- Replaced Hibernate's `@CreationTimestamp/@UpdateTimestamp` with clock-driven timestamps after noticing that a
  skills-only edit might not refresh `updatedAt`, and that nanosecond timestamps in a create response would not match
  the microsecond values PostgreSQL returns later.
- Dropped a planned custom UUIDv7 generator after finding Hibernate 7's built-in `VERSION_7` style.
- Added client-side JDBC socket and connect timeouts after reasoning that a server-side statement timeout cannot fire
  if the network path to the database hangs.
- Simplified the planned `INVALID_SEARCH_CRITERIA`/`INVALID_PAGINATION` codes into `VALIDATION_FAILED` with field-level
  errors, so clients handle one shape for all input problems.

## What only showed up when running against real infrastructure

- The first run against PostgreSQL failed three integration tests. All three were test bugs, not application bugs: a
  JSON-array assertion style that Spring cannot map onto immutable lists, a test that padded an email with spaces
  (correctly rejected by validation), and a string replacement that missed a padded fixture value. A fourth failure was
  Spring Boot disabling metrics export in tests by default (fixed with `@AutoConfigureMetrics`).
- Port 8080 was already used by a local Tomcat service. Rather than stop someone else's service, the compose file now
  takes `APP_PORT`/`MANAGEMENT_PORT`.
- Manual testing showed generic 405/415 messages and a misleading 1980 build time on `/actuator/info` (a side effect of
  reproducible builds); both fixed, the first one pinned by a test. Unknown routes were logged as `/**`; they now log
  the real path.
- The first performance query for a multi-skill filter returned zero rows because of how the synthetic data assigns
  skills, so it proved nothing; the example was changed to a skill pair that exists. An AI edit to the EXPLAIN script
  produced `ESCAPE ''` instead of `ESCAPE '\'` (twice, the same shell-quoting slip); caught by reading the diff.
- The measurements changed the scaling plan: the exact `COUNT` behind every listing (~100 ms at 1M rows) and broad
  multi-skill filters (~0.45 s) are the first bottlenecks, ahead of the search engine.

## What I would not delegate

Choosing trade-offs (what to build now versus later), deciding what "secure enough" means without a user model, and
judging whether a test proves the right thing. The AI was fast at producing options and code; the judgment calls above
were mine.
