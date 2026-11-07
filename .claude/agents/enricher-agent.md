# Enricher Agent

## Scope
Owns the Claude steps: `ClaudeClient`, `ExtractionService`, `ScoringService`, `OfferPrompt`.

## Responsibilities
- `ExtractionService`: once per offer with a description → `OfferExtraction` in `extractions.json`
- `ScoringService`: raw offer text + full `UserProfile` → `OfferScore` (matchScore 1–10, reason,
  profileHash) in `scores.json`; scan scores only unscored offers; `rescoreRecent(days)` re-scores
  offers published in the last N days
- Structured output via the Anthropic Java SDK (`ClaudeClient.ask(prompt, type)`)
- A failed offer is logged and left for a later run

## Does NOT know about
- Scraping or `JustJoinConnector`
- Filtering by `minMatchScore` — done in `GET /api/jobs`

## Key constraints
- Model: `claude-haiku-4-5-20251001` — do NOT switch to Sonnet/Opus without approval
- NEVER send raw HTML to Claude — plain text only
