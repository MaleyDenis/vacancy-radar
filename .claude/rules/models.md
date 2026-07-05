# Key Models

## RawJobOffer
`id, title, companyName, companySize, location, remote, publishedAt, experienceLevel, description, skills, salaryMin, salaryMax, currency, contractType, applyUrl, sourceConnector, domain`

- `description` and `domain` come from the offer's own page (details step), not the listing API — `null` until fetched. `RawJobOffer.withDetails(description, domain)` returns an enriched copy.

## StoredRawOffer (element of `raw-offers.json`)
`offer (RawJobOffer), firstSeenAt, detailsFetchedAt`

- Profile-independent scraped pool. `detailsFetchedAt` is `null` until the details step fetches the page; the step processes only null entries.

## OfferDetails
`description, domain` — the "as written" fields lifted from an offer page's JobPosting JSON-LD (no AI). Produced by `OfferDetailsParser`.

## UserProfile (`data/profile.json`)
`currentSkills, experienceYears, preferredType, preferredRemote, salaryMin, currency, contractType, locations, minMatchScore`

## JobReport
`rawJobOffer, keySkills, niceToHave, projectType, realSeniority, redFlags, greenFlags, interviewFocus, matchScore (1-10), salary`

## Analytics
`date, totalScanned, skillFrequency, topCompanies, salaryDistribution, projectTypeBreakdown, remotePercentage`
