package radar.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import radar.model.JobReport;
import radar.model.RawJobOffer;
import radar.model.SalaryRange;
import radar.model.UserProfile;
import radar.repository.JsonRepository;
import radar.service.ReporterService;
import radar.service.ScanService;

/**
 * Standalone MockMvc tests for the REST layer — no Spring context, so no dependency on the web test
 * slice (which Spring Boot 4 splits into a separate module not on the classpath).
 */
class WebControllersTest {

  private JsonRepository repository;
  private ScanService scanService;
  private ReporterService reporterService;
  private MockMvc mvc;

  private final UserProfile profile = new UserProfile(
      List.of("Java", "Spring"), 3, "B2B", true, 15000, "PLN", "B2B", List.of("Kraków"), 6);

  @BeforeEach
  void setUp() {
    repository = mock(JsonRepository.class);
    scanService = mock(ScanService.class);
    reporterService = mock(ReporterService.class);
    mvc = MockMvcBuilders.standaloneSetup(
        new ScanController(scanService),
        new JobController(repository),
        new AnalyticsController(reporterService),
        new ProfileController(repository)).build();
  }

  private JobReport report(String id, int score) {
    RawJobOffer offer = new RawJobOffer(id, "Java Dev", "Acme", null, "Kraków", true, "2026-07-04",
        "senior", null, List.of("Java"), 18000, 26000, "PLN", "b2b", "url", "justjoin");
    return new JobReport(offer, List.of("Java"), List.of(), "product", "senior", List.of(),
        List.of(), "focus", score, new SalaryRange(18000, 26000, "PLN"));
  }

  @Test
  void postScanStartsAsyncScanWithNoLimit() throws Exception {
    mvc.perform(post("/api/scan")).andExpect(request().asyncStarted());
    verify(scanService).startScan(any(), org.mockito.ArgumentMatchers.isNull());
  }

  @Test
  void postScanPassesLimitParam() throws Exception {
    mvc.perform(post("/api/scan").param("limit", "20")).andExpect(request().asyncStarted());
    verify(scanService).startScan(any(), org.mockito.ArgumentMatchers.eq(20));
  }

  @Test
  void getJobsUsesLatestDateAndFiltersByMinMatchScore() throws Exception {
    when(repository.listDates()).thenReturn(List.of("2026-07-03", "2026-07-04"));
    when(repository.readProfile()).thenReturn(profile);
    when(repository.loadJobs("2026-07-04")).thenReturn(List.of(report("a", 8), report("b", 4)));

    mvc.perform(get("/api/jobs"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].matchScore").value(8));
  }

  @Test
  void getJobsHonoursExplicitDateParam() throws Exception {
    when(repository.readProfile()).thenReturn(profile);
    when(repository.loadJobs("2026-07-01")).thenReturn(List.of(report("x", 9)));

    mvc.perform(get("/api/jobs").param("date", "2026-07-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].rawJobOffer.id").value("x"));
    verify(repository).loadJobs("2026-07-01");
  }

  @Test
  void getJobsReturnsEmptyArrayWhenNoSnapshots() throws Exception {
    when(repository.listDates()).thenReturn(List.of());

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
