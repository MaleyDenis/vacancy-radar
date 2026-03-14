# Data Storage

## File Structure

```
data/
  seen-ids.json          — deduplication list of processed IDs
  profile.json           — user profile
  2026-03-05/
    jobs.json            — JobReports with score >= minMatchScore
    analytics.json       — aggregated data across all scanned offers
```

## Rules

- Do NOT use database — JSON files only
- `seen-ids.json` prevents reprocessing already enriched offers
- `analytics.json` aggregates ALL scanned offers (before score filter)
- `jobs.json` contains only offers with score >= `UserProfile.minMatchScore`
- Date directories use format `YYYY-MM-DD`
