# Data Storage

## File Structure

```
data/
  profile.json       — user profile
  raw-offers.json    — scraped offers (site data only, no AI), never pruned by scan
  extractions.json   — Claude-extracted facts, keyed by offer id (profile-independent)
  scores.json        — Claude match scores, keyed by offer id (profile-dependent)
```

## Rules

- Do NOT use database — JSON files only
- Files are linked by offer id; `GET /api/jobs` joins them (raw + extraction + score + `stale`)
- `raw-offers.json` holds only what the site gives — nothing from Claude
- Extraction runs once per offer; a profile change never re-extracts
- A profile change re-scores nothing by itself: old scores get `stale=true` (profile hash differs);
  `POST /api/scores/rescore?days=N` re-scores offers published in the last N days
  (default `radar.rescore.days`)
- `DELETE /api/scores` removes `scores.json` only; `DELETE /api/data` removes everything except `profile.json`
