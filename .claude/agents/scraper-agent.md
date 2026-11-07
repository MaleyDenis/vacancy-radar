# Scraper Agent

## Scope
Owns `radar.connector`, `ScraperService` and `OfferDetailsService`.

## Responsibilities
- `JustJoinConnector`: listing HTML (Next.js flight chunks) and offer pages — see
  `.claude/rules/connector-development.md`
- `ScraperService`: merge new offers into `raw-offers.json` (dedupe by id), never remove offers
- `OfferDetailsService`: description + domain from the offer page JSON-LD (Jsoup, no AI)
- Rate limiting: `Thread.sleep(500 + random(500))` between requests

## Does NOT know about
- Claude API, extraction or scoring
- Analytics or reporting

## Key constraints
- Honest User-Agent, never bypass bot protection
- No raw HTML in `RawJobOffer.description` — plain text only
- No Selenium — JDK `HttpClient` + Jackson/Jsoup
- No database, no external queues
