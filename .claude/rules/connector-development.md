---
paths:
  - "src/**/connector/**"
---

# Connector Development

## JustJoinConnector Rules

- The old JSON API `api.justjoin.it/v2/user-panel/offers` is dead (HTTP 503) — do NOT use it.
- Source: server-rendered listing `https://justjoin.it/job-offers/all-locations/java?page=N`
  (50 offers/page, newest first by `publishedAt`, ~18 pages).
- Offers are JSON objects inside Next.js flight chunks `self.__next_f.push([1,"..."])`:
  concatenate the decoded chunks, read every object starting with `{"applyUrl":` that has a `guid`,
  dedupe by `guid`.
- Experience filter: `radar.justjoin.experience-levels` (default `senior,team-leader-manager`) →
  `?experience-levels=senior,team-leader-manager&page=N`. Slugs: intern, junior, mid, senior,
  team-leader-manager, c-level. Empty = all levels.
- Last page: no `?page=N+1` link in the HTML → `OfferPage.hasNext = false`.
- Salary: take the `employmentTypes[]` entry with `currencySource == "original"` — the others are
  currency conversions (GBP/USD/EUR/CHF), often listed first. Map `unit` → `salaryUnit`,
  `gross` → `salaryGross`.
- `applyUrl` in the data is always null → build `https://justjoin.it/job-offer/{slug}`.
- Honest `User-Agent: vacancy-radar/1.0 (+repo url)` — never impersonate a browser, never bypass
  bot protection. If the site starts blocking, stop and report.
- Rate limiting: 500–1000ms between requests.
- JDK `HttpClient` + Jackson for the listing; Jsoup for the offer page's JSON-LD (`OfferDetailsParser`).
- No Selenium.
- `description` / `domain` are not in the listing → filled by the details step from the offer page's
  `JobPosting` JSON-LD (`domain` = host of `hiringOrganization.sameAs`).

## Adding New Connectors (Phase 2)

- Do NOT implement until Phase 2 is approved
- Planned: GoWork, NoFluffJobs, Pracuj, LinkedIn
- Each connector must implement `JobSourceConnector` interface
- Must return `List<RawJobOffer>` — same model regardless of source
