# Spring Music Modernization — Project CLAUDE.md

## What this is

Hackathon project — Scenario 1: Code Modernization.
Northwind Logistics catalog service (Spring Boot 2.4, Java 8, Cloud Foundry) being modernized
via **Strangler Fig**: extract services one at a time, monolith stays running throughout.

Team: Giniewicz Grzegorz (Architect), Debski Mateusz, Dworakowski Jakub, Kisielewska Mariola,
Kucharski Igor, Marcinczyk Adrian, Zaręba Justyna.

## Module map

| Path | Purpose |
|---|---|
| `src/` | Legacy Spring Boot 2.4 monolith — the thing being modernized |
| `services/album-service/` | Extracted modern album CRUD service (TypeScript/Express/SQLite) |
| `acl/` | Anti-corruption layer: translates legacy ↔ new domain models |
| `decisions/` | Architecture Decision Records (ADR-001 through ADR-004) |
| `docs/` | Patient diagnosis, cutover runbook |
| `stories/` | User stories and acceptance criteria |
| `scouts/` | Agentic seam-risk scorer (Python + Claude API) |
| `src/test/.../characterization/` | Behavior-pinning tests against the monolith |
| `.claude/` | Claude Code config: hooks and permissions |

## Architecture boundaries — HARD RULES

1. Code in `services/` must NEVER import from `src/` (the monolith). Use `acl/` as the bridge.
2. The monolith (`src/`) must NEVER import from `services/`. It is unaware of its successors.
3. Legacy field names (`releaseYear`, `albumId`, `_class`) must NEVER appear in any new service's API response.
4. New features go into `services/`, not into the monolith.

Rules 1–2 are enforced by a `PreToolUse` hook in `.claude/settings.json` — deterministic block.
Rule 4 is a prompt preference — probabilistic guidance.
See `decisions/ADR-004-acl-design.md` for why each enforcement mechanism is what it is.

## What was done (challenge status)

| Challenge | What exists |
|---|---|
| 1 The Stories | `stories/USER_STORIES.md` — 5 capability groups, 8 stories, stakeholder disagreements |
| 2 The Patient | `docs/patient-diagnosis.md` — full monolith diagnosis, 9 problems, dependency map |
| 3 The Map | `decisions/ADR-001-decomposition.md` — 4 seams ranked by extraction risk, Strangler Fig |
| 4 The Pin | `src/test/.../characterization/` — 8 characterization test files pinning current behavior |
| 5 The Cut | `services/album-service/` — TypeScript/Express service with OpenAPI contract |
| 6 The Fence | `acl/` + `.claude/hooks/check-boundary.sh` — ACL + fence tests + PreToolUse hook |
| 7 The Scorecard | not attempted |
| 8 The Weekend | `docs/cutover-runbook.md` — 3-phase ops runbook with rollback triggers |
| 9 The Scouts | `scouts/` — Python coordinator + seam scorer using Claude API |

## Code conventions

- Monolith (`src/`): Java 8, Spring Boot 2.4 — do not refactor internals, do not upgrade dependencies
- New services: TypeScript strict mode, no `any`, Zod for runtime validation
- Tests: Jest + Supertest for services; JUnit for characterization tests
- OpenAPI: every new service endpoint described in `openapi.yaml` at the service root
- ADRs: write one before any significant architectural decision; lives in `decisions/`

## Running things

```bash
# Legacy monolith (Java 8+, Gradle)
./gradlew bootRun

# Monolith characterization tests
./gradlew test

# Album service (local dev)
cd services/album-service && npm install && npm run dev

# Album service tests
cd services/album-service && npm test

# ACL tests (includes fence/boundary invariant tests)
cd acl && npm install && npm test

# Scouts — seam risk scoring
cd scouts && pip install -r requirements.txt && python coordinator.py
```

## Three-level CLAUDE.md structure

This file is the **project level** — shared conventions for everyone.
Directory-level files add module-specific context:
- `services/CLAUDE.md` — service boundary rules and conventions
- `services/album-service/CLAUDE.md` — domain model, endpoint contract, what changed vs legacy
- `acl/CLAUDE.md` — translation contract, fence invariant, file map

## What NOT to do

- Do not modify `src/main/` — characterization tests pin its current behavior as a baseline
- Do not add new business logic to the monolith; use `services/` instead
- Do not bypass the ACL by passing legacy JSON shapes directly to new service consumers
- Do not expose `releaseYear`, `albumId`, or `_class` in any new service response
