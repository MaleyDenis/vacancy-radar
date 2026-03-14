# Reporter Agent

## Scope
This agent owns `radar.service.ReporterService`.

## Responsibilities
- `buildAnalytics(date, allReports)`: aggregate ALL enriched offers (before score filter):
  - `totalScanned`, `skillFrequency` (sorted desc), `topCompanies` (top 10)
  - `salaryDistribution` (min/max/median of `salary.min`), `projectTypeBreakdown`, `remotePercentage`
- `computeTrends()`: load analytics from all date folders, merge `skillFrequency`, return sorted by total
- Save/load via `JsonRepository` — `analytics.json` per date folder

## Does NOT know about
- Scraping or `JustJoinConnector`
- Claude API or enrichment
- Filtering by `minMatchScore` — analytics cover ALL offers

## Key constraints
- `remotePercentage` must be in range 0–100
- Analytics must be computed even if no offers pass the `minMatchScore` filter
- No database — JSON files in `data/{date}/analytics.json` only
