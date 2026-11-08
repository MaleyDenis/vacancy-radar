# Key Models

## RawJobOffer
`id, title, companyName, companySize, location, remote, publishedAt, experienceLevel, description, salaryMin, salaryMax, currency, salaryUnit, salaryGross, contractType, applyUrl, sourceConnector, domain, locations`

- `location` = main city; `locations` = every city the offer is open in (JustJoin "multilocation": one offer, several cities — stored once, not duplicated; the site's "1 019 offers" counts city variants, the pool holds ~583 unique offers). Null for older entries; `allLocations()` falls back to `[location]`. A scan refreshes `locations` of known offers it sees again.
- Salary is the original (non-converted) amount; `salaryUnit` = `hour`/`day`/`month`/`year`, `salaryGross` = gross (`true`) or net (`false`).

- `description` and `domain` come from the offer's own page (details step), not the listing API — `null` until fetched. `RawJobOffer.withDetails(description, domain)` returns an updated copy.
- No `skills` field: the listing's skill list is unreliable, so skills are extracted by Claude from the description (→ `OfferExtraction.skills`).

## StoredRawOffer (element of `raw-offers.json`)
`offer (RawJobOffer), firstSeenAt, detailsFetchedAt`

- Site data only, no AI fields. `detailsFetchedAt` is `null` until the details step fetches the page; the step processes only null entries.

## OfferDetails
`description, domain` — the "as written" fields lifted from an offer page's JobPosting JSON-LD (no AI). Produced by `OfferDetailsParser`.

## UserProfile (`data/profile.json`)
`currentSkills, experienceYears, preferredType, preferredRemote, salaryMin, currency, contractType, locations, minMatchScore`

## OfferExtraction (`extractions.json`, keyed by offer id)
`skills, niceToHaveSkills, requirements, responsibilities, projectDescription, realSeniority, englishLevel, polishRequired, workMode, benefits, recruitmentSteps, industry, companyType, leadership, aiTools, aiToolsUsage`

- Claude, once per offer, profile-independent. Any field is null when the text does not say.
- Claude answers with `ExtractedFacts` (Optional fields → nullable schema; enum-like values are strings checked in `toExtraction()`, the SDK rejects nullable enums).
- Values: `realSeniority` junior/mid/senior/lead · `englishLevel` A2–C2 · `workMode` remote/hybrid/office · `companyType` product/outsourcing/bodyleasing/agency · `aiToolsUsage` required/preferred/used.
- `responsibilities` (≤ 5) and `projectDescription` (≤ 4, capped in code) are lists of short bullets in Claude's own words, English.
- `skills` = must-have, `niceToHaveSkills` = a plus (no overlap); canonical names, no versions, no AI tools. Full list with notes: `docs/fields.html`.

## OfferScore (`scores.json`, keyed by offer id)
`matchScore (1-10), reason, profileHash, scoredAt`

- `profileHash` ≠ current profile hash → score is stale.

## JobView (`GET /api/jobs` element)
`offer (RawJobOffer), extraction (OfferExtraction|null), score (OfferScore|null), stale`

## JobPage (`GET /api/jobs`)
`items (JobView, offer without description), total, page, size, staleCount`

## Analytics
`date, totalScanned, skillFrequency, topCompanies, salaryDistribution, remotePercentage`
