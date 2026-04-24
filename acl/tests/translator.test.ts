import { fromLegacy, toLegacy, LegacyAlbum, Album } from "../src/translator";

const legacyNevermind: LegacyAlbum = {
  id: "abc-123",
  title: "Nevermind",
  artist: "Nirvana",
  releaseYear: "1991",
  genre: "Rock",
  trackCount: 13,
  albumId: "abc-123",
  _class: "org.cloudfoundry.samples.music.domain.Album",
};

const newNevermind: Album = {
  id: "abc-123",
  title: "Nevermind",
  artist: "Nirvana",
  year: 1991,
  genre: "Rock",
  trackCount: 13,
};

describe("fromLegacy", () => {
  it("maps releaseYear string to year integer", () => {
    const result = fromLegacy(legacyNevermind);
    expect(result.year).toBe(1991);
    expect(typeof result.year).toBe("number");
  });

  it("strips albumId from output", () => {
    const result = fromLegacy(legacyNevermind);
    expect(result).not.toHaveProperty("albumId");
  });

  it("strips _class from output", () => {
    const result = fromLegacy(legacyNevermind);
    expect(result).not.toHaveProperty("_class");
  });

  it("preserves id when present", () => {
    const result = fromLegacy(legacyNevermind);
    expect(result.id).toBe("abc-123");
  });

  it("defaults trackCount to 0 when absent", () => {
    const { trackCount: _, ...noTrackCount } = legacyNevermind;
    const result = fromLegacy(noTrackCount);
    expect(result.trackCount).toBe(0);
  });

  it("throws for unparseable releaseYear", () => {
    expect(() =>
      fromLegacy({ ...legacyNevermind, releaseYear: "unknown" })
    ).toThrow(/cannot parse releaseYear/);
  });

  it("handles missing id gracefully", () => {
    const { id: _, ...noId } = legacyNevermind;
    const result = fromLegacy(noId);
    expect(result).not.toHaveProperty("id");
  });
});

describe("toLegacy", () => {
  it("maps year integer to releaseYear string", () => {
    const result = toLegacy(newNevermind);
    expect(result.releaseYear).toBe("1991");
    expect(typeof result.releaseYear).toBe("string");
  });

  it("sets albumId equal to id", () => {
    const result = toLegacy(newNevermind);
    expect(result.albumId).toBe(newNevermind.id);
  });

  it("preserves all required fields", () => {
    const result = toLegacy(newNevermind);
    expect(result.title).toBe("Nevermind");
    expect(result.artist).toBe("Nirvana");
    expect(result.genre).toBe("Rock");
    expect(result.trackCount).toBe(13);
  });
});

describe("round-trip: fromLegacy → toLegacy", () => {
  it("round-trips without data loss (excluding stripped fields)", () => {
    const intermediate = fromLegacy(legacyNevermind) as Album;
    const backToLegacy = toLegacy(intermediate);
    expect(backToLegacy.title).toBe(legacyNevermind.title);
    expect(backToLegacy.artist).toBe(legacyNevermind.artist);
    expect(backToLegacy.releaseYear).toBe(legacyNevermind.releaseYear);
    expect(backToLegacy.genre).toBe(legacyNevermind.genre);
    expect(backToLegacy.trackCount).toBe(legacyNevermind.trackCount);
  });
});
