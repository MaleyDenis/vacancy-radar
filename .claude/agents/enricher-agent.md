# Enricher Agent

## Scope
Owns the Claude steps: `ClaudeClient`, `ExtractionService`, `ScoringService`, `OfferPrompt`.

## Responsibilities
- `ExtractionService`: once per offer with a description → `ExtractedFacts` (Claude output) →
  `OfferExtraction` in `extractions.json` (fields: `docs/fields.html`)
- `ScoringService`: raw offer text + full `UserProfile` → `OfferScore` (matchScore 1–10, reason,
  profileHash) in `scores.json`; scans never score — `scoreOne(id)` scores on demand
  (card buttons "Оценить" / "Переоценить"); `rescoreRecent(days)` (endpoint only) re-scores already scored offers published
  in the last N days
- Structured output via the Anthropic Java SDK (`ClaudeClient.ask(prompt, type)`)
- A failed offer is logged and left for a later run

## Does NOT know about
- Scraping or `JustJoinConnector`
- Filtering by `minMatchScore` — done in `GET /api/jobs`

## Key constraints
- Model: `claude-haiku-5-5`, effort `low` (`ClaudeClient`) — do NOT switch to Sonnet/Opus without approval
- NEVER send raw HTML to Claude — plain text only
