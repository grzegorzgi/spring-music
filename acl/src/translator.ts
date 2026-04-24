/**
 * Anti-Corruption Layer: translates between the legacy spring-music Album model
 * and the new album-service Album model.
 *
 * This is a pure translation module — no I/O, no side effects.
 * See decisions/ADR-003-acl-design.md for the design rationale.
 */

// ── Legacy shape (as served by spring-music /albums) ─────────────────────────

export interface LegacyAlbum {
  id?: string;
  title: string;
  artist: string;
  releaseYear: string;   // stored as string in legacy — design flaw
  genre: string;
  trackCount?: number;
  albumId?: string;      // undocumented internal artifact
  _class?: string;       // MongoDB discriminator — must never leak out
}

// ── New shape (as served by album-service) ───────────────────────────────────

export interface Album {
  id: string;
  title: string;
  artist: string;
  year: number;          // integer — corrected type
  genre: string;
  trackCount: number;
}

// ── Legacy field names that must NEVER appear in any new service response ─────

export const LEGACY_FIELD_NAMES = ["releaseYear", "albumId", "_class"] as const;
export type LegacyFieldName = typeof LEGACY_FIELD_NAMES[number];

// ── Translation: legacy → new ─────────────────────────────────────────────────

export function fromLegacy(legacy: LegacyAlbum): Omit<Album, "id"> & { id?: string } {
  return {
    ...(legacy.id ? { id: legacy.id } : {}),
    title: legacy.title,
    artist: legacy.artist,
    year: parseReleaseYear(legacy.releaseYear),
    genre: legacy.genre,
    trackCount: legacy.trackCount ?? 0,
    // albumId intentionally dropped
    // _class intentionally dropped
  };
}

// ── Translation: new → legacy ─────────────────────────────────────────────────

export function toLegacy(album: Album): LegacyAlbum {
  return {
    id: album.id,
    title: album.title,
    artist: album.artist,
    releaseYear: String(album.year),
    genre: album.genre,
    trackCount: album.trackCount,
    albumId: album.id,   // legacy expects this populated; set to same as id
  };
}

// ── Fence guard: checks a response object for legacy field leakage ────────────

export function assertNoLegacyFields(obj: unknown): void {
  const serialized = JSON.stringify(obj);
  const leaked: LegacyFieldName[] = [];

  for (const field of LEGACY_FIELD_NAMES) {
    if (serialized.includes(`"${field}"`)) {
      leaked.push(field);
    }
  }

  if (leaked.length > 0) {
    throw new Error(
      `ACL FENCE VIOLATION: legacy field(s) [${leaked.join(", ")}] found in new service response. ` +
      "These fields must be translated by the ACL before leaving the service boundary."
    );
  }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

function parseReleaseYear(raw: string): number {
  const parsed = parseInt(raw, 10);
  if (isNaN(parsed)) {
    throw new Error(`ACL: cannot parse releaseYear "${raw}" as integer`);
  }
  return parsed;
}
