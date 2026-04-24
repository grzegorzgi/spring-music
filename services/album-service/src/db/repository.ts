import { v4 as uuidv4 } from "uuid";
import type { Album, AlbumInput } from "../models/album";

// In-memory store — swap for PostgreSQL in production via the same interface.
export class AlbumRepository {
  private store = new Map<string, Album>();

  findAll(): Album[] {
    return [...this.store.values()].sort((a, b) =>
      a.artist.localeCompare(b.artist) || a.title.localeCompare(b.title)
    );
  }

  findById(id: string): Album | undefined {
    return this.store.get(id);
  }

  create(input: AlbumInput): Album {
    const album: Album = { id: uuidv4(), ...input };
    this.store.set(album.id, album);
    return album;
  }

  update(id: string, input: AlbumInput): Album | undefined {
    if (!this.store.has(id)) return undefined;
    const album: Album = { id, ...input };
    this.store.set(id, album);
    return album;
  }

  delete(id: string): boolean {
    return this.store.delete(id);
  }

  close(): void {
    // no-op for in-memory store; placeholder for real DB teardown
  }
}
