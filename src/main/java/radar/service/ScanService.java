package radar.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import radar.model.Analytics;
import radar.model.JobReport;
import radar.model.RawJobOffer;
import radar.model.ScanProgress;
import radar.model.UserProfile;
import radar.repository.JsonRepository;

/**
 * Orchestrates a full scan: scrape → enrich → aggregate analytics → filter → persist, streaming
 * {@link ScanProgress} events to the caller's {@link SseEmitter} as it goes. Runs asynchronously so
 * the HTTP request returns immediately.
 */
@Service
public class ScanService {

  private final ScraperService scraperService;
  private final EnricherService enricherService;
  private final ReporterService reporterService;
  private final JsonRepository repository;

  public ScanService(ScraperService scraperService, EnricherService enricherService,
      ReporterService reporterService, JsonRepository repository) {
    this.scraperService = scraperService;
    this.enricherService = enricherService;
    this.reporterService = reporterService;
    this.repository = repository;
  }

  /** Launches the scan asynchronously and returns immediately. */
  public void startScan(SseEmitter emitter) {
    CompletableFuture.runAsync(() -> runScan(emitter));
  }

  /**
   * The scan body. Package-private and synchronous so it can be unit-tested directly; {@link
   * #startScan} wraps it in a background task.
   */
  void runScan(SseEmitter emitter) {
    try {
      String date = LocalDate.now().toString();
      UserProfile profile = repository.readProfile();

      emit(emitter, new ScanProgress(ScanProgress.Type.SCANNING, "Scanning offers", null, null, null));
      List<RawJobOffer> newOffers = scraperService.scrapeNew();
      emit(emitter, new ScanProgress(
          ScanProgress.Type.SCANNING, "Found new offers", 0, newOffers.size(), null));

      List<JobReport> allReports =
          enricherService.enrichAll(newOffers, profile, progress -> emit(emitter, progress));

      Analytics analytics = reporterService.buildAnalytics(date, allReports);
      int minScore = profile.minMatchScore() == null ? 0 : profile.minMatchScore();
      List<JobReport> filtered = allReports.stream()
          .filter(report -> report.matchScore() >= minScore)
          .toList();

      emit(emitter, new ScanProgress(
          ScanProgress.Type.SAVING, "Saving results", null, null, filtered.size()));
      repository.saveJobs(date, filtered);
      repository.saveAnalytics(date, analytics);
      scraperService.markAsSeen(newOffers.stream().map(RawJobOffer::id).toList());

      emit(emitter, new ScanProgress(
          ScanProgress.Type.DONE, "Scan complete", newOffers.size(), newOffers.size(),
          filtered.size()));
      emitter.complete();
    } catch (Exception e) {
      try {
        emit(emitter, new ScanProgress(ScanProgress.Type.ERROR, e.getMessage(), null, null, null));
      } catch (RuntimeException ignored) {
        // the stream is already broken; fall through to completeWithError
      }
      emitter.completeWithError(e);
    }
  }

  /** Sends one progress event over SSE. Package-private/overridable so tests can observe the stream. */
  void emit(SseEmitter emitter, ScanProgress progress) {
    try {
      emitter.send(SseEmitter.event().data(progress));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to emit scan progress", e);
    }
  }
}
