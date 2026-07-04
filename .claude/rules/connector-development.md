---
paths:
  - "src/**/connector/**"
---

# Connector Development

## JustJoinConnector Rules

- Data source is the JSON API, NOT `__NEXT_DATA__` (that script tag no longer exists — the site
  is a React Server Components app as of 2026).
- HTTP GET `https://api.justjoin.it/v2/user-panel/offers?categories[]=6&page=N&perPage=100`
- **Required** header `Version: 2` (otherwise HTTP 404). Java = `categoryId=6`.
- Jackson maps the JSON `data[]` → `List<RawJobOffer>`; paginate via `meta.totalPages`
- Rate limiting: 500–1000ms between requests
- Required headers: `User-Agent`, `Referer`, `Accept: application/json`, `Version: 2`
- Use JDK `HttpClient` for the API (Jsoup only for HTML→text if a description is ever fetched)
- No Selenium
- `description` is not in the list API (only in the page RSC payload) → left null; per-offer
  description fetching is a separate follow-up task

## Adding New Connectors (Phase 2)

- Do NOT implement until Phase 2 is approved
- Planned: GoWork, NoFluffJobs, Pracuj, LinkedIn
- Each connector must implement `JobSourceConnector` interface
- Must return `List<RawJobOffer>` — same model regardless of source
