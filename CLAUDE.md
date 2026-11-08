# vacancy-radar — CLAUDE.md

## Project

**Goal:** Collect job offers from JustJoin, enrich via Claude Haiku, filter by user profile, analyze skill trends.
**Trigger:** REST API + a minimal static UI (`src/main/resources/static/index.html`: plain HTML/CSS/JS,
no build, no dependencies). Phase 2 (GoWork, new connectors) — do NOT implement.
**Storage:** JSON files by date. No database.

---

## Tech Stack

- Java 25
- Spring Boot 4.0.3
- Gradle
- JustJoin JSON API (`api.justjoin.it/v2/user-panel/offers`, `Version: 2` header, Java = `categoryId=6`)
- Jsoup (HTML→text for job descriptions when fetched)
- Jackson (JSON mapping)
- Anthropic Java SDK (Claude Haiku — model `claude-haiku-5-5`, effort `low`)

---

## Package Structure

```
radar.model       — POJOs: RawJobOffer, StoredRawOffer, OfferExtraction, OfferScore, JobView, UserProfile
radar.service     — ScraperService, OfferDetailsService, ExtractionService, ScoringService, ClaudeClient
radar.connector   — JustJoinConnector
radar.repository  — read/write JSON files to disk
radar.web         — REST controllers
static/index.html — the UI (single file, talks to /api/*)
```

---

## Sub-agents (`.claude/agents/`)

| Agent | Responsibility | Does NOT know about |
|---|---|---|
| `scraper-agent.md` | JustJoinConnector, scraping, offer details | Claude API, reporting |
| `enricher-agent.md` | Claude Haiku: extraction + scoring | scraping, export |
| `reporter-agent.md` | snapshot aggregation, Analytics, trends | scraping, enrichment |

---

## Constraints

- Do NOT add Gradle dependencies without approval
- Do NOT implement Phase 2 features (GoWork, NoFluffJobs, Pracuj, LinkedIn)
- Do NOT use database — JSON files only
- Do NOT send HTML to Claude API
- Do NOT add any Claude attribution to commits or PRs — no `Co-Authored-By: Claude`,
  no "Generated with Claude Code" lines (also disabled in `.claude/settings.json` → `attribution`)

---

## Git Conventions

- Commit / PR title: `<type>(<scope>): <description>` — e.g. `feat(scan): add evaluate step`
  - types: `feat fix refactor docs style test chore`; lowercase, imperative, no trailing period, ≤ 70 chars
  - NO ticket numbers (`VR-NN`), NO PR numbers (`(#NN)`)
- Branch: `<type>/<short-slug>` — e.g. `feat/evaluate-step`. Never commit to `main`.
- Merge: `gh pr merge <pr> --rebase --delete-branch`. Repo allows **rebase only** (squash disabled —
  it appends `(#NN)`), so each branch commit lands on main as-is: one clean commit per branch.
- Enforced by `scripts/hooks/` (install: `scripts/install-hooks.sh`). Flow: `/finish-task`.

---

## Code Style

- Use `var` for every local variable where the compiler allows it (Java 25).
  See `.claude/rules/code-style.md`.
