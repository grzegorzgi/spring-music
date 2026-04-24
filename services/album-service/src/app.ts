import express from "express";
import { AlbumRepository } from "./db/repository";
import { albumRouter } from "./routes/albums";

export function createApp(repo: AlbumRepository) {
  const app = express();

  app.use(express.json());

  app.get("/health", (_req, res) => {
    res.json({ status: "ok" });
  });

  app.use("/albums", albumRouter(repo));

  return app;
}
