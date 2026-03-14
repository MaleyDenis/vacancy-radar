---
paths:
  - "src/**/connector/**"
---

# Connector Development

## JustJoinConnector Rules

- HTTP GET `https://justjoin.it/job-offers/all-locations/java`
- Jsoup extracts `<script id="__NEXT_DATA__">` → Jackson maps to `List<RawJobOffer>`
- Pagination: `page=2, page=3...`
- Per offer: GET `/job-offer/{slug}` → full details
- Rate limiting: 500–1000ms between requests
- Required headers: `User-Agent`, `Referer`
- No Selenium

## Adding New Connectors (Phase 2)

- Do NOT implement until Phase 2 is approved
- Planned: GoWork, NoFluffJobs, Pracuj, LinkedIn
- Each connector must implement `JobSourceConnector` interface
- Must return `List<RawJobOffer>` — same model regardless of source
