# ADR-001: Characterization Test Strategy for spring-music Monolith

**Status:** Accepted  
**Date:** 2026-04-24  
**Author:** The Pin (Tester role)

---

## Context

The spring-music monolith must be refactored as part of a code modernization effort.
Before the first line of production code changes, we need a safety net that will catch
unintentional behavioral regressions. This ADR records the key decisions made when
designing that safety net.

A "characterization test" (term coined by Michael Feathers in *Working Effectively with
Legacy Code*) documents **what the system actually does** rather than what it should do.
It intentionally preserves bugs. If a characterization test fails, it means behavior
changed — which may or may not be intentional. The developer must decide, not the test.

---

## Decisions

### Decision 1: Pin bugs, do not fix them

The monolith contains several deviations from convention:

| Anomaly | REST convention | Monolith behavior |
|---------|----------------|-------------------|
| `PUT /albums` | Idempotent update | Creates a new album every call |
| `POST /albums` | Create | Updates existing album |
| `GET /albums/{missing}` | HTTP 404 | HTTP 200 + null body |
| `DELETE /albums/{missing}` | HTTP 404 or 204 | HTTP 200, silent no-op |
| Album creation | HTTP 201 Created | HTTP 200 OK |

**Decision:** Tests assert the *observed* behavior, including these anomalies.

**Why:** A characterization test that asserts "should return 404" would pass only after
the bug is fixed, defeating the purpose. The goal is to detect *unexpected* changes
during refactoring, not to enforce correctness. Correctness tests belong in a separate,
later phase once a target behavior is agreed.

---

### Decision 2: Use @SpringBootTest(RANDOM_PORT) + TestRestTemplate, not MockMvc

**Alternatives considered:**
- `@WebMvcTest` with `MockMvc` — faster, no persistence layer
- `@SpringBootTest(MOCK)` with `MockMvc` — full context, no real servlet container
- `@SpringBootTest(RANDOM_PORT)` with `TestRestTemplate` — full stack, real HTTP

**Decision:** `RANDOM_PORT` + `TestRestTemplate`.

**Why:** Characterization tests must pin the **actual wire behavior** of the system —
HTTP status codes, response bodies, serialization format, content negotiation.
MockMvc bypasses the servlet container and would silently miss discrepancies that only
manifest over real TCP (e.g., HTTP 200 vs 201 differences in raw response line).
The full-stack approach also catches issues in Spring's default error handling,
Jackson serialization, and filter chains that MockMvc abstracts away.

**Trade-off accepted:** Slower test startup; shared H2 context requires manual cleanup.
For a characterization suite run infrequently before refactoring, this is acceptable.

---

### Decision 3: H2 in-memory database only — not all profiles

**Profiles available:** mysql, postgres, mongodb, redis, oracle, sqlserver, h2 (default)

**Decision:** Run characterization tests against H2 only.

**Why:**
1. H2 requires no external infrastructure — tests run anywhere (CI, laptop, container).
2. The REST API surface is identical across profiles; what changes is the underlying
   repository implementation. Domain and HTTP contract tests cover 95% of refactoring risk.
3. The postgres profile is currently broken (`ProgressDialect` does not exist in Hibernate).
   Testing it would require a separate fix first, which is out of scope for The Pin.
4. Redis and MongoDB profiles require running services that are not available in the
   standard CI environment.

**Future work:** Contract tests per profile should be added *after* the postgres dialect
bug is resolved. The Redis repository has a hand-rolled `CrudRepository` implementation
that diverges from the JPA path and warrants dedicated tests.

---

### Decision 4: Exclude /errors/kill and /errors/fill-heap from the automated suite

**The endpoints:**
- `GET /errors/kill` — calls `System.exit(1)`, terminates the JVM
- `GET /errors/fill-heap` — infinite allocation loop, causes `OutOfMemoryError`

**Decision:** Do NOT write automated tests that call these endpoints. Document their
behavior in comments within `ErrorEndpointCharacterizationTest.java` instead.

**Why:** Executing either endpoint would crash the test JVM, corrupt CI results, and
potentially affect co-located processes. The risk of accidental execution in a future
test run outweighs the benefit of automated coverage. The behavior of these endpoints
is trivially predictable from code inspection and does not require empirical pinning.

**The more important concern:** These endpoints are unauthenticated and unconditionally
destructive. Any production deployment of this monolith is a denial-of-service
vulnerability. This is documented as a finding in the characterization suite comments
and must be addressed before any cloud deployment.

---

### Decision 5: Use @DirtiesContext for AlbumInitializationCharacterizationTest

The `AlbumRepositoryPopulator` is not a Spring-managed bean — it is registered manually
via `SpringApplicationBuilder.listeners()` in `Application.main()`. `@SpringBootTest`
does not run `main()`, so the populator does not fire in the default test context.

**Decision:** `AlbumInitializationCharacterizationTest` uses `@DirtiesContext` plus a
`@TestConfiguration` that registers `AlbumRepositoryPopulator` as a Spring bean.
Spring detects the `ApplicationListener<ApplicationReadyEvent>` interface and fires
the listener on context startup.

**Why not mock it:** Characterization tests must observe real behavior. A mocked
populator would tell us nothing about the actual JSON parsing, null-safety checks,
or idempotency guard (`if count == 0`).

**Cost accepted:** `@DirtiesContext` destroys and rebuilds the application context,
adding ~5-10 seconds to the test run. Acceptable for a suite run pre-refactoring.

---

## Findings Summary (bugs pinned by this suite)

| ID | Location | Description | Severity |
|----|----------|-------------|----------|
| F-01 | `AlbumController` | PUT and POST HTTP semantics are reversed | HIGH |
| F-02 | `AlbumController.getById()` | Returns HTTP 200 + null for missing ID, not 404 | HIGH |
| F-03 | `AlbumController.deleteById()` | Silent HTTP 200 on deletion of non-existent ID | MEDIUM |
| F-04 | `AlbumController` | PUT returns HTTP 200, not 201 Created | MEDIUM |
| F-05 | `Album.releaseYear` | Declared as String — accepts any non-numeric value | MEDIUM |
| F-06 | `Album.albumId` | Field exists, is never populated, always null | LOW |
| F-07 | `Album.trackCount` | int primitive defaults to 0 — POST silently zeros out the field | MEDIUM |
| F-08 | `application.yml` | PostgreSQL profile sets `ProgressDialect` (typo, class does not exist) | BUG |
| F-09 | `ErrorController` | /errors/kill and /errors/fill-heap are unauthenticated DoS vectors | CRITICAL |
| F-10 | `InfoController` | /appinfo and /service expose runtime topology with no authentication | SECURITY |
| F-11 | `AlbumController` + `RandomIdGenerator` | Client-supplied ID in PUT body is always ignored; server generates a new UUID regardless | HIGH |
| F-12 | `AlbumController.deleteById()` | DELETE of non-existent ID returns HTTP 500 (EmptyResultDataAccessException), not 404 | HIGH |

---

## Consequences

- Any refactoring that inadvertently changes a pinned behavior will produce a test failure
  with a message explaining precisely what the monolith used to do.
- Developers fixing the semantic HTTP method reversal (F-01, F-02, F-03, F-04) MUST
  update the corresponding characterization tests to reflect the new target behavior,
  and document the decision in a separate ADR.
- F-09 must be resolved (remove or gate the ErrorController) before any cloud deployment.
