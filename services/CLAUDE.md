# Services — Directory CLAUDE.md

## What lives here

Extracted microservices replacing parts of the spring-music monolith.
Each service is independently deployable, has its own `package.json`, and owns its data store.

## Current services

| Service | Port | Replaces | Status |
|---|---|---|---|
| `album-service` | 3000 | `/albums` in spring-music | active |

## Service conventions

- TypeScript strict mode; no `any` escapes
- Zod schemas at every API boundary (request body, response shape)
- OpenAPI 3.0 spec at `<service>/openapi.yaml` — must match actual behavior
- All tests in `src/tests/`; run with `npm test`
- Health endpoint at `GET /health` returning `{ status: "ok" }`

## Boundary rule (enforced by hook)

Services in this directory must NEVER import from `../../legacy/`.
If you need data from the legacy system, use `../../acl/src/translator.ts`.
