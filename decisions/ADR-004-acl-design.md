# ADR-003: Anti-Corruption Layer Design — Hook vs. Prompt

**Date:** 2026-04-24  
**Status:** Accepted  
**Authors:** Role 6 (Dev/Tester)

---

## Context

The monolith and the new service have different domain models. The legacy Album exposes:
- `releaseYear: string` (a type flaw)
- `albumId: string` (an undocumented artifact)
- `_class: string` (MongoDB discriminator — internal implementation detail)

If any of these field names appear in the new service's API responses, the legacy data model has leaked across the boundary. This is the "big ball of mud" that makes future refactoring progressively harder.

We have two enforcement mechanisms available in Claude Code:
1. A `PreToolUse` **hook** (deterministic, always runs, Claude cannot bypass it)
2. A guidance **prompt** in `CLAUDE.md` (probabilistic, influences Claude's choices)

The question is: which mechanism should enforce what?

## Decision

### Hard block: `PreToolUse` hook in `.claude/settings.json`

The hook (`check-boundary.sh`) fires on every `Edit` or `Write` tool call and rejects any file write where:
- code in `services/` imports from `legacy/`, or
- code in `legacy/` imports from `services/`

**Why a hook, not a prompt:** This is a deterministic invariant. "No cross-boundary imports" must hold on every commit, not just when Claude remembers the guideline. A prompt can be forgotten across context window compression or in a long session. The hook cannot.

### Soft preference: `CLAUDE.md` prompt

The root `CLAUDE.md` says: *"When choosing where to add a feature, prefer the new service over the monolith."*

**Why a prompt, not a hook:** Feature placement is a judgment call. There are legitimate reasons to touch the monolith (characterization tests, bug fixes that affect the running system, adding legacy adapters). Blocking this at the hook level would create friction for valid work. The preference should guide, not block.

### ACL fence test: `acl/tests/fence.test.ts`

The fence test runs in CI and asserts: no legacy field name appears in any translated response. This is the runtime enforcement of the model boundary — complementing the static hook enforcement of the import boundary.

## The three-layer enforcement model

| Layer | Mechanism | What it enforces | Why |
|---|---|---|---|
| Import boundary | `PreToolUse` hook | No `legacy/` ↔ `services/` imports | Deterministic invariant — hook is right |
| Feature placement | `CLAUDE.md` prompt | Prefer new service for new features | Judgment call — prompt is right |
| Data model boundary | `fence.test.ts` | No legacy fields in API responses | Runtime invariant — test is right |

## What we chose NOT to do

- **Block all edits to `legacy/`:** Too blunt. Characterization tests legitimately live there.
- **Put the fence invariant only in the hook:** The hook checks imports, not data shapes. A service could produce the wrong shape without importing from legacy. The fence test catches this.
- **Rely solely on code review:** The boundary invariant is too important to depend on reviewer memory.
