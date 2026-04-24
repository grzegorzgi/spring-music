# Spring Music Modernization — Project CLAUDE.md

## Context

Scenario 1: Code Modernization. Legacy: spring-music (Spring Boot 2.4, Java, multi-store).
Modernizing via **Strangler Fig** — extract services one at a time, keeping the monolith running.

First extraction: `services/album-service` (TypeScript/Express/SQLite).
Anti-corruption layer: `acl/` translates between legacy and new domain models.

## Roles

- Role 5 (Dev) — The Cut: extracts album-service with clean OpenAPI contract
- Role 6 (Dev/Tester) — The Fence: ACL + boundary enforcement tests + hook

## Architecture boundaries — HARD RULES

1. Code in `services/` must NEVER import from `legacy/`. Use `acl/` as the bridge.
2. Code in `legacy/` must NEVER import from `services/`. The monolith is unaware of its successors.
3. Legacy field names (`releaseYear`, `albumId`, `_class`) must NEVER appear in any new service's API response shape.
4. When choosing where to add a feature, prefer the new service over the monolith.

These rules are enforced by a `PreToolUse` hook in `.claude/settings.json`. The hook is deterministic; the preference above is a prompt. See `decisions/ADR-003-acl-design.md` for why each is which.

## Code conventions

- New services: TypeScript strict mode, no `any`, Zod for runtime validation
- Tests: Jest + Supertest; co-located in `src/tests/`
- OpenAPI: every public endpoint must be described in `openapi.yaml` at the service root
- ADRs live in `decisions/` — write one before making a significant architectural call

## Module map

| Path | Purpose |
|---|---|
| `legacy/spring-music/` | Original Spring Boot 2.4 monolith — do not refactor internals |
| `services/album-service/` | Extracted modern album CRUD service |
| `acl/` | Anti-corruption layer: translates legacy ↔ new domain models |
| `decisions/` | Architecture Decision Records |
| `.claude/` | Claude Code config (hooks, settings) |

## Running things

```bash
# Legacy monolith (Java 11+, Gradle)
cd legacy/spring-music && ./gradlew bootRun

# Album service
cd services/album-service && npm install && npm run dev

# Album service tests
cd services/album-service && npm test

# ACL tests (includes fence/boundary tests)
cd acl && npm install && npm test
```

## What NOT to do

- Do not bump Spring Boot in `legacy/spring-music` — characterization tests pin its behavior as-is
- Do not add new business logic to the legacy monolith; add it to `services/` instead
- Do not bypass the ACL by passing legacy JSON shapes directly to new service consumers
