# Key Models

## RawJobOffer
`id, title, companyName, companySize, location, remote, publishedAt, experienceLevel, description, skills, salaryMin, salaryMax, currency, contractType, applyUrl, sourceConnector`

## UserProfile (`data/profile.json`)
`currentSkills, experienceYears, preferredType, preferredRemote, salaryMin, currency, contractType, locations, minMatchScore`

## JobReport
`rawJobOffer, keySkills, niceToHave, projectType, realSeniority, redFlags, greenFlags, interviewFocus, matchScore (1-10), salary`

## Analytics
`date, totalScanned, skillFrequency, topCompanies, salaryDistribution, projectTypeBreakdown, remotePercentage`
