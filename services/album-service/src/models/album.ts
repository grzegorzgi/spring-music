import { z } from "zod";

export const AlbumInputSchema = z.object({
  title: z.string().min(1),
  artist: z.string().min(1),
  year: z.number().int().min(1900).max(2100),
  genre: z.string().min(1),
  trackCount: z.number().int().min(0).default(0),
});

export const AlbumSchema = AlbumInputSchema.extend({
  id: z.string().uuid(),
});

export type AlbumInput = z.infer<typeof AlbumInputSchema>;
export type Album = z.infer<typeof AlbumSchema>;

export const LEGACY_FIELD_NAMES = ["releaseYear", "albumId", "_class"] as const;
