import request from "supertest";
import { createApp } from "../app";
import { AlbumRepository } from "../db/repository";
import { LEGACY_FIELD_NAMES } from "../models/album";

let repo: AlbumRepository;
let app: ReturnType<typeof createApp>;

beforeEach(() => {
  repo = new AlbumRepository();
  app = createApp(repo);
});

afterEach(() => {
  repo.close();
});

describe("GET /health", () => {
  it("returns ok", async () => {
    const res = await request(app).get("/health");
    expect(res.status).toBe(200);
    expect(res.body.status).toBe("ok");
  });
});

describe("GET /albums", () => {
  it("returns empty array when no albums", async () => {
    const res = await request(app).get("/albums");
    expect(res.status).toBe(200);
    expect(res.body).toEqual([]);
  });

  it("returns seeded albums", async () => {
    repo.create({ title: "Nevermind", artist: "Nirvana", year: 1991, genre: "Rock", trackCount: 13 });
    repo.create({ title: "Thriller", artist: "Michael Jackson", year: 1982, genre: "Pop", trackCount: 9 });

    const res = await request(app).get("/albums");
    expect(res.status).toBe(200);
    expect(res.body).toHaveLength(2);
  });
});

describe("POST /albums", () => {
  it("creates an album and returns 201 with id", async () => {
    const res = await request(app).post("/albums").send({
      title: "Nevermind",
      artist: "Nirvana",
      year: 1991,
      genre: "Rock",
      trackCount: 13,
    });
    expect(res.status).toBe(201);
    expect(res.body.id).toBeDefined();
    expect(res.body.title).toBe("Nevermind");
    expect(res.body.year).toBe(1991);
  });

  it("defaults trackCount to 0 when omitted", async () => {
    const res = await request(app).post("/albums").send({
      title: "Pet Sounds",
      artist: "The Beach Boys",
      year: 1966,
      genre: "Rock",
    });
    expect(res.status).toBe(201);
    expect(res.body.trackCount).toBe(0);
  });

  it("returns 422 for missing required fields", async () => {
    const res = await request(app).post("/albums").send({ title: "Incomplete" });
    expect(res.status).toBe(422);
    expect(res.body.error).toBe("Validation failed");
  });

  it("returns 422 when year is a string (legacy format rejected)", async () => {
    const res = await request(app).post("/albums").send({
      title: "Nevermind",
      artist: "Nirvana",
      year: "1991",
      genre: "Rock",
    });
    expect(res.status).toBe(422);
  });
});

describe("GET /albums/:id", () => {
  it("returns album by id", async () => {
    const created = repo.create({ title: "Nevermind", artist: "Nirvana", year: 1991, genre: "Rock", trackCount: 13 });
    const res = await request(app).get(`/albums/${created.id}`);
    expect(res.status).toBe(200);
    expect(res.body.id).toBe(created.id);
  });

  it("returns 404 for unknown id", async () => {
    const res = await request(app).get("/albums/00000000-0000-0000-0000-000000000000");
    expect(res.status).toBe(404);
    expect(res.body.error).toBe("Album not found");
  });
});

describe("PUT /albums/:id", () => {
  it("updates an album", async () => {
    const created = repo.create({ title: "Old Title", artist: "Artist", year: 2000, genre: "Pop", trackCount: 10 });
    const res = await request(app).put(`/albums/${created.id}`).send({
      title: "New Title",
      artist: "Artist",
      year: 2001,
      genre: "Rock",
      trackCount: 11,
    });
    expect(res.status).toBe(200);
    expect(res.body.title).toBe("New Title");
    expect(res.body.year).toBe(2001);
  });

  it("returns 404 for unknown id", async () => {
    const res = await request(app).put("/albums/00000000-0000-0000-0000-000000000000").send({
      title: "X", artist: "Y", year: 2000, genre: "Rock", trackCount: 0,
    });
    expect(res.status).toBe(404);
  });
});

describe("DELETE /albums/:id", () => {
  it("deletes an album and returns 204", async () => {
    const created = repo.create({ title: "Nevermind", artist: "Nirvana", year: 1991, genre: "Rock", trackCount: 13 });
    const res = await request(app).delete(`/albums/${created.id}`);
    expect(res.status).toBe(204);
    expect(repo.findById(created.id)).toBeUndefined();
  });

  it("returns 404 when album does not exist", async () => {
    const res = await request(app).delete("/albums/00000000-0000-0000-0000-000000000000");
    expect(res.status).toBe(404);
  });
});

// ── Fence: legacy field names must never appear in API responses ──────────────
describe("Fence: no legacy field names in responses", () => {
  it("GET /albums responses contain no legacy field names", async () => {
    repo.create({ title: "Nevermind", artist: "Nirvana", year: 1991, genre: "Rock", trackCount: 13 });
    const res = await request(app).get("/albums");
    const bodyStr = JSON.stringify(res.body);
    for (const field of LEGACY_FIELD_NAMES) {
      expect(bodyStr).not.toContain(`"${field}"`);
    }
  });

  it("POST /albums response contains no legacy field names", async () => {
    const res = await request(app).post("/albums").send({
      title: "Nevermind", artist: "Nirvana", year: 1991, genre: "Rock", trackCount: 13,
    });
    const bodyStr = JSON.stringify(res.body);
    for (const field of LEGACY_FIELD_NAMES) {
      expect(bodyStr).not.toContain(`"${field}"`);
    }
  });

  it("GET /albums/:id response contains no legacy field names", async () => {
    const created = repo.create({ title: "Nevermind", artist: "Nirvana", year: 1991, genre: "Rock", trackCount: 13 });
    const res = await request(app).get(`/albums/${created.id}`);
    const bodyStr = JSON.stringify(res.body);
    for (const field of LEGACY_FIELD_NAMES) {
      expect(bodyStr).not.toContain(`"${field}"`);
    }
  });
});
