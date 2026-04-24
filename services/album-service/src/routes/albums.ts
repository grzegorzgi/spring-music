import { Router, Request, Response } from "express";
import { ZodError } from "zod";
import { AlbumInputSchema } from "../models/album";
import { AlbumRepository } from "../db/repository";

export function albumRouter(repo: AlbumRepository): Router {
  const router = Router();

  router.get("/", (_req: Request, res: Response) => {
    res.json(repo.findAll());
  });

  router.get("/:id", (req: Request, res: Response) => {
    const album = repo.findById(req.params.id);
    if (!album) {
      res.status(404).json({ error: "Album not found" });
      return;
    }
    res.json(album);
  });

  router.post("/", (req: Request, res: Response) => {
    const parsed = AlbumInputSchema.safeParse(req.body);
    if (!parsed.success) {
      res.status(422).json({ error: "Validation failed", issues: parsed.error.issues });
      return;
    }
    const album = repo.create(parsed.data);
    res.status(201).json(album);
  });

  router.put("/:id", (req: Request, res: Response) => {
    const parsed = AlbumInputSchema.safeParse(req.body);
    if (!parsed.success) {
      res.status(422).json({ error: "Validation failed", issues: parsed.error.issues });
      return;
    }
    const album = repo.update(req.params.id, parsed.data);
    if (!album) {
      res.status(404).json({ error: "Album not found" });
      return;
    }
    res.json(album);
  });

  router.delete("/:id", (req: Request, res: Response) => {
    const deleted = repo.delete(req.params.id);
    if (!deleted) {
      res.status(404).json({ error: "Album not found" });
      return;
    }
    res.status(204).send();
  });

  return router;
}
