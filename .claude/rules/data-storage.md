# Data Storage

## File Structure

```
data/
  resume.txt         — the user's resume, plain text (scoring input)
  raw-offers.json    — scraped offers (site data only, no AI), never pruned by scan
  extractions.json   — Claude-extracted facts, keyed by offer id (resume-independent)
  scores.json        — Claude match scores, keyed by offer id (resume-dependent)
```

## Rules

- Do NOT use database — JSON files only
- Files are linked by offer id; `GET /api/jobs` joins them (raw + extraction + score + `stale`)
- `raw-offers.json` holds only what the site gives — nothing from Claude
- Extraction runs once per offer; a resume change never re-extracts
- A resume change re-scores nothing by itself: old scores get `stale=true` (resume hash differs);
  the UI's "Переоценить" button re-scores one offer; `POST /api/scores/rescore?days=N` (endpoint
  only, no UI) re-scores already scored offers published in the last N days (default `radar.rescore.days`)
- Scans never score; offers are scored on demand (`POST /api/jobs/{id}/score`)
- `DELETE /api/scores` removes `scores.json` only; `DELETE /api/data` removes everything except `resume.txt`
