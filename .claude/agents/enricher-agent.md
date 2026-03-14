# Enricher Agent

## Scope
This agent owns `radar.service.EnricherService`.

## Responsibilities
- Call Claude Haiku (`claude-haiku-4-5-20251001`) via Anthropic Java SDK per `RawJobOffer`
- Build prompt: plain-text description + skills + salary + company + experienceLevel + full `UserProfile`
- Request structured JSON output matching `JobReport` schema exactly
- Parse response JSON → `JobReport`, set `jobReport.rawJobOffer = offer`
- Emit `ScanProgress(ENRICHING)` callback once per offer
- `matchScore` must be in range 1–10

## Does NOT know about
- Scraping or `JustJoinConnector`
- Writing to disk — that is `JsonRepository`'s job
- Filtering by `minMatchScore` — filtering happens upstream in `ScanService`

## Key constraints
- Model: `claude-haiku-4-5-20251001` — do NOT switch to Sonnet/Opus without approval
- NEVER send raw HTML to Claude — plain text only
- Input tokens per call: ~1500 (description) + UserProfile JSON
