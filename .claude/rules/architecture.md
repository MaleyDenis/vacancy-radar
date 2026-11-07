# Architecture

## High-Level Flow (`POST /api/scan`)

```
JustJoin listing HTML (justjoin.it/job-offers/all-locations/java?experience-levels=…&page=N)
    │  JustJoinConnector (flight-chunk JSON → RawJobOffer)
    ▼
ScraperService        → raw-offers.json   (new offers only, nothing removed)
    ▼
OfferDetailsService   → raw-offers.json   (description + domain from offer page JSON-LD, no AI)
    ▼
ExtractionService     → extractions.json  (Claude, once per offer: skills, requirements, …)
    ▼
ScoringService        → scores.json       (Claude: raw offer + profile → matchScore, reason; newest radar.score.batch only)

GET /api/jobs = raw ⨝ extraction ⨝ score by offer id, score >= minMatchScore, stale flag
```

## Component Responsibilities

| Component | Responsibility |
|---|---|
| `JustJoinConnector` | listing + offer page HTTP, flight-chunk parsing, pagination |
| `ScraperService` | merges new offers into the pool (dedupe by id) |
| `OfferDetailsService` | fills description/domain from the offer page |
| `ExtractionService` | Claude extraction, once per offer |
| `ScoringService` | Claude scoring: newest unscored (batch per scan), one offer on demand, rescore of recent scored offers |
| `ClaudeClient` | Anthropic SDK wrapper, structured output |
| `JsonRepository` | read/write JSON files to disk |

## Key Decisions

**JSON files over database**
- No persistence layer or DevOps needed; files are easy to inspect
- One file per concern, linked by offer id

**Extraction separate from scoring**
- Extracted facts don't depend on the profile → computed once
- Only scoring is profile-dependent; a profile change marks scores stale, rescore is explicit

**Claude Haiku over Sonnet**
- Runs per offer (hundreds per full scan); Haiku is fast and cheap enough
- Output is strictly typed JSON

**Background scan**
- `POST /api/scan` and `POST /api/scores/rescore` start a background task (202) and return at once;
  one task at a time (409 while busy; deletes are refused too)
- `GET /api/status` = running, task, step, done/total, last result or error; the UI polls it
- `limit` bounds heavy runs (attempts per step)
