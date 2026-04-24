import { createApp } from "./app";
import { AlbumRepository } from "./db/repository";

const PORT = process.env.PORT ? parseInt(process.env.PORT) : 3000;
const DB_PATH = process.env.DB_PATH ?? "./albums.db";

const repo = new AlbumRepository(DB_PATH);
const app = createApp(repo);

app.listen(PORT, () => {
  console.log(`album-service listening on http://localhost:${PORT}`);
});

process.on("SIGTERM", () => {
  repo.close();
  process.exit(0);
});
