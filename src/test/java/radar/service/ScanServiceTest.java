package radar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import radar.model.Analytics;
import radar.model.JobReport;
import radar.model.RawJobOffer;
import radar.model.ScanProgress;
import radar.model.SalaryRange;
import radar.model.UserProfile;
import radar.repository.JsonRepository;

class ScanServiceTest {

  private ScraperService scraper;
  private EnricherService enricher;
  private ReporterService reporter;
  private JsonRepository repository;
  private SseEmitter emitter;
  private List<ScanProgress> emitted;
  private ScanService scanService;

  private final UserProfile profile = new UserProfile(
      List.of("Java"), 3, "B2B", true, 15000, "PLN", "B2B", List.of("Kraków"), 6);

  @BeforeEach
  void setUp() {
    scraper = mock(ScraperService.class);
    enricher = mock(EnricherService.class);
    reporter = mock(ReporterService.class);
    repository = mock(JsonRepository.class);
    emitter = mock(SseEmitter.class);
    emitted = new ArrayList<>();
    // capture the event stream by overriding emit; still exercise complete()/completeWithError()
    scanService = new ScanService(scraper, enricher, reporter, repository) {
      @Override
      void emit(SseEmitter e, ScanProgress progress) {
        emitted.add(progress);
      }
    };
  }

  private RawJobOffer offer(String id) {
    return new RawJobOffer(id, "Java Dev", "Acme", null, "Kraków", true, "2026-07-04", "senior",
        null, List.of("Java"), 18000, 26000, "PLN", "b2b", "url", "justjoin");
  }

  private JobReport report(RawJobOffer offer, int score) {
    return new JobReport(offer, List.of("Java"), List.of(), "product", "senior", List.of(),
        List.of(), "focus", score, new SalaryRange(18000, 26000, "PLN"));
  }

  @Test
  void runScanEmitsEventsInOrderAndPersistsFilteredResults() {
    RawJobOffer a = offer("a");
    RawJobOffer b = offer("b");
    JobReport good = report(a, 8); // >= minMatchScore 6
    JobReport weak = report(b, 4); // filtered out
    Analytics analytics = new Analytics("2026-07-04", 2, java.util.Map.of("Java", 2), List.of(),
        null, java.util.Map.of(), 100.0);

    when(repository.readProfile()).thenReturn(profile);
    when(scraper.scrapeNew()).thenReturn(List.of(a, b));
    when(enricher.enrichAll(anyList(), eq(profile), any())).thenAnswer(inv -> {
      Consumer<ScanProgress> cb = inv.getArgument(2);
      cb.accept(new ScanProgress(ScanProgress.Type.ENRICHING, "Enriched a", 1, 2, null));
      cb.accept(new ScanProgress(ScanProgress.Type.ENRICHING, "Enriched b", 2, 2, null));
      return List.of(good, weak);
    });
    when(reporter.buildAnalytics(anyString(), anyList())).thenReturn(analytics);

    scanService.runScan(emitter, null);

    // event order: SCANNING, SCANNING, ENRICHING, ENRICHING, SAVING, DONE
    assertThat(emitted).extracting(ScanProgress::type).containsExactly(
        ScanProgress.Type.SCANNING, ScanProgress.Type.SCANNING,
        ScanProgress.Type.ENRICHING, ScanProgress.Type.ENRICHING,
        ScanProgress.Type.SAVING, ScanProgress.Type.DONE);

    // only the offer meeting minMatchScore is saved as jobs; analytics saved regardless
    ArgumentCaptor<List<JobReport>> jobs = ArgumentCaptor.forClass(List.class);
    verify(repository).saveJobs(anyString(), jobs.capture());
    assertThat(jobs.getValue()).containsExactly(good);
    verify(repository).saveAnalytics(anyString(), eq(analytics));
    verify(scraper).markAsSeen(List.of("a", "b"));
    verify(emitter).complete();

    ScanProgress done = emitted.get(emitted.size() - 1);
    assertThat(done.saved()).isEqualTo(1);
    assertThat(done.total()).isEqualTo(2);
  }

  @Test
  void runScanSavesAnalyticsEvenWhenNothingPassesFilter() {
    RawJobOffer a = offer("a");
    JobReport weak = report(a, 3);
    Analytics analytics = new Analytics("2026-07-04", 1, java.util.Map.of(), List.of(), null,
        java.util.Map.of(), 100.0);

    when(repository.readProfile()).thenReturn(profile);
    when(scraper.scrapeNew()).thenReturn(List.of(a));
    when(enricher.enrichAll(anyList(), any(), any())).thenReturn(List.of(weak));
    when(reporter.buildAnalytics(anyString(), anyList())).thenReturn(analytics);

    scanService.runScan(emitter, null);

    ArgumentCaptor<List<JobReport>> jobs = ArgumentCaptor.forClass(List.class);
    verify(repository).saveJobs(anyString(), jobs.capture());
    assertThat(jobs.getValue()).isEmpty();
    verify(repository).saveAnalytics(anyString(), eq(analytics));
    verify(emitter).complete();
  }

  @Test
  void runScanEmitsErrorAndCompletesWithErrorOnFailure() {
    when(repository.readProfile()).thenReturn(profile);
    RuntimeException boom = new RuntimeException("scrape failed");
    when(scraper.scrapeNew()).thenThrow(boom);

    scanService.runScan(emitter, null);

    assertThat(emitted).extracting(ScanProgress::type)
        .contains(ScanProgress.Type.ERROR)
        .doesNotContain(ScanProgress.Type.DONE);
    verify(emitter).completeWithError(boom);
    verify(repository, org.mockito.Mockito.never()).saveJobs(anyString(), anyList());
  }

  @Test
  void runScanCapsEnrichmentAndSeenIdsToLimit() {
    RawJobOffer a = offer("a");
    RawJobOffer b = offer("b");
    RawJobOffer c = offer("c");
    Analytics analytics = new Analytics("2026-07-04", 2, java.util.Map.of(), List.of(), null,
        java.util.Map.of(), 100.0);

    when(repository.readProfile()).thenReturn(profile);
    when(scraper.scrapeNew()).thenReturn(List.of(a, b, c));
    ArgumentCaptor<List<RawJobOffer>> enriched = ArgumentCaptor.forClass(List.class);
    when(enricher.enrichAll(enriched.capture(), any(), any()))
        .thenReturn(List.of(report(a, 8), report(b, 7)));
    when(reporter.buildAnalytics(anyString(), anyList())).thenReturn(analytics);

    scanService.runScan(emitter, 2);

    // only the first 2 offers are enriched and marked seen; the third is left for a later run
    assertThat(enriched.getValue()).containsExactly(a, b);
    verify(scraper).markAsSeen(List.of("a", "b"));
    ScanProgress done = emitted.get(emitted.size() - 1);
    assertThat(done.type()).isEqualTo(ScanProgress.Type.DONE);
    assertThat(done.total()).isEqualTo(2);
  }
}
