package radar.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import radar.model.RawJobOffer;
import radar.model.ScanProgress;
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

  /**
   * Launches the scan asynchronously and returns immediately.
   *
   * @param limit optional cap on how many new offers to enrich this run (null = no cap).
   */
  public void startScan(SseEmitter emitter, Integer limit) {
    CompletableFuture.runAsync(() -> runScan(emitter, limit));
  }

  /**
   * The scan body. Package-private and synchronous so it can be unit-tested directly; {@link
   * #startScan} wraps it in a background task.
   */
  void runScan(SseEmitter emitter, Integer limit) {
    try {
      var date = LocalDate.now().toString();
      var profile = repository.readProfile();

      emit(emitter, new ScanProgress(ScanProgress.Type.SCANNING, "Scanning offers", null, null, null));
      var newOffers = capToLimit(scraperService.scrapeNew(), limit);
      emit(emitter, new ScanProgress(
          ScanProgress.Type.SCANNING, "Found new offers", 0, newOffers.size(), null));

      var allReports =
          enricherService.enrichAll(newOffers, profile, progress -> emit(emitter, progress));

      var analytics = reporterService.buildAnalytics(date, allReports);
      var minScore = profile.minMatchScore() == null ? 0 : profile.minMatchScore();
      var filtered = allReports.stream()
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

  /**
   * Caps the offer list to at most {@code limit} entries. Because this runs before enrichment and
   * {@code markAsSeen}, only the offers actually processed this run get marked seen.
   */
  private static List<RawJobOffer> capToLimit(List<RawJobOffer> offers, Integer limit) {
    if (limit == null || limit < 0 || offers.size() <= limit) {
      return offers;
    }
    return List.copyOf(offers.subList(0, limit));
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
