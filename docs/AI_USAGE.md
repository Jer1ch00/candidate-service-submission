# AI-assisted development

I built this service with Claude Code, an AI coding agent that works in the terminal with access to the repository.
I used it as a tool, the way I would use a very fast pair programmer: it wrote most of the code, tests and documents
and ran the builds and measurements; I set the direction, made the design calls that shaped the system, and decided
when it was ready to submit. Nothing was accepted on the tool's word. Every claim had to be backed by a compiler, a
test, the library's actual API, or the running system.

## How the work was divided

| Area | My part | The tool's part |
|---|---|---|
| Scope and quality bar | Set the standard: an SDE-2 level service where every decision is justified and nothing is over-engineered. Decided what is built now and what becomes future scope | Mapped each requirement to a design element and a test; drafted the implementation plan |
| Design | Made the decisions listed below, including reshaping or overruling several proposals | Proposed the architecture, data model, API, error contract and scaling plan, each with alternatives |
| Code structure | Directed the production-style layout (controller, service interfaces, service implementations, repository) and a SOLID and design-pattern review with no regressions allowed | Carried out the restructure and encoded the conventions as ArchUnit rules that fail the build |
| Implementation and tests | Set the non-negotiables: failures as typed exceptions instead of crashes, mandatory server-side pagination | Wrote the code and the 158 automated tests |
| Verification | Ran the service myself from IntelliJ against the Dockerised database and worked through the setup problems it exposed (decision 7) | Ran the builds, the Docker stack, the failure tests and the 1,000,000-row performance measurements |
| Documentation | Asked for the object-oriented design, SOLID and design-pattern sections, including which patterns are deliberately not used | Drafted the README, DESIGN and this document |
| Submission | Made the release calls: what to fix before sharing, pinning the build tool version for reviewers | Ran the pre-submission checks (file permissions, secrets, fresh-build behaviour) |

## Decisions I made

1. **Decide, don't ask.** The tool's first plan asked me to choose between options for almost every decision. I
   redirected it to make each decision itself, to the standard expected of an SDE-2. It then recorded every choice with
   its reason and the alternative it rejected, which became the decision table in
   [DESIGN.md](DESIGN.md#3-key-decisions).

2. **Request signing needs an identity.** I first proposed POST-only endpoints, mandatory request bodies and an
   `X-MD5` body-hash header. The tool pointed out that an MD5 hash without a secret detects corruption but proves
   nothing about the sender, because anyone can recompute it. I concluded that signing only makes sense once requests
   are tied to a client identity, and with no login model in scope it would add complexity without adding security. I
   kept standard REST verbs and moved signing (designed as HMAC-SHA256 with per-client secrets and replay protection)
   and body-only requests to future scope. For the same reason, no user model, the tool replaced its proposal of HTTP
   Basic with roles by API keys with rotation support.

3. **Failures are typed exceptions, never crashes.** I made this a requirement for every layer. It became an
   `ApplicationException` hierarchy rendered by one handler (also used by servlet filters), a `503` with `Retry-After`
   when the database is down, and a test that renders every error code.

4. **Pagination is part of the server contract.** The first plan left pagination out of scope. I made server-side
   pagination mandatory for every list endpoint. The design then rejects out-of-range page sizes instead of silently
   clamping them and adds a tie-breaker so pages stay stable.

5. **A structure a team would recognise.** I had the code reorganised into the layout used in the production codebases
   I work with: `controller`, `service` interfaces, `service/impl` implementations and `repository`, with a SOLID and
   design-pattern review and no regressions allowed. The move surfaced two latent problems: a circular dependency
   between the error and web packages that the architecture test could not see (the compiler inlines constants), and a
   repository test that only passed because of the order tests happened to run in. Both were fixed, and the suite now
   passes in normal and reverse order.

6. **Patterns only where they solve a problem.** I asked which design patterns the code uses and whether adding
   Strategy, Observer or others would improve it. The analysis showed none would make it faster (the measured
   bottlenecks are in the queries) and that each would add indirection today. I kept the design and had DESIGN.md
   record which patterns are used, which are not, and what would make each worthwhile.

7. **No surprises for reviewers.** Running the project in IntelliJ, I hit a misleading "release version 5 not
   supported" error. It traced back to my IntelliJ version being unable to import the project with the Maven 3.10
   wrapper. I decided to pin the wrapper to Maven 3.9.9 so nobody evaluating the project hits the same problem.

## How the output was verified

The tool ran most of these checks itself.

- **Builds and tests after every change.** 158 tests: 134 unit, web-slice and architecture tests that run without
  Docker, and 24 repository and integration tests against real PostgreSQL via Testcontainers. Every commit builds on
  its own.
- **The running system, not just tests.** The full stack was exercised through Docker Compose: every endpoint and error
  path, PostgreSQL stopped mid-run to confirm the API answers `503` and recovers without a restart, and logs checked
  for personal data (none).
- **Measured performance.** `EXPLAIN ANALYZE` on the exact SQL Hibernate generates, over 1,000,000 generated rows, plus
  timed API calls. These numbers, not estimates, set the order of the scaling plan.
- **Real APIs over model memory.** Spring Boot 4, Hibernate 7 and Jackson 3 changed packages and APIs, so the tool
  inspected the actual jars (`javap`) before coding against them. Versions were taken from start.spring.io and Maven
  Central.
- **Tests designed to catch mistakes, not confirm them.** The status workflow is compared with an independently written
  table of all 36 transitions; a guard test fails if any error code lacks a scenario; concurrency tests assert exactly
  one winner instead of assuming locking works.

## Where the tool was wrong, and how it was caught

- It proposed an H2 database fallback for local runs and tests, then dropped it during planning: H2 cannot run
  PostgreSQL's trigram search or generated columns, so tests would have passed against a different database.
- It replaced Hibernate's automatic timestamps with clock-driven ones after finding that a skills-only edit might not
  update `updatedAt`, and that nanosecond timestamps would not match what PostgreSQL stores.
- The first run against PostgreSQL failed three integration tests. All three were wrong test assertions, not
  application bugs; a fourth failure was Spring Boot disabling metrics in tests by default.
- Its first multi-skill performance query returned zero rows because of how the synthetic data assigned skills, so it
  measured nothing. The example was replaced with a skill pair that exists.
- A shell-quoting slip twice turned `ESCAPE '\'` into `ESCAPE ''` in the EXPLAIN script; caught by reading the diff.
- Testing the running stack found vague 405/415 messages, a misleading 1980 build time on `/actuator/info`, and unknown
  routes logged as `/**`. All were fixed, the first pinned by a test.
- The restructure exposed a test of its own that depended on execution order (decision 5).

## What I kept for myself

What to build now and what to defer, what "secure enough" means without a user model, how the code should be organised
for the people who maintain it, and when the work was ready to submit. The tool made me much faster at producing and
checking options; those calls stayed mine.
