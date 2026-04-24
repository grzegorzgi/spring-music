# ADR-001: Strangler Fig as Decomposition Strategy

**Date:** 2026-04-24  
**Status:** Accepted  
**Authors:** Role 3 (Architect), Role 5 (Dev)

---

## Context

Spring-music is a Spring Boot 2.4 monolith that:
- runs Java EE conventions (`javax.persistence`, XML-era patterns)
- mixes three persistence backends (JPA/H2, MongoDB, Redis) behind a single `CrudRepository` abstraction
- has no API versioning, no error contract, and mixed PUT/POST semantics
- has one meaningful bounded context: **Albums**

The board wants it "modernized." Two risky paths were considered:
1. Big-bang rewrite (high risk: the monolith keeps running in production while a parallel rewrite races)
2. Strangler Fig (incremental: extract one seam at a time, monolith stays live, traffic routes to the new service piece by piece)

## Decision

We use the **Strangler Fig** pattern.

Each extraction follows the same playbook:
1. Characterize the monolith endpoint (pin behavior, bugs included)
2. Extract a new service with a clean API contract
3. Stand up an Anti-Corruption Layer to translate between data models
4. Route traffic to the new service; keep the monolith as a fallback
5. Once the new service carries 100% of traffic for 2 weeks, retire the monolith endpoint

## Rationale

| Criterion | Big-bang rewrite | Strangler Fig |
|---|---|---|
| Production risk | High — no live validation until cutover | Low — new service is tested under real traffic early |
| Rollback | Hard — two codebases diverge | Easy — monolith stays live until the seam is stable |
| Team velocity | Blocked until rewrite is "done" | Parallel progress on extraction and feature work |
| Business continuity | Break possible during cutover | Continuous; routing is the only risk |

## What we chose NOT to do

- **Lift-and-shift containerization:** Would preserve all the legacy problems and add operational overhead without improvement.
- **Database-first split:** We do not want to split the H2 schema into microservice-owned stores before the API contracts are stable. Data migration after API stability is safer.
- **Rewrite in same stack:** Modernizing to Spring Boot 3.x in-place is valuable but orthogonal. We do it in the monolith root if time permits; it does not replace extraction.

## Consequences

- The monolith must not be feature-developed during extraction. New features go into `services/`.
- The ACL is the only legal bridge between old and new. No direct cross-boundary imports.
- Characterization tests must be written before extraction starts so regressions are detectable.
- Each seam extraction requires an ADR (see ADR-002+) naming the seam, its boundaries, and its risk rank.
