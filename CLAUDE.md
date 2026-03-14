# vacancy-radar — CLAUDE.md

## Project

**Goal:** Collect job offers from JustJoin, enrich via Claude Haiku, filter by user profile, analyze skill trends.
**Trigger:** REST API (Phase 1 only). Phase 2 (UI, GoWork, new connectors) — do NOT implement.
**Storage:** JSON files by date. No database.

---

## Tech Stack

- Java 25
- Spring Boot 4.0.3
- Gradle
- Jsoup (parsing `__NEXT_DATA__` from JustJoin HTML)
- Jackson (JSON mapping)
- Anthropic Java SDK (Claude Haiku — model `claude-haiku-4-5-20251001`)

---

## Package Structure

```
radar.model       — POJOs: RawJobOffer, JobReport, Analytics, UserProfile
radar.service     — ScraperService, EnricherService, ReporterService
radar.connector   — JustJoinConnector
radar.repository  — read/write JSON files to disk
radar.web         — REST controllers
```

---

## Sub-agents (`.claude/agents/`)

| Agent | Responsibility | Does NOT know about |
|---|---|---|
| `scraper-agent.md` | JustJoinConnector, `__NEXT_DATA__` parsing | Claude API, reporting |
| `enricher-agent.md` | Claude Haiku, structured output, JobReport | scraping, export |
| `reporter-agent.md` | snapshot aggregation, Analytics, trends | scraping, enrichment |

---

## Constraints

- Do NOT add Gradle dependencies without approval
- Do NOT implement Phase 2 features (UI, GoWork, NoFluffJobs, Pracuj, LinkedIn)
- Do NOT use database — JSON files only
- Do NOT send HTML to Claude API
