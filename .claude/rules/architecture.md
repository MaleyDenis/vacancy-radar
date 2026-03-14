# Architecture

## High-Level Flow

```
JustJoin HTML
    │
    ▼
JustJoinConnector (Jsoup → __NEXT_DATA__ → List<RawJobOffer>)
    │
    ▼
ScraperService
    ├── analytics.json  ← aggregate ALL offers (before filter)
    │
    └── EnricherService (Claude Haiku per offer)
              │
              ▼
          JobReport (matchScore 1-10)
              │
              └── filter score >= UserProfile.minMatchScore
                        │
                        ▼
                    ReporterService → jobs.json
```

## Component Responsibilities

| Component | Responsibility |
|---|---|
| `JustJoinConnector` | HTTP scraping, `__NEXT_DATA__` parsing, pagination |
| `ScraperService` | orchestrates scan, deduplication, coordinates enrichment |
| `EnricherService` | sends RawJobOffer + UserProfile to Claude Haiku, returns JobReport |
| `ReporterService` | aggregates Analytics, saves snapshots |
| `JsonRepository` | read/write JSON files to disk |

## Key Decisions

**JSON files over database**
- Phase 1 needs no persistence layer, no DevOps infrastructure
- Date-based snapshots enable trend analysis natively
- Simple to deploy, simple to inspect

**Claude Haiku over Sonnet**
- Enrichment runs per-offer (up to 200+ offers per scan)
- Haiku is fast and cheap enough for structured extraction
- Output is strictly typed JSON — no need for reasoning

**Async scan via SSE**
- Scans can take minutes (rate limiting + API calls)
- SSE allows real-time progress without polling
- Single endpoint `POST /api/scan` returns scanId, client subscribes to SSE stream

**Sub-agents with isolated context**
- Each agent (scraper, enricher, reporter) knows only its own domain
- Prevents cross-concern confusion when implementing changes
