---
paths:
  - "src/**/web/**"
---

# REST API

## Endpoints

```
POST /api/scan                    — start scan, returns scanId (async)
GET  /api/scan/{scanId}/progress  — SSE stream of progress events
GET  /api/jobs                    — latest scan, score >= minMatchScore
GET  /api/jobs?date=2026-03-05    — specific date
GET  /api/trends                  — trends across all snapshots
GET  /api/profile                 — get user profile
PUT  /api/profile                 — update user profile
```

## SSE Events

Stream on `GET /api/scan/{scanId}/progress`:

| Type | Fields |
|---|---|
| `SCANNING` | type, message, current, total |
| `ENRICHING` | type, message, current, total |
| `SAVING` | type, message |
| `DONE` | type, message, saved |
| `ERROR` | type, message |
