# album-service — Directory CLAUDE.md

## Purpose

Modern replacement for the `/albums` endpoints in spring-music.
Runs on port 3000. SQLite for local dev; swap in PostgreSQL for production.

## Domain model

```typescript
interface Album {
  id: string;       // UUID v4
  title: string;
  artist: string;
  year: number;     // integer — was releaseYear: string in legacy
  genre: string;
  trackCount: number;
}
```

## What changed vs legacy

| Legacy field | New field | Reason |
|---|---|---|
| `releaseYear: string` | `year: number` | Type correctness; string was a legacy accident |
| `albumId: string` | removed | Undocumented internal artifact; stripped by ACL |
| `_class: string` | removed | MongoDB discriminator; not a domain concept |

## Endpoints

| Method | Path | Description |
|---|---|---|
| GET | /albums | List all albums |
| GET | /albums/:id | Get album by id |
| POST | /albums | Create album |
| PUT | /albums/:id | Full update |
| DELETE | /albums/:id | Delete |
| GET | /health | Liveness check |

Full contract: see `openapi.yaml` in this directory.
