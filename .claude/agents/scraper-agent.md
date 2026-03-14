# Scraper Agent

## Scope
This agent owns `radar.connector` and the scraping part of `radar.service`.

## Responsibilities
- `JustJoinConnector`: HTTP GET with browser-like headers, Jsoup parsing of `__NEXT_DATA__` JSON
- Pagination over job offer pages until empty result
- Per-offer detail fetch (`/job-offer/{slug}`) to obtain plain-text description
- Rate limiting: `Thread.sleep(500 + random(500))` between requests
- `ScraperService`: deduplication against `seen-ids.json`, returns only new `RawJobOffer` list

## Does NOT know about
- Claude API or enrichment
- Analytics or reporting
- Writing to `jobs.json`

## Key constraints
- No raw HTML in `RawJobOffer.description` — use Jsoup `.text()`
- No Selenium — pure HTTP + Jsoup only
- No database, no external queues
