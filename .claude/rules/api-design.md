# REST API

## Endpoints

```
POST   /api/scan?fullFetch&limit        — scrape → details → extract in background (202; 409 if busy); no scoring
GET    /api/status                      — running task, step, done/total, last result or error
GET    /api/jobs?minScore&scoredOnly&sort&page&size
                                        — JobPage {items, total, page, size, staleCount}
GET    /api/jobs/{id}                   — one JobView with full description (404 if unknown)
POST   /api/jobs/{id}/score             — score one offer now (sync; 404/400/409/502)
POST   /api/scores/rescore?days=N       — rescore scored offers of last N days (background; no UI button)
DELETE /api/scores                      — delete scores.json (409 if busy)
DELETE /api/data                        — delete everything except resume.txt (409 if busy)
GET    /api/resume, PUT /api/resume     — resume as text/plain (PUT strips surrounding whitespace)
GET    /api/analytics?domain=all|finance|insurance|retail|it|other|unknown
                                        — Dashboard (no AI, computed from the JSON files; 400 unknown domain)
GET    /api/trends                      — legacy, unused
```

## GET /api/jobs

- `minScore` — hides scored offers below it; unscored offers are not affected. The UI sends
  6 by default.
- `scoredOnly=true` — hides unscored offers.
- `sort` — `score` (best first, then newest; unscored last) | `date` (newest first) |
  `date_score` (newest day first, best score within a day). Default `score`.
- `page` (1-based, default 1), `size` (default 50, max 200).
- Items are card views: `offer.description` is null; the detail view loads `GET /api/jobs/{id}`.
- Filtering, sorting and paging run on the server (the pool may grow to thousands of offers).

## GET /api/analytics

- Only offers with an extraction count. Skills go through `SkillNames.clean` at read time
  (versions stripped, synonyms merged, junk dropped) — editing those lists needs no re-extraction.
- Domains: `Domain.of(industry)` by keywords; insurance before finance; small groups → `other`.
- Salary: mid of min/max → PLN/month (hour × 168, day × 21, year ÷ 12; `radar.rate.eur|usd`);
  median and 25th–75th percentile, separately for `b2b` (net) and `permanent` (gross).
