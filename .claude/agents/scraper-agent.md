# Scraper Agent

## Scope
This agent owns `radar.connector` and the scraping part of `radar.service`.

## Responsibilities
- `JustJoinConnector`: HTTP GET against the justjoin.it JSON API with browser-like headers, Jackson parsing of the JSON response
- Pagination driven by `meta.totalPages`
- Rate limiting: `Thread.sleep(500 + random(500))` between requests
- `ScraperService`: deduplication against `seen-ids.json`, returns only new `RawJobOffer` list

## Data source (as of 2026)
justjoin.it is now a React Server Components app — the old `__NEXT_DATA__` script tag no longer
exists. Offers come from the public JSON API:

- Endpoint: `https://api.justjoin.it/v2/user-panel/offers?categories[]=6&page=N&perPage=100`
- **Required** header: `Version: 2` (otherwise HTTP 404)
- Java is `categoryId=6`
- The list endpoint does **not** include the full job description (it lives only in the page's RSC
  payload, with no stable JSON endpoint), so `RawJobOffer.description` is left null. Per-offer
  description fetching is a separate follow-up task.

## Does NOT know about
- Claude API or enrichment
- Analytics or reporting
- Writing to `jobs.json`

## Key constraints
- No raw HTML in `RawJobOffer.description` — use Jsoup `.text()` if/when a description is fetched
- No Selenium — pure HTTP only (JDK `HttpClient`) + Jackson/Jsoup
- No database, no external queues
