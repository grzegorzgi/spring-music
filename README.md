# Team JeanClaude — Room 3

## Participants

- Giniewicz, Grzegorz (Architect)
- Debski, Mateusz (Developer)
- Dworakowski, Jakub (Developer)
- Kisielewska, Mariola (PM / Business Analyst)
- Kucharski, Igor (Quality / Agentic)
- Marcinczyk, Adrian (Developer)
- Zaręba, Justyna (Tester)

## Scenario

Scenario 1: Code Modernization

## What We Built

We took spring-music — a Spring Boot 2.4 / Java 8 application running on Cloud Foundry that stores album data across relational, MongoDB, and Redis backends — and proved it can be evolved safely without a big-bang rewrite.

We applied the **Strangler Fig** pattern. The monolith stays running. A new TypeScript/Express `album-service` replaces the `/albums` endpoints with a clean, corrected API contract. An Anti-Corruption Layer (`acl/`) translates between the legacy data model and the new one, stripping accidental fields (`albumId`, `_class`) and fixing types (`releaseYear: string` → `year: number`). A `PreToolUse` hook in `.claude/settings.json` deterministically blocks cross-boundary imports, so Claude itself cannot accidentally write code that violates the architecture.

Before touching anything, we wrote characterization tests that pin the monolith's current behavior — bugs included — so any unintentional regression fails loudly and immediately. A seam-risk scorer (`scouts/`) uses Claude API subagents to independently evaluate each extraction candidate and ranks them by risk. An ops runbook documents the cutover procedure for a 3am on-call engineer. An eval harness (`scorecard/`) measures whether Claude consistently proposes correct seam boundaries, giving the modernization workflow a defensible number instead of a vibe.

What runs: monolith (Java/Gradle), album-service (TypeScript/Node), ACL (TypeScript), scouts (Python), scorecard eval (Python).
What's scaffolding: traffic routing from monolith to new service (nginx/gateway not wired yet).

## Challenges Attempted

| # | Challenge | Status | Notes |
|---|---|---|---|
| 1 | The Stories | done | 5 capability groups, 8 stories, stakeholder disagreements captured |
| 2 | The Patient | done | Full monolith diagnosis: 9 problems, dependency map, severity table |
| 3 | The Map | done | ADR-001: 4 named seams ranked by extraction risk, Strangler Fig strategy |
| 4 | The Pin | done | 8 characterization test files pinning current behavior, bugs included |
| 5 | The Cut | done | album-service with OpenAPI contract, Zod validation, integration tests |
| 6 | The Fence | done | ACL translator + fence tests + PreToolUse hook blocking cross-boundary imports |
| 7 | The Scorecard | done | Eval harness: golden dataset, boundary correctness metric, CI-ready |
| 8 | The Weekend | done | 3-phase cutover runbook with rollback triggers and smoke tests |
| 9 | The Scouts | done | Python coordinator + seam scorer using Claude API, fan-out risk analysis |

## Key Decisions

**Strangler Fig over big-bang rewrite** — the monolith stays live; we validate each extraction against characterization tests before switching traffic. Full rationale in [decisions/ADR-002-strangler-fig.md](decisions/ADR-002-strangler-fig.md).

**TypeScript/Express for the first extracted service** — forces type corrections the Java codebase never caught (`releaseYear` was always a string, `year` is a number). Full rationale in [decisions/ADR-003-album-service-extraction.md](decisions/ADR-003-album-service-extraction.md).

**Hook for hard boundary enforcement, prompt for soft preferences** — the `PreToolUse` hook is deterministic and cannot be talked out of blocking an import. The CLAUDE.md preference for "add features to services/, not legacy" is probabilistic guidance. These are different tools for different guarantees. Full rationale in [decisions/ADR-004-acl-design.md](decisions/ADR-004-acl-design.md).

**Characterization tests before everything** — no extraction happens until the pin suite is green. When behavior changes unintentionally, the failure names the exact field or endpoint that changed. See [decisions/ADR-001-decomposition.md](decisions/ADR-001-decomposition.md).

## How to Run It

```bash
# Requirements: Java 8+, Node 18+, Python 3.9+, Gradle

# Legacy monolith (in-memory H2 by default)
./gradlew bootRun

# Monolith characterization tests
./gradlew test

# Album service (new extracted service)
cd services/album-service
npm install
npm run dev        # starts on port 3000
npm test           # integration + fence tests

# ACL boundary tests
cd acl
npm install
npm test

# Scouts — agentic seam risk scoring
cd scouts
pip install -r requirements.txt
python coordinator.py

# Scorecard eval harness
cd scorecard
pip install -r requirements.txt
python scorecard.py
```

## If We Had More Time

1. **Wire the API Façade** — nginx or Spring Cloud Gateway routing `/albums/*` to album-service; currently the two services run independently without traffic routing.
2. **PostgreSQL for album-service** — SQLite works locally but production needs a real database; the repository interface is already abstracted for this swap.
3. **CI pipeline** — run characterization tests + fence tests + scorecard eval on every PR; the pieces exist, the workflow file does not.
4. **Frontend modernization** — AngularJS 1.2.16 is end-of-life; explicitly deferred as a separate workstream.
5. **Phase 2 from the ADR** — refactor `InfoController` (direct `new CfEnv()` instantiation) and dissolve Cloud Foundry dependency in favour of standard env-var configuration.

## How We Used Claude Code

**What worked best:** three-level `CLAUDE.md` — the project-level file taught Claude the architecture rules once, and every subsequent session respected the Strangler Fig boundary without re-explanation. The `PreToolUse` hook meant we never had to argue with Claude about whether to write across the boundary; it simply couldn't.

**Biggest time save:** characterization tests. Claude read the monolith source and generated behavior-pinning tests in one pass — tests that captured the PUT/POST semantic inversion and the implicit `count() == 0` seeding rule, neither of which was documented anywhere.

**What surprised us:** the scouts. Fan-out risk scoring with explicit context in each Task call produced a ranking that agreed with the human architect's judgment on 4 out of 5 seams — and disagreed on the one seam where the human had underestimated data-model tangle.

**Where it saved the most time:** Adrian used Claude Code to generate the full TypeScript service scaffold — models, routes, repository, OpenAPI spec, and tests — from the legacy `AlbumController` source. What would have been a half-day of boilerplate took under 30 minutes, with fence tests catching the one field that slipped through.
