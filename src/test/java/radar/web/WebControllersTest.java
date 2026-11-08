package radar.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import radar.model.DetailsResult;
import radar.model.ExtractionResult;
import radar.model.OfferExtraction;
import radar.model.OfferScore;
import radar.model.RawJobOffer;
import radar.model.ScanResult;
import radar.model.ScoringResult;
import radar.model.StoredRawOffer;
import radar.model.UserProfile;
import radar.repository.JsonRepository;
import radar.service.BackgroundRunner;
import radar.service.ExtractionService;
import radar.service.OfferDetailsService;
import radar.service.ProfileHasher;
import radar.service.Progress;
import radar.service.ReporterService;
import radar.service.ScoringService;
import radar.service.ScraperService;

/**
 * Standalone MockMvc tests for the REST layer — no Spring context, so no dependency on the web test
 * slice (which Spring Boot 4 splits into a separate module not on the classpath).
 */
class WebControllersTest {

  private JsonRepository repository;
  private ScraperService scraperService;
  private OfferDetailsService offerDetailsService;
  private ExtractionService extractionService;
  private ScoringService scoringService;
  private ProfileHasher hasher;
  private ReporterService reporterService;
  private BackgroundRunner runner;
  private MockMvc mvc;

  private final UserProfile profile = new UserProfile(
      List.of("Java", "Spring"), 3, "B2B", true, 15000, "PLN", "B2B", List.of("Kraków"), 6);

  @BeforeEach
  void setUp() {
    repository = mock(JsonRepository.class);
    scraperService = mock(ScraperService.class);
    offerDetailsService = mock(OfferDetailsService.class);
    extractionService = mock(ExtractionService.class);
    scoringService = mock(ScoringService.class);
    hasher = mock(ProfileHasher.class);
    reporterService = mock(ReporterService.class);
    runner = new BackgroundRunner(new Progress());
    mvc = MockMvcBuilders.standaloneSetup(
        new ScanController(scraperService, offerDetailsService, extractionService,
            runner),
        new JobController(repository, hasher, scoringService, runner, 7),
        new AnalyticsController(reporterService),
        new ProfileController(repository),
        new DataController(repository, runner)).build();
  }

  private StoredRawOffer stored(String id) {
    var offer = new RawJobOffer(id, "Java Dev", "Acme", null, "Kraków", true, "2026-07-04",
        "senior", "desc", 18000, 26000, "PLN", "month", false, "b2b", "url", "justjoin", null);
    return new StoredRawOffer(offer, "2026-07-04T00:00:00Z", null);
  }

  /** Waits until the background task finishes, then returns {@code GET /api/status} assertions. */
  private ResultActions awaitStatus() throws Exception {
    var deadline = System.currentTimeMillis() + 5000;
    while (runner.isRunning() && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
    }
    return mvc.perform(get("/api/status")).andExpect(status().isOk())
        .andExpect(jsonPath("$.running").value(false));
  }

  /** Occupies the runner until the returned latch is released. */
  private CountDownLatch occupyRunner() {
    var latch = new CountDownLatch(1);
    runner.start("scan", () -> {
      try {
        latch.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return null;
    });
    return latch;
  }

  private OfferScore score(int value, String profileHash) {
    return new OfferScore(value, "why", profileHash, "2026-11-07T00:00:00Z");
  }

  @Test
  void postScanRunsAllStagesWithIncrementalDefaults() throws Exception {
    when(scraperService.scan(false, null)).thenReturn(new ScanResult(5, 10));
    when(offerDetailsService.fetchMissing(null)).thenReturn(new DetailsResult(3, 7));
    when(extractionService.extractMissing(null)).thenReturn(new ExtractionResult(3, 0));

    mvc.perform(post("/api/scan"))
        .andExpect(status().isAccepted())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.started").value(true));
    awaitStatus()
        .andExpect(jsonPath("$.task").value("scan"))
        .andExpect(jsonPath("$.error").isEmpty())
        .andExpect(jsonPath("$.result.added").value(5))
        .andExpect(jsonPath("$.result.total").value(10))
        .andExpect(jsonPath("$.result.detailsFetched").value(3))
        .andExpect(jsonPath("$.result.detailsRemaining").value(7))
        .andExpect(jsonPath("$.result.extracted").value(3))
        .andExpect(jsonPath("$.result.scored").doesNotExist());
    verify(scraperService).scan(false, null);
    verify(offerDetailsService).fetchMissing(null);
    verify(extractionService).extractMissing(null);
    org.mockito.Mockito.verifyNoInteractions(scoringService); // scans never score
  }

  @Test
  void postScanPassesFullFetchAndLimitToAllStages() throws Exception {
    when(scraperService.scan(true, 20)).thenReturn(new ScanResult(20, 964));
    when(offerDetailsService.fetchMissing(20)).thenReturn(new DetailsResult(20, 100));
    when(extractionService.extractMissing(20)).thenReturn(new ExtractionResult(20, 80));

    mvc.perform(post("/api/scan").param("fullFetch", "true").param("limit", "20"))
        .andExpect(status().isAccepted());
    awaitStatus()
        .andExpect(jsonPath("$.result.added").value(20))
        .andExpect(jsonPath("$.result.detailsFetched").value(20))
        .andExpect(jsonPath("$.result.extracted").value(20));
    verify(scraperService).scan(true, 20);
    verify(offerDetailsService).fetchMissing(20);
    verify(extractionService).extractMissing(20);
  }

  private StoredRawOffer stored(String id, String publishedAt) {
    var offer = new RawJobOffer(id, "Java Dev", "Acme", null, "Kraków", true, publishedAt,
        "senior", "desc", 18000, 26000, "PLN", "month", false, "b2b", "url", "justjoin", null);
    return new StoredRawOffer(offer, "2026-07-04T00:00:00Z", null);
  }

  /** low(4, day 3), old(7 stale, day 1), best(9, day 2 morning), mid(7, day 2 evening), none(day 3). */
  private void givenJobs() {
    when(repository.readProfile()).thenReturn(profile);
    when(hasher.hash(profile)).thenReturn("current");
    when(repository.readRawOffers()).thenReturn(List.of(
        stored("low", "2026-11-03T10:00:00Z"), stored("old", "2026-11-01T10:00:00Z"),
        stored("best", "2026-11-02T08:00:00Z"), stored("mid", "2026-11-02T20:00:00Z"),
        stored("none", "2026-11-03T12:00:00Z")));
    var extractions = new LinkedHashMap<String, OfferExtraction>();
    extractions.put("best", new OfferExtraction(List.of("Java"), null, List.of(), List.of("r"),
        List.of("p"), "senior", null, null, null, null, null, null, null, null, null, null));
    when(repository.readExtractions()).thenReturn(extractions);
    var scores = new LinkedHashMap<String, OfferScore>();
    scores.put("low", score(4, "current"));
    scores.put("old", score(7, "previous"));
    scores.put("best", score(9, "current"));
    scores.put("mid", score(7, "current"));
    when(repository.readScores()).thenReturn(scores);
  }

  @Test
  void getJobsSortsByScoreAndShowsUnscoredLast() throws Exception {
    givenJobs();

    mvc.perform(get("/api/jobs"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.total").value(5))
        .andExpect(jsonPath("$.staleCount").value(1))
        .andExpect(jsonPath("$.items[*].offer.id").value(
            org.hamcrest.Matchers.contains("best", "mid", "old", "low", "none")))
        .andExpect(jsonPath("$.items[0].extraction.skills[0]").value("Java"))
        .andExpect(jsonPath("$.items[0].offer.description").isEmpty()) // cards: no description
        .andExpect(jsonPath("$.items[2].stale").value(true))
        .andExpect(jsonPath("$.items[4].score").isEmpty());
  }

  @Test
  void getJobsFiltersByMinScoreAndScoredOnly() throws Exception {
    givenJobs();

    mvc.perform(get("/api/jobs").param("minScore", "7"))
        .andExpect(jsonPath("$.items[*].offer.id").value(
            org.hamcrest.Matchers.contains("best", "mid", "old", "none")));
    mvc.perform(get("/api/jobs").param("minScore", "7").param("scoredOnly", "true"))
        .andExpect(jsonPath("$.total").value(3))
        .andExpect(jsonPath("$.items[*].offer.id").value(
            org.hamcrest.Matchers.contains("best", "mid", "old")));
  }

  @Test
  void getJobsSortsByDateAndByDayThenScore() throws Exception {
    givenJobs();

    mvc.perform(get("/api/jobs").param("sort", "date"))
        .andExpect(jsonPath("$.items[*].offer.id").value(
            org.hamcrest.Matchers.contains("none", "low", "mid", "best", "old")));
    mvc.perform(get("/api/jobs").param("sort", "date_score"))
        .andExpect(jsonPath("$.items[*].offer.id").value(
            org.hamcrest.Matchers.contains("low", "none", "best", "mid", "old")));
  }

  @Test
  void getJobsPagesAndRejectsBadParams() throws Exception {
    givenJobs();

    mvc.perform(get("/api/jobs").param("page", "2").param("size", "2"))
        .andExpect(jsonPath("$.total").value(5))
        .andExpect(jsonPath("$.items[*].offer.id").value(
            org.hamcrest.Matchers.contains("old", "low")));
    mvc.perform(get("/api/jobs").param("page", "9").param("size", "2"))
        .andExpect(jsonPath("$.items").isEmpty());
    mvc.perform(get("/api/jobs").param("sort", "salary")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/jobs").param("size", "500")).andExpect(status().isBadRequest());
  }

  @Test
  void getJobByIdReturnsFullOffer() throws Exception {
    givenJobs();

    mvc.perform(get("/api/jobs/best"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.offer.description").value("desc"))
        .andExpect(jsonPath("$.score.matchScore").value(9));
    mvc.perform(get("/api/jobs/nope")).andExpect(status().isNotFound());
  }

  @Test
  void postScoreOneReturnsUpdatedJob() throws Exception {
    when(repository.readRawOffers()).thenReturn(List.of(stored("a")));
    when(repository.readExtractions()).thenReturn(new LinkedHashMap<>());
    when(scoringService.scoreOne("a")).thenReturn(score(8, "current"));

    mvc.perform(post("/api/jobs/a/score"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.offer.id").value("a"))
        .andExpect(jsonPath("$.score.matchScore").value(8))
        .andExpect(jsonPath("$.stale").value(false));
  }

  @Test
  void postScoreOneMapsErrors() throws Exception {
    when(scoringService.scoreOne("missing")).thenThrow(new java.util.NoSuchElementException("x"));
    when(scoringService.scoreOne("bare")).thenThrow(new IllegalStateException("no description"));
    when(scoringService.scoreOne("down")).thenThrow(new RuntimeException("api down"));

    mvc.perform(post("/api/jobs/missing/score")).andExpect(status().isNotFound());
    mvc.perform(post("/api/jobs/bare/score")).andExpect(status().isBadRequest());
    mvc.perform(post("/api/jobs/down/score")).andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error").value("api down"));
  }

  @Test
  void postScoreOneRefusedWhileScanRuns() throws Exception {
    var latch = occupyRunner();
    try {
      mvc.perform(post("/api/jobs/a/score")).andExpect(status().isConflict());
      verify(scoringService, never()).scoreOne(any());
    } finally {
      latch.countDown();
    }
  }

  @Test
  void deleteDataReturnsRemovedCount() throws Exception {
    when(repository.deleteAllData()).thenReturn(4);

    mvc.perform(delete("/api/data"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.removed").value(4));
    verify(repository).deleteAllData();
  }

  @Test
  void rescoreRunsInBackgroundWithConfiguredDaysByDefault() throws Exception {
    when(scoringService.rescoreRecent(7)).thenReturn(new ScoringResult(12, 1));
    when(scoringService.rescoreRecent(30)).thenReturn(new ScoringResult(40, 0));

    mvc.perform(post("/api/scores/rescore")).andExpect(status().isAccepted());
    awaitStatus()
        .andExpect(jsonPath("$.task").value("rescore"))
        .andExpect(jsonPath("$.result.scored").value(12))
        .andExpect(jsonPath("$.result.remaining").value(1));
    mvc.perform(post("/api/scores/rescore").param("days", "30")).andExpect(status().isAccepted());
    awaitStatus().andExpect(jsonPath("$.result.scored").value(40));
  }

  @Test
  void failedTaskReportsError() throws Exception {
    when(scraperService.scan(false, null)).thenThrow(new IllegalStateException("JustJoin down"));

    mvc.perform(post("/api/scan")).andExpect(status().isAccepted());
    awaitStatus()
        .andExpect(jsonPath("$.error").value("JustJoin down"))
        .andExpect(jsonPath("$.result").isEmpty());
  }

  @Test
  void busyRunnerRejectsNewTasksAndDeletes() throws Exception {
    var latch = occupyRunner();
    try {
      mvc.perform(get("/api/status")).andExpect(jsonPath("$.running").value(true));
      mvc.perform(post("/api/scan")).andExpect(status().isConflict())
          .andExpect(jsonPath("$.error").exists());
      mvc.perform(post("/api/scores/rescore")).andExpect(status().isConflict());
      mvc.perform(delete("/api/scores")).andExpect(status().isConflict());
      mvc.perform(delete("/api/data")).andExpect(status().isConflict());
      verify(repository, never()).deleteScores();
      verify(repository, never()).deleteAllData();
    } finally {
      latch.countDown();
    }
    awaitStatus();
  }

  @Test
  void deleteScoresRemovesScoresFile() throws Exception {
    when(repository.deleteScores()).thenReturn(1);

    mvc.perform(delete("/api/scores"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deleted").value(1));
    verify(repository).deleteScores();
  }

  @Test
  void getJobsReturnsEmptyWhenNone() throws Exception {
    when(repository.readProfile()).thenReturn(profile);
    when(hasher.hash(profile)).thenReturn("current");
    when(repository.readRawOffers()).thenReturn(List.of());
    when(repository.readExtractions()).thenReturn(new LinkedHashMap<>());
    when(repository.readScores()).thenReturn(new LinkedHashMap<>());

    mvc.perform(get("/api/jobs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0))
        .andExpect(jsonPath("$.items").isEmpty());
  }

  @Test
  void getTrendsReturnsJson() throws Exception {
    when(reporterService.computeTrends()).thenReturn(Map.of("Java", 5, "Spring", 2));

    mvc.perform(get("/api/trends"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.Java").value(5));
  }

  @Test
  void getProfileReturnsProfile() throws Exception {
    when(repository.readProfile()).thenReturn(profile);

    mvc.perform(get("/api/profile"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.minMatchScore").value(6))
        .andExpect(jsonPath("$.currentSkills[0]").value("Java"));
  }

  @Test
  void putProfilePersistsAndReturnsSaved() throws Exception {
    when(repository.readProfile()).thenReturn(profile);
    String body = """
        {"currentSkills":["Java","Spring"],"experienceYears":3,"preferredType":"B2B",
         "preferredRemote":true,"salaryMin":15000,"currency":"PLN","contractType":"B2B",
         "locations":["Kraków"],"minMatchScore":6}
        """;

    mvc.perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.minMatchScore").value(6));

    verify(repository).writeProfile(any(UserProfile.class));
  }
}
