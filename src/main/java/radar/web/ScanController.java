package radar.web;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import radar.model.ScanResponse;
import radar.service.BackgroundRunner;
import radar.service.ExtractionService;
import radar.service.OfferDetailsService;
import radar.service.ScoringService;
import radar.service.ScraperService;

/**
 * Starts a scan in the background: scrape the listing into the raw-offers pool, fetch missing page
 * details, extract facts with Claude (once per offer), then score unscored offers against the
 * profile. Progress and the final {@link ScanResponse} are read from {@code GET /api/status}.
 */
@RestController
public class ScanController {

  private final ScraperService scraperService;
  private final OfferDetailsService offerDetailsService;
  private final ExtractionService extractionService;
  private final ScoringService scoringService;
  private final BackgroundRunner runner;

  public ScanController(ScraperService scraperService, OfferDetailsService offerDetailsService,
      ExtractionService extractionService, ScoringService scoringService,
      BackgroundRunner runner) {
    this.scraperService = scraperService;
    this.offerDetailsService = offerDetailsService;
    this.extractionService = extractionService;
    this.scoringService = scoringService;
    this.runner = runner;
  }

  /**
   * @param fullFetch walk every listing page; default {@code false} stops early once a page brings
   *                  nothing new
   * @param limit     caps each step this run (newest N scraped, N details, N extractions,
   *                  N scores); omit for no cap
   * @return 202 when started, 409 when a scan or rescore is already running
   */
  @PostMapping("/api/scan")
  public ResponseEntity<Map<String, Object>> scan(
      @RequestParam(defaultValue = "false") boolean fullFetch,
      @RequestParam(required = false) Integer limit) {
    var started = runner.start("scan", () -> runScan(fullFetch, limit));
    return started ? Busy.started() : Busy.conflict();
  }

  ScanResponse runScan(boolean fullFetch, Integer limit) {
    var scan = scraperService.scan(fullFetch, limit);
    var details = offerDetailsService.fetchMissing(limit);
    var extraction = extractionService.extractMissing(limit);
    var scoring = scoringService.scoreMissing(limit);
    return ScanResponse.of(scan, details, extraction, scoring);
  }

  /** What runs now (step, progress) and the result or error of the last finished task. */
  @GetMapping("/api/status")
  public BackgroundRunner.Status status() {
    return runner.status();
  }
}
