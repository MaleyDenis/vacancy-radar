# REST API

## Endpoints

```
POST   /api/scan?fullFetch&limit        — start scan in background (202; 409 if busy)
GET    /api/status                      — running task, step, done/total, last result or error
GET    /api/jobs?minScore&scoredOnly&sort&page&size
                                        — JobPage {items, total, page, size, staleCount}
GET    /api/jobs/{id}                   — one JobView with full description (404 if unknown)
POST   /api/jobs/{id}/score             — score one offer now (sync; 404/400/409/502)
POST   /api/scores/rescore?days=N       — rescore scored offers of last N days (background)
DELETE /api/scores                      — delete scores.json (409 if busy)
DELETE /api/data                        — delete everything except profile.json (409 if busy)
GET    /api/profile, PUT /api/profile   — user profile
GET    /api/trends                      — legacy, unused
```

## GET /api/jobs

- `minScore` — hides scored offers below it; unscored offers are not affected. The UI sends
  `profile.minMatchScore` by default.
- `scoredOnly=true` — hides unscored offers.
- `sort` — `score` (best first, then newest; unscored last) | `date` (newest first) |
  `date_score` (newest day first, best score within a day). Default `score`.
- `page` (1-based, default 1), `size` (default 50, max 200).
- Items are card views: `offer.description` is null; the detail view loads `GET /api/jobs/{id}`.
- Filtering, sorting and paging run on the server (the pool may grow to thousands of offers).
