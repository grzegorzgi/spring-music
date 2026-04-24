# ADR-002: Extract Album Service as First Seam

**Date:** 2026-04-24  
**Status:** Accepted  
**Authors:** Role 5 (Dev)

---

## Context

Following ADR-001 (Strangler Fig), we need to pick the first seam to extract. Spring-music has one bounded context: Albums. The monolith exposes:

```
GET    /albums         → list all
PUT    /albums         → create (semantically wrong: PUT should be idempotent)
POST   /albums         → update (semantically wrong: POST should be for create)
GET    /albums/{id}    → get by id
DELETE /albums/{id}    → delete
```

This is the entire business surface. Extraction risk analysis:

| Factor | Score (1=low, 5=high) | Notes |
|---|---|---|
| Coupling to other services | 1 | Albums is the only bounded context |
| Data model tangle | 2 | Multi-store via profiles; H2 default is clean for extraction |
| Test coverage in legacy | 1 | Minimal — one `@SpringBootTest` smoke test |
| Business criticality | 5 | Albums IS the app; extraction is the whole migration |

**Extraction risk:** Medium-low. The domain is self-contained. The main risk is the multi-store configuration: the new service must pick a single store (we choose SQLite for dev, PostgreSQL for prod).

## Decision

Extract a standalone `album-service` in **TypeScript + Express + SQLite**.

Stack rationale:
- TypeScript enforces the type corrections the legacy code never made (`year: number` instead of `releaseYear: string`)
- Express is minimal and fast for a single-resource service
- SQLite works without infrastructure for local development; PostgreSQL is a drop-in swap for production
- Zod provides runtime validation at API boundaries, preventing the "year is a string" class of bug

## API contract changes from legacy

| Legacy | New | Reason |
|---|---|---|
| `PUT /albums` = create | `POST /albums` = create | Correct HTTP semantics |
| `POST /albums` = update | `PUT /albums/:id` = update | Correct HTTP semantics |
| `releaseYear: "1991"` | `year: 1991` | Type correctness |
| `albumId` field exposed | removed | Undocumented artifact; no consumers |
| `_class` field in MongoDB JSON | removed | Implementation detail; not a domain concept |
| No 404 on unknown id | Returns `404 + { error }` | Proper error contract |
| No validation errors | Returns `422 + { error, issues }` | Zod-validated, structured errors |

## Coexistence

During the transition both services run simultaneously. Routing is not implemented in this hackathon (infrastructure scope); the seam boundary and API contract are the deliverable. The ACL (`acl/`) handles data translation for any integration path.

## What we chose NOT to do

- **Keep Java/Spring Boot:** The point of the extraction is to demonstrate the modernization, not just re-package.
- **Multi-store support in new service:** We pick one store. Profile-based multi-store was a Cloud Foundry convenience, not a real requirement.
- **GraphQL:** CRUD over a single resource does not benefit from GraphQL complexity.
