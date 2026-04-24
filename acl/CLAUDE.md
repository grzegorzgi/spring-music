# acl — Directory CLAUDE.md

## Purpose

Anti-corruption layer between the spring-music legacy monolith and the new album-service.
Translates domain objects in both directions without leaking either model's internals.

## Translation contract

| Direction | Input shape | Output shape | Key transforms |
|---|---|---|---|
| legacy → new | `LegacyAlbum` | `Album` | `releaseYear: string` → `year: number`; strip `albumId`; strip `_class` |
| new → legacy | `Album` | `LegacyAlbum` | `year: number` → `releaseYear: string`; set `albumId = id` |

## Fence invariant

**No legacy field name may appear in any new Album API response.**

The field names that must never leak: `releaseYear`, `albumId`, `_class`.
`fence.test.ts` asserts this on every simulated response from album-service.
If a field name slips through, the fence test fails with a specific field-name callout.

## Files

| File | Purpose |
|---|---|
| `src/translator.ts` | Pure translation functions, no I/O |
| `tests/translator.test.ts` | Unit tests for both directions |
| `tests/fence.test.ts` | Boundary invariant: no legacy fields in new API shape |
