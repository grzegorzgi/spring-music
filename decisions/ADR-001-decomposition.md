# ADR-001: Decomposition Strategy for Spring Music Monolith

**Status:** Proposed  
**Date:** 2026-04-24  
**Author:** Architect  
**Context:** Northwind Logistics — Scenario 1, Challenge 3 (The Map)

---

## Context

The Northwind Logistics catalog service is a Spring Boot 2.4.0 monolith that stores music album data. It supports three database backends (relational, MongoDB, Redis) via Spring Profiles and runs on Cloud Foundry. The board approved "modernization" without defining what that means.

A full diagnosis of the current state is in [`docs/patient-diagnosis.md`](../docs/patient-diagnosis.md). The key facts that drive this decision:

- There is **one business capability**: album catalog CRUD
- There is **no test coverage** — no characterization tests exist yet
- The codebase has **dangerous unauthenticated endpoints** (`/errors/kill`, `/errors/fill-heap`)
- **All Actuator endpoints are exposed publicly** (env vars, heap dumps, full bean context)
- Business logic is hidden in an **event listener** (`AlbumRepositoryPopulator`)
- Platform detection and profile management are tangled in a **god class** (`SpringApplicationContextInitializer`)
- The frontend (AngularJS 1.2.16) is **end-of-life**

The system is small but the structural problems are real. Any extraction that happens before addressing these will carry the problems forward.

---

## Decision

We adopt the **Strangler Fig** pattern with an **API Façade** as the entry point.

We do not rewrite. We do not migrate all at once. We extract one service at a time, leaving the monolith in place and progressively shrinking it. Each extraction must pass a characterization test suite before and after — the monolith must keep working until the extracted service is fully proven.

**Modernization target:** containerised microservices with clean REST contracts, no Cloud Foundry dependency, and a single relational database (PostgreSQL) as the default backend.

---

## Named Seams

A seam is a place where the monolith can be cut without breaking what remains. Below are the four seams identified, with justification.

### Seam 1 — Operational Safety (remove, not extract)

**What it is:** `ErrorController` + Actuator wildcard exposure  
**Why it is a seam:** These are not business capabilities. They are either bugs (unauthenticated crash endpoints) or misconfiguration (exposing all actuator data). They must be addressed before anything else because they are security risks in any deployment environment.

**Action:** Delete `ErrorController`. Restrict Actuator to `health` and `info` only.  
**Extraction risk:** None — this is a deletion, not an extraction.

---

### Seam 2 — Album Catalog Service (extract first)

**What it is:** `AlbumController` + `JpaAlbumRepository` / `MongoAlbumRepository` / `RedisAlbumRepository` + `Album` entity + `RandomIdGenerator`  
**Why it is a seam:** This is the only business capability in the system. The REST API contract is already well-defined. The `CrudRepository<Album, String>` abstraction provides a clean internal boundary. There are no cross-cutting dependencies to other business logic.

**Extraction risk: LOW**
- Clean REST API boundary already exists
- `CrudRepository` interface isolates persistence from the controller
- No circular dependencies with other capabilities
- The only complication: data seeding (see Seam 3)

**Extracted service responsibilities:**
- `GET/PUT/POST/DELETE /albums` endpoints
- Album persistence (PostgreSQL as default; profiles for MongoDB/Redis if needed)
- Input validation (`@Valid`)

---

### Seam 3 — Data Initialization (extract alongside Seam 2)

**What it is:** `AlbumRepositoryPopulator` + `albums.json`  
**Why it is a seam:** This is hidden business logic — the rule "populate the catalog on first startup" — buried in an event listener. It must move with the Catalog Service extraction, not stay in the monolith.

**Extraction risk: LOW-MEDIUM**
- Small, self-contained class
- The `count() == 0` guard is an implicit business rule that must be made explicit in the new service
- Risk: if extraction happens without moving this, the new service starts empty

**Action:** Move seeding logic into the Catalog Service as an explicit initialization step (e.g., a `DataInitializer` component or a Flyway/Liquibase seed migration).

---

### Seam 4 — Platform / Infrastructure Adapter (extract last)

**What it is:** `SpringApplicationContextInitializer` + `InfoController` + `RedisConfig`  
**Why it is a seam:** CF service detection, profile mapping, and auto-configuration exclusion are all infrastructure concerns tangled in one class. `InfoController` exposes runtime environment data and CF service bindings — useful for ops, not business logic.

**Extraction risk: HIGH**
- Runs at bootstrap time, before the Spring context is fully built
- Tightly coupled to the Cloud Foundry runtime (`CfEnv`)
- `InfoController` instantiates `CfEnv` directly (not injected — untestable)
- Hardcoded service-tag-to-profile mapping makes it brittle
- Moving away from CF means this class becomes irrelevant rather than extracted

**Action:** In the short term, refactor `InfoController` to inject `CfEnv`. In the medium term, replace CF-specific detection with standard `SPRING_PROFILES_ACTIVE` environment variable and Kubernetes/container-native configuration. The class dissolves rather than being extracted.

---

## Services Ranked by Extraction Risk

| Rank | Seam | Risk | Action |
|------|------|------|--------|
| 1 | Operational Safety | None | Delete `ErrorController`, restrict Actuator |
| 2 | Album Catalog Service | Low | Extract as standalone service |
| 3 | Data Initialization | Low–Medium | Move with Catalog Service |
| 4 | Platform Adapter | High | Refactor then dissolve as CF dependency is removed |

> **Note:** Size is not the ranking criterion. The Platform Adapter (Seam 4) is the smallest piece of code but the highest extraction risk, because it is entangled with the Spring bootstrap lifecycle.

---

## Migration Approach

```
Phase 0 (now)
  └── Delete ErrorController
  └── Restrict Actuator endpoints
  └── Write characterization tests against current monolith API

Phase 1
  └── Extract Album Catalog Service
        ├── New service: Spring Boot 3.x, Java 17, PostgreSQL
        ├── Move AlbumRepositoryPopulator logic as explicit seed
        ├── API Façade routes /albums/* to new service
        └── Monolith /albums endpoints kept alive until contract tests pass

Phase 2
  └── Refactor InfoController to inject CfEnv
  └── Replace CF profile detection with env-var-based configuration
  └── Monolith becomes a thin shell — decommission when new service is stable

Phase 3
  └── Decommission monolith
  └── Frontend modernization (separate workstream, out of scope here)
```

---

## What We Chose Not to Do

**Big-bang rewrite.**  
The system works. A rewrite introduces new bugs while the old ones are still being discovered. The Strangler Fig lets us validate each extraction against characterization tests before switching over.

**Extract all database backends (MongoDB, Redis) as separate services.**  
The multi-backend pattern is a demo artifact, not a business requirement. In production, one database (PostgreSQL) is sufficient. Extracting Redis and MongoDB as separate services would add operational complexity without business value.

**Modernize the frontend as part of this effort.**  
AngularJS 1.2.16 is end-of-life and needs replacing. However, frontend modernization is a separate concern with separate skills and risks. Coupling it to backend extraction would slow both workstreams. It is tracked as a separate initiative.

**Event-driven architecture in Phase 1.**  
Introducing a message broker (Kafka, RabbitMQ) before the services are stable would add infrastructure complexity before we have proven service boundaries. Events can be introduced in Phase 2 if the domain grows.

**Serverless extraction.**  
The catalog API has stateful concerns (database, initialization) that are poorly suited to serverless. The operational overhead of a container-based service is justified.

**Migrate to a new ORM or query layer.**  
Spring Data JPA is sufficient. Replacing it adds risk without solving any of the identified problems.

---

## Consequences

**Positive:**
- Each extraction is independently testable and reversible
- The monolith remains in production throughout, reducing deployment risk
- Phase 0 removes active security risks before any new code is written
- The Platform Adapter dissolves naturally as CF dependency is removed — no forced extraction of untestable code

**Negative:**
- The API Façade introduces a routing layer that must be maintained during the transition period
- Running monolith and new service in parallel means two deployments to manage temporarily
- Characterization tests must be written before Phase 1 begins — this is a prerequisite, not optional

**Risks:**
- If characterization tests are skipped, there is no safety net for behaviour regressions
- The data seeding migration (Seam 3) is easy to forget — it is not visible from the API surface
- `SpringApplicationContextInitializer` (Seam 4) may surprise developers who haven't read this ADR, since it runs before any beans are created
