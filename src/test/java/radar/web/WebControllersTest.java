package radar.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
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
import radar.service.ExtractionService;
import radar.service.OfferDetailsService;
import radar.service.ProfileHasher;
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
    mvc = MockMvcBuilders.standaloneSetup(
        new ScanController(scraperService, offerDetailsService, extractionService,
            scoringService),
        new JobController(repository, hasher, scoringService, 7),
        new AnalyticsController(reporterService),
        new ProfileController(repository),
        new DataController(repository)).build();
  }

  private StoredRawOffer stored(String id) {
    var offer = new RawJobOffer(id, "Java Dev", "Acme", null, "Kraków", true, "2026-07-04",
        "senior", "desc", 18000, 26000, "PLN", "month", false, "b2b", "url", "justjoin", null);
    return new StoredRawOffer(offer, "2026-07-04T00:00:00Z", null);
  }

  private OfferScore score(int value, String profileHash) {
    return new OfferScore(value, "why", profileHash, "2026-11-07T00:00:00Z");
  }

  @Test
  void postScanRunsAllStagesWithIncrementalDefaults() throws Exception {
    when(scraperService.scan(false, null)).thenReturn(new ScanResult(5, 10));
    when(offerDetailsService.fetchMissing(null)).thenReturn(new DetailsResult(3, 7));
    when(extractionService.extractMissing(null)).thenReturn(new ExtractionResult(3, 0));
    when(scoringService.scoreMissing(null)).thenReturn(new ScoringResult(4, 0));

    mvc.perform(post("/api/scan"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.added").value(5))
        .andExpect(jsonPath("$.total").value(10))
        .andExpect(jsonPath("$.detailsFetched").value(3))
        .andExpect(jsonPath("$.detailsRemaining").value(7))
        .andExpect(jsonPath("$.extracted").value(3))
        .andExpect(jsonPath("$.scored").value(4));
    verify(scraperService).scan(false, null);
    verify(offerDetailsService).fetchMissing(null);
    verify(extractionService).extractMissing(null);
    verify(scoringService).scoreMissing(null);
  }

  @Test
  void postScanPassesFullFetchAndLimitToAllStages() throws Exception {
    when(scraperService.scan(true, 20)).thenReturn(new ScanResult(20, 964));
    when(offerDetailsService.fetchMissing(20)).thenReturn(new DetailsResult(20, 100));
    when(extractionService.extractMissing(20)).thenReturn(new ExtractionResult(20, 80));
    when(scoringService.scoreMissing(20)).thenReturn(new ScoringResult(20, 60));

    mvc.perform(post("/api/scan").param("fullFetch", "true").param("limit", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.added").value(20))
        .andExpect(jsonPath("$.detailsFetched").value(20))
        .andExpect(jsonPath("$.scored").value(20));
    verify(scraperService).scan(true, 20);
    verify(offerDetailsService).fetchMissing(20);
    verify(extractionService).extractMissing(20);
    verify(scoringService).scoreMissing(20);
  }

  @Test
  void getJobsJoinsFilesFiltersSortsAndFlagsStale() throws Exception {
    when(repository.readProfile()).thenReturn(profile);
    when(hasher.hash(profile)).thenReturn("current");
    when(repository.readRawOffers())
        .thenReturn(List.of(stored("low"), stored("old"), stored("best"), stored("unscored")));
    var extractions = new LinkedHashMap<String, OfferExtraction>();
    extractions.put("best", new OfferExtraction(List.of("Java"), List.of(), "r", "p", "senior"));
    when(repository.readExtractions()).thenReturn(extractions);
    var scores = new LinkedHashMap<String, OfferScore>();
    scores.put("low", score(4, "current"));
    scores.put("old", score(7, "previous"));
    scores.put("best", score(9, "current"));
    when(repository.readScores()).thenReturn(scores);

    mvc.perform(get("/api/jobs"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].offer.id").value("best"))
        .andExpect(jsonPath("$[0].extraction.skills[0]").value("Java"))
        .andExpect(jsonPath("$[0].score.matchScore").value(9))
        .andExpect(jsonPath("$[0].stale").value(false))
        .andExpect(jsonPath("$[1].offer.id").value("old"))
        .andExpect(jsonPath("$[1].extraction").isEmpty())
        .andExpect(jsonPath("$[1].stale").value(true));
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
  void rescoreUsesConfiguredDaysByDefault() throws Exception {
    when(scoringService.rescoreRecent(7)).thenReturn(new ScoringResult(12, 1));
    when(scoringService.rescoreRecent(30)).thenReturn(new ScoringResult(40, 0));

    mvc.perform(post("/api/scores/rescore"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scored").value(12))
        .andExpect(jsonPath("$.remaining").value(1));
    mvc.perform(post("/api/scores/rescore").param("days", "30"))
        .andExpect(jsonPath("$.scored").value(40));
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
        .andExpect(jsonPath("$.length()").value(0));
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
