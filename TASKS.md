# vacancy-radar — TASKS.md

## Task Graph

```
VR-001 (scaffold)
    └── VR-002 (models)
            ├── VR-003 (repository)
            │       ├── VR-005 (ScraperService)
            │       │       └── VR-008 (scan orchestration)
            │       │               └── VR-009 (REST controllers + SSE)
            │       ├── VR-006 (EnricherService)
            │       │       └── VR-008
            │       └── VR-007 (ReporterService)
            │               └── VR-008
            └── VR-004 (JustJoinConnector)
                    └── VR-005
```

**Legend:** each task depends on the one above it unless marked `parallel`.

---

# VR-001: Project Scaffold

## Context
- New project, nothing exists yet
- Must produce a working Gradle + Spring Boot skeleton

## Action
1. Initialize Gradle wrapper (`gradle wrapper --gradle-version 8.x`)
2. Create `build.gradle` with dependencies:
   - `org.springframework.boot:spring-boot-starter-web`
   - `org.jsoup:jsoup`
   - `com.fasterxml.jackson.core:jackson-databind`
   - `com.anthropic:anthropic-java` (Anthropic Java SDK)
   - `com.diffplug.spotless` plugin (Google Java Format)
3. Create `src/main/java/radar/VacancyRadarApplication.java` with `@SpringBootApplication`
4. Create `src/main/resources/application.properties`:
   - `server.port=8080`
   - `anthropic.api.key=${ANTHROPIC_API_KEY}`
5. Create `data/` directory with `.gitkeep`
6. Create `data/profile.json` with default `UserProfile` values
7. Create `.claude/settings.json` with hooks (spotlessApply, test, bash guard)
8. Create `.claude/agents/scraper-agent.md`, `enricher-agent.md`, `reporter-agent.md`

## Acceptance Criteria
- [ ] `./gradlew build` succeeds
- [ ] Application starts: `./gradlew bootRun`
- [ ] `./gradlew spotlessApply` runs without error

## Boundaries
- Do NOT add dependencies outside the list above without approval
- Do NOT create UI files

## Dependencies
- None — this is the first task

---

# VR-002: Domain Models

## Context
- Package: `radar.model`
- No existing files
- Pure POJOs with Jackson annotations, no Spring beans

## Action
1. Create `RawJobOffer.java`:
   - Fields: `id, title, companyName, companySize, location, remote, publishedAt, experienceLevel, description, skills (List<String>), salaryMin, salaryMax, currency, contractType, applyUrl, sourceConnector`
   - Use `@JsonIgnoreProperties(ignoreUnknown = true)` for safe deserialization
2. Create `UserProfile.java`:
   - Fields: `currentSkills (List<String>), experienceYears, preferredType, preferredRemote, salaryMin, currency, contractType, locations (List<String>), minMatchScore`
3. Create `SalaryRange.java`:
   - Fields: `min, max, currency`
4. Create `JobReport.java`:
   - Fields: `rawJobOffer, keySkills (List<String>), niceToHave (List<String>), projectType, realSeniority, redFlags (List<String>), greenFlags (List<String>), interviewFocus, matchScore (int), salary (SalaryRange)`
5. Create `Analytics.java`:
   - Fields: `date (String), totalScanned, skillFrequency (Map<String,Integer>), topCompanies (List<String>), salaryDistribution (SalaryRange with median), projectTypeBreakdown (Map<String,Double>), remotePercentage`
6. Create `ScanProgress.java` (SSE payload):
   - Fields: `type (enum: SCANNING/ENRICHING/SAVING/DONE/ERROR), message, current (Integer), total (Integer), saved (Integer)`

## Acceptance Criteria
- [ ] All model classes compile
- [ ] Jackson can serialize/deserialize each model (unit test with sample JSON)

## Boundaries
- Do NOT add business logic to models
- Do NOT add Spring annotations (@Service, @Component) to models

## Dependencies
- VR-001: must be completed first

---

# VR-003: File Repository

## Context
- Package: `radar.repository`
- Storage: `data/` directory, JSON files
- Handles: `seen-ids.json`, `profile.json`, `{date}/jobs.json`, `{date}/analytics.json`

## Action
1. Create `JsonRepository.java` — Spring `@Component` with `ObjectMapper` injection:
   - `UserProfile readProfile()` — reads `data/profile.json`
   - `void writeProfile(UserProfile)` — writes `data/profile.json`
   - `List<String> readSeenIds()` — reads `data/seen-ids.json`
   - `void appendSeenIds(List<String> newIds)` — merges and writes `data/seen-ids.json`
   - `void saveJobs(String date, List<JobReport> jobs)` — creates `data/{date}/jobs.json`
   - `List<JobReport> loadJobs(String date)` — reads `data/{date}/jobs.json`
   - `void saveAnalytics(String date, Analytics analytics)` — creates `data/{date}/analytics.json`
   - `Analytics loadAnalytics(String date)` — reads `data/{date}/analytics.json`
   - `List<String> listDates()` — lists subdirectories of `data/` (date folders only)
2. Create directory `data/{date}` on write if it doesn't exist
3. Thread-safe writes (synchronized or `ReentrantReadWriteLock`)

## Acceptance Criteria
- [ ] Unit tests: write then read round-trip for each method
- [ ] `data/` directory is created automatically if missing
- [ ] `seen-ids.json` deduplication: no duplicates after multiple `appendSeenIds` calls

## Boundaries
- Do NOT use any database
- Do NOT read/write outside the `data/` directory

## Dependencies
- VR-002: models must exist

---

# VR-004: JustJoinConnector

## Context
- Package: `radar.connector`
- Scrapes `https://justjoin.it/job-offers/all-locations/java`
- Parses `__NEXT_DATA__` JSON embedded in HTML
- No Selenium — pure HTTP + Jsoup

## Action
1. Create `JustJoinConnector.java` — Spring `@Component`:
   - `List<RawJobOffer> fetchAll()` — main entry point
   - Private `String fetchPage(String url)` — HTTP GET with headers:
     - `User-Agent: Mozilla/5.0 ...`
     - `Referer: https://justjoin.it/`
   - Private `List<RawJobOffer> parseNextData(String html)`:
     - Jsoup: `doc.select("script#__NEXT_DATA__").first().data()`
     - Jackson: navigate to `props.pageProps.offers` → `List<RawJobOffer>`
   - Pagination loop: increment `page` param until empty result
   - Per offer: GET `/job-offer/{slug}` to fetch `description` (plain text, strip HTML)
   - Rate limit: `Thread.sleep(500 + random(500))` between requests
2. Strip HTML from description before storing: use Jsoup `.text()`

## Acceptance Criteria
- [ ] Unit test with mocked HTML (sample `__NEXT_DATA__` fixture) parses correctly
- [ ] `fetchAll()` returns non-empty list in integration smoke test (requires network)
- [ ] No raw HTML in `RawJobOffer.description`

## Boundaries
- Do NOT call Claude API
- Do NOT write to disk (repository is separate)
- Do NOT modify `RawJobOffer` fields beyond what exists

## Dependencies
- VR-002: `RawJobOffer` model must exist
- VR-001: Spring context and Jsoup dependency must exist

---

# VR-005: ScraperService

## Context
- Package: `radar.service`
- Orchestrates: JustJoinConnector → deduplication → hand-off to enrichment

## Action
1. Create `ScraperService.java` — Spring `@Service`:
   - `List<RawJobOffer> scrapeNew()`:
     1. Call `JustJoinConnector.fetchAll()` → all offers
     2. Call `JsonRepository.readSeenIds()` → existing IDs
     3. Filter out offers whose `id` is in seen IDs
     4. Return only new offers (not persisting seen IDs yet — done after enrichment)
   - `void markAsSeen(List<String> ids)`:
     1. Call `JsonRepository.appendSeenIds(ids)`

## Acceptance Criteria
- [ ] Unit test: given 10 offers, 3 already seen → returns 7
- [ ] `markAsSeen` correctly delegates to repository

## Boundaries
- Do NOT call Claude API
- Do NOT aggregate analytics

## Dependencies
- VR-003: `JsonRepository` must exist
- VR-004: `JustJoinConnector` must exist

---

# VR-006: EnricherService

## Context
- Package: `radar.service`
- Calls Claude Haiku via Anthropic Java SDK
- Input: `RawJobOffer` + `UserProfile` → Output: `JobReport`

## Action
1. Create `EnricherService.java` — Spring `@Service`:
   - Inject `AnthropicClient` (configured from `anthropic.api.key`)
   - `JobReport enrich(RawJobOffer offer, UserProfile profile)`:
     1. Build prompt text (plain text only — description + profile JSON)
     2. Call Claude Haiku with structured output schema matching `JobReport` fields
     3. Parse response JSON → `JobReport`
     4. Set `jobReport.rawJobOffer = offer`
   - `List<JobReport> enrichAll(List<RawJobOffer> offers, UserProfile profile, Consumer<ScanProgress> progressCallback)`:
     1. For each offer: call `enrich()`, emit `ENRICHING` progress event
     2. Return all enriched reports (including low-score ones — filtering happens upstream)
2. Prompt must include: description, skills, salary, company, experienceLevel, and full UserProfile
3. Prompt must request JSON output matching `JobReport` schema exactly

## Acceptance Criteria
- [ ] Unit test with mocked Anthropic client: valid JSON in → `JobReport` out
- [ ] `matchScore` is in range 1–10
- [ ] Progress callback called once per offer

## Boundaries
- Do NOT filter by `minMatchScore` here
- Do NOT send HTML to Claude — plain text only
- Do NOT write to disk

## Dependencies
- VR-002: models must exist
- VR-001: Anthropic Java SDK dependency must exist

---

# VR-007: ReporterService

## Context
- Package: `radar.service`
- Aggregates analytics across ALL scanned offers (before score filter)
- Also computes cross-snapshot trends

## Action
1. Create `ReporterService.java` — Spring `@Service`:
   - `Analytics buildAnalytics(String date, List<JobReport> allReports)`:
     1. `totalScanned` = allReports size
     2. `skillFrequency` = flatten all `keySkills`, count occurrences, sort desc
     3. `topCompanies` = top 10 by offer count
     4. `salaryDistribution` = min/max/median of `salary.min` across all offers
     5. `projectTypeBreakdown` = percentage per `projectType`
     6. `remotePercentage` = % of offers where `rawJobOffer.remote == true`
     7. Set `date`
   - `Map<String, Integer> computeTrends()`:
     1. Load analytics from all date folders via `JsonRepository.listDates()`
     2. Merge `skillFrequency` across snapshots (sum per skill)
     3. Return sorted by total frequency desc

## Acceptance Criteria
- [ ] Unit test: 5 reports in → correct `skillFrequency` map
- [ ] `remotePercentage` is between 0 and 100
- [ ] `computeTrends()` returns non-empty map when at least one snapshot exists

## Boundaries
- Do NOT filter by `minMatchScore`
- Do NOT call Claude API
- Do NOT scrape

## Dependencies
- VR-003: `JsonRepository` must exist
- VR-002: models must exist

---

# VR-008: Scan Orchestration

## Context
- Package: `radar.service`
- Ties together: ScraperService → EnricherService → ReporterService → JsonRepository
- Async execution, SSE progress emitted directly via SseEmitter

## Action
1. Create `ScanService.java` — Spring `@Service`:
   - `void startScan(SseEmitter emitter)`:
     1. Launch async task (`@Async` or `CompletableFuture`)
     2. Async task body:
        1. Emit `SCANNING` progress
        2. `ScraperService.scrapeNew()` → `newOffers`
        3. Emit `SCANNING` progress with total count
        4. `EnricherService.enrichAll(newOffers, profile, progressCallback)` → `allReports`
        5. `ReporterService.buildAnalytics(date, allReports)` → `analytics`
        6. Filter: `filtered = allReports.filter(r -> r.matchScore >= profile.minMatchScore)`
        7. Emit `SAVING` progress
        8. `JsonRepository.saveJobs(date, filtered)`
        9. `JsonRepository.saveAnalytics(date, analytics)`
        10. `ScraperService.markAsSeen(newOffers.map(id))`
        11. Emit `DONE` with saved count
        12. `emitter.complete()`
     3. On exception: emit `ERROR`, call `emitter.completeWithError(ex)`
   - Emit via: `emitter.send(SseEmitter.event().data(progress))`

## Acceptance Criteria
- [ ] `startScan()` launches async task and returns immediately
- [ ] Progress events arrive in order: SCANNING → ENRICHING → SAVING → DONE
- [ ] `emitter.complete()` called after DONE
- [ ] `emitter.completeWithError()` called on exception
- [ ] `jobs.json` and `analytics.json` written after successful scan
- [ ] `seen-ids.json` updated after successful scan

## Boundaries
- Do NOT expose HTTP here — that is VR-009
- Do NOT skip the analytics step even if no offers pass the filter
- Do NOT use ConcurrentHashMap or polling — emitter is passed directly

## Dependencies
- VR-005: `ScraperService` must exist
- VR-006: `EnricherService` must exist
- VR-007: `ReporterService` must exist
- VR-003: `JsonRepository` must exist

---

# VR-009: REST Controllers + SSE

## Context
- Package: `radar.web`
- Spring MVC controllers
- SSE via `SseEmitter` — emitter passed directly into ScanService, no polling

## Action
1. Create `ScanController.java` — `@RestController`:
   - `POST /api/scan` → creates `SseEmitter`, calls `ScanService.startScan(emitter)`,
     returns emitter as `text/event-stream` with HTTP 202
   - No separate `GET /api/scan/{scanId}/progress` endpoint needed —
     emitter is returned directly from POST
2. Create `JobController.java` — `@RestController`:
   - `GET /api/jobs` → `JsonRepository.loadJobs(latestDate)` filtered by `minMatchScore`
   - `GET /api/jobs?date=2026-03-05` → `JsonRepository.loadJobs(date)` filtered by `minMatchScore`
   - Latest date = max of `JsonRepository.listDates()`
3. Create `AnalyticsController.java` — `@RestController`:
   - `GET /api/trends` → `ReporterService.computeTrends()` as JSON
4. Create `ProfileController.java` — `@RestController`:
   - `GET /api/profile` → `JsonRepository.readProfile()`
   - `PUT /api/profile` → `JsonRepository.writeProfile(body)`, return saved profile

## Acceptance Criteria
- [ ] `POST /api/scan` returns `text/event-stream` content type
- [ ] SSE events arrive in order: SCANNING → ENRICHING → SAVING → DONE
- [ ] Emitter completes on `DONE` or `ERROR`
- [ ] `GET /api/jobs` returns JSON array (may be empty)
- [ ] `GET /api/profile` returns valid `UserProfile` JSON
- [ ] `PUT /api/profile` persists changes (verify via subsequent GET)

## Boundaries
- Do NOT poll for progress — SseEmitter is passed directly to ScanService
- Do NOT add business logic to controllers — delegate to services
- Do NOT implement UI endpoints

## Dependencies
- VR-008: `ScanService` must exist
- VR-007: `ReporterService` must exist
- VR-003: `JsonRepository` must exist
