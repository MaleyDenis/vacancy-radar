# Key Models

## RawJobOffer
`id, title, companyName, companySize, location, remote, publishedAt, experienceLevel, description, salaryMin, salaryMax, currency, salaryUnit, salaryGross, contractType, applyUrl, sourceConnector, domain`

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
`skills, requirements, responsibilities, projectDescription, realSeniority`

- Claude, once per offer, profile-independent. `responsibilities`/`projectDescription` are Claude's own-words summary.

## OfferScore (`scores.json`, keyed by offer id)
`matchScore (1-10), reason, profileHash, scoredAt`

- `profileHash` ≠ current profile hash → score is stale.

## JobView (`GET /api/jobs` element)
`offer (RawJobOffer), extraction (OfferExtraction|null), score (OfferScore|null), stale`

## Analytics
`date, totalScanned, skillFrequency, topCompanies, salaryDistribution, remotePercentage`
