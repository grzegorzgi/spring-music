/**
 * Fence tests — The Fence (Role 6: Dev/Tester)
 *
 * These tests enforce the anti-corruption boundary invariant:
 * "No legacy field name may appear in any new service API response."
 *
 * If any of these fail, a legacy concept has leaked across the service boundary.
 * The failure message names the exact offending field so the fix is unambiguous.
 *
 * Fields that must never appear: releaseYear, albumId, _class
 */

import { assertNoLegacyFields, fromLegacy, LEGACY_FIELD_NAMES, LegacyAlbum } from "../src/translator";

const sampleLegacyAlbums: LegacyAlbum[] = [
  {
    id: "abc-1",
    title: "Nevermind",
    artist: "Nirvana",
    releaseYear: "1991",
    genre: "Rock",
    trackCount: 13,
    albumId: "abc-1",
    _class: "org.cloudfoundry.samples.music.domain.Album",
  },
  {
    id: "abc-2",
    title: "Thriller",
    artist: "Michael Jackson",
    releaseYear: "1982",
    genre: "Pop",
    trackCount: 9,
    albumId: "abc-2",
    _class: "org.cloudfoundry.samples.music.domain.Album",
  },
];

describe("assertNoLegacyFields guard function", () => {
  it("passes for a clean new Album shape", () => {
    expect(() =>
      assertNoLegacyFields({ id: "x", title: "T", artist: "A", year: 2000, genre: "Rock", trackCount: 10 })
    ).not.toThrow();
  });

  it("throws when releaseYear is present", () => {
    expect(() =>
      assertNoLegacyFields({ id: "x", title: "T", artist: "A", releaseYear: "1991", genre: "Rock" })
    ).toThrow(/releaseYear/);
  });

  it("throws when albumId is present", () => {
    expect(() =>
      assertNoLegacyFields({ id: "x", albumId: "x", title: "T", artist: "A", year: 2000, genre: "Rock" })
    ).toThrow(/albumId/);
  });

  it("throws when _class is present", () => {
    expect(() =>
      assertNoLegacyFields({ _class: "org.example.Album", id: "x", title: "T" })
    ).toThrow(/_class/);
  });

  it("names ALL offending fields in one error", () => {
    expect(() =>
      assertNoLegacyFields({ releaseYear: "1991", albumId: "x", _class: "Foo" })
    ).toThrow(/releaseYear.*albumId.*_class|albumId.*_class.*releaseYear/);
  });
});

describe("Fence: translated legacy responses contain no legacy fields", () => {
  it("single album fromLegacy has no legacy field names", () => {
    for (const legacy of sampleLegacyAlbums) {
      const translated = fromLegacy(legacy);
      expect(() => assertNoLegacyFields(translated)).not.toThrow();
    }
  });

  it("array of translated albums has no legacy field names", () => {
    const translated = sampleLegacyAlbums.map(fromLegacy);
    expect(() => assertNoLegacyFields(translated)).not.toThrow();
  });

  it("serialised JSON of translated albums contains no legacy field name strings", () => {
    const translated = sampleLegacyAlbums.map(fromLegacy);
    const json = JSON.stringify(translated);
    for (const field of LEGACY_FIELD_NAMES) {
      expect(json).not.toContain(`"${field}"`);
    }
  });
});

describe("Fence: raw legacy objects DO contain legacy fields (sanity check)", () => {
  it("raw legacy JSON contains releaseYear — confirming the field exists and the fence is meaningful", () => {
    const json = JSON.stringify(sampleLegacyAlbums);
    expect(json).toContain('"releaseYear"');
    expect(json).toContain('"albumId"');
    expect(json).toContain('"_class"');
  });
});
