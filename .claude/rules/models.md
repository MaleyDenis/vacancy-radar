# Key Models

## RawJobOffer
`id, title, companyName, companySize, location, remote, publishedAt, experienceLevel, description, salaryMin, salaryMax, currency, contractType, applyUrl, sourceConnector, domain`

- `description` and `domain` come from the offer's own page (details step), not the listing API — `null` until fetched. `RawJobOffer.withDetails(description, domain)` returns an enriched copy.
- No `skills` field: the listing's skill list is unreliable, so skills are extracted by Claude from the description at enrichment (→ `JobReport.keySkills`).

## StoredRawOffer (element of `raw-offers.json`)
`offer (RawJobOffer), firstSeenAt, detailsFetchedAt`

- Profile-independent scraped pool. `detailsFetchedAt` is `null` until the details step fetches the page; the step processes only null entries.

## OfferDetails
`description, domain` — the "as written" fields lifted from an offer page's JobPosting JSON-LD (no AI). Produced by `OfferDetailsParser`.

## UserProfile (`data/profile.json`)
`currentSkills, experienceYears, preferredType, preferredRemote, salaryMin, currency, contractType, locations, minMatchScore`

## JobReport
`rawJobOffer, skills, responsibilities, projectDescription, requirements, realSeniority, matchScore (1-10)`

- `skills`: all technical skills Claude found in the offer text. `responsibilities`/`projectDescription`: Claude's own-words summary. `requirements`: list. Salary/domain live on `rawJobOffer`, not duplicated here.

## Analytics
`date, totalScanned, skillFrequency, topCompanies, salaryDistribution, remotePercentage`
