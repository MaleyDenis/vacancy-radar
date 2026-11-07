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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import radar.model.DetailsResult;
import radar.model.EvaluationResult;
import radar.model.ExtractionResult;
import radar.model.JobReport;
import radar.model.RawJobOffer;
import radar.model.ScanResult;
import radar.model.UserProfile;
import radar.repository.JsonRepository;
import radar.service.EvaluationService;
import radar.service.ExtractionService;
import radar.service.OfferDetailsService;
import radar.service.ReporterService;
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
  private EvaluationService evaluationService;
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
    evaluationService = mock(EvaluationService.class);
    reporterService = mock(ReporterService.class);
    mvc = MockMvcBuilders.standaloneSetup(
        new ScanController(scraperService, offerDetailsService, extractionService,
            evaluationService),
        new JobController(repository),
        new AnalyticsController(reporterService),
        new ProfileController(repository),
        new DataController(repository)).build();
  }

  private JobReport report(String id, int score) {
    var offer = new RawJobOffer(id, "Java Dev", "Acme", null, "Kraków", true, "2026-07-04",
        "senior", null, 18000, 26000, "PLN", "month", false, "b2b", "url", "justjoin", null);
    return new JobReport(offer, List.of("Java"), "resp", "proj", List.of(), "senior", score);
  }

  @Test
  void postScanRunsAllStagesWithIncrementalDefaults() throws Exception {
    when(scraperService.scan(false, null)).thenReturn(new ScanResult(5, 10));
    when(offerDetailsService.fetchMissing(null)).thenReturn(new DetailsResult(3, 7));
    when(extractionService.extractMissing(null)).thenReturn(new ExtractionResult(3, 0));
    when(evaluationService.evaluate(null)).thenReturn(new EvaluationResult(4, 10));

    mvc.perform(post("/api/scan"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.added").value(5))
        .andExpect(jsonPath("$.total").value(10))
        .andExpect(jsonPath("$.detailsFetched").value(3))
        .andExpect(jsonPath("$.detailsRemaining").value(7))
        .andExpect(jsonPath("$.extracted").value(3))
        .andExpect(jsonPath("$.evaluated").value(4));
    verify(scraperService).scan(false, null);
    verify(offerDetailsService).fetchMissing(null);
    verify(extractionService).extractMissing(null);
    verify(evaluationService).evaluate(null);
  }

  @Test
  void postScanPassesFullFetchAndLimitToAllStages() throws Exception {
    when(scraperService.scan(true, 20)).thenReturn(new ScanResult(20, 964));
    when(offerDetailsService.fetchMissing(20)).thenReturn(new DetailsResult(20, 100));
    when(extractionService.extractMissing(20)).thenReturn(new ExtractionResult(20, 80));
    when(evaluationService.evaluate(20)).thenReturn(new EvaluationResult(20, 20));

    mvc.perform(post("/api/scan").param("fullFetch", "true").param("limit", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.added").value(20))
        .andExpect(jsonPath("$.detailsFetched").value(20))
        .andExpect(jsonPath("$.evaluated").value(20));
    verify(scraperService).scan(true, 20);
    verify(offerDetailsService).fetchMissing(20);
    verify(extractionService).extractMissing(20);
    verify(evaluationService).evaluate(20);
  }

  @Test
  void getJobsFiltersByMinMatchScore() throws Exception {
    when(repository.readProfile()).thenReturn(profile);
    when(repository.loadJobs()).thenReturn(List.of(report("a", 8), report("b", 4)));

    mvc.perform(get("/api/jobs"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].matchScore").value(8));
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
  void deleteJobsRemovesFileAndClearsEvaluationMarks() throws Exception {
    when(repository.deleteAllJobs()).thenReturn(1);

    mvc.perform(delete("/api/jobs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deleted").value(1));
    verify(repository).deleteAllJobs();
    verify(repository).clearEvaluationMarks();
  }

  @Test
  void getJobsReturnsEmptyWhenNone() throws Exception {
    when(repository.readProfile()).thenReturn(profile);
    when(repository.loadJobs()).thenReturn(List.of());

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
