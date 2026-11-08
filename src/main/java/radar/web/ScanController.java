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
import radar.service.ScraperService;

/**
 * Starts a scan in the background: scrape the listing into the raw-offers pool, fetch missing page
 * details, then extract facts with Claude (once per offer). No scoring: offers are scored one by
 * one from their card. Progress and the final {@link ScanResponse} are read from
 * {@code GET /api/status}.
 */
@RestController
public class ScanController {

  private final ScraperService scraperService;
  private final OfferDetailsService offerDetailsService;
  private final ExtractionService extractionService;
  private final BackgroundRunner runner;

  public ScanController(ScraperService scraperService, OfferDetailsService offerDetailsService,
      ExtractionService extractionService, BackgroundRunner runner) {
    this.scraperService = scraperService;
    this.offerDetailsService = offerDetailsService;
    this.extractionService = extractionService;
    this.runner = runner;
  }

  /**
   * @param fullFetch walk every listing page; default {@code false} stops early once a page brings
   *                  nothing new
   * @param limit     caps each step this run (newest N scraped, N details, N extractions);
   *                  omit for no cap
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
    return ScanResponse.of(scan, details, extraction);
  }

  /** What runs now (step, progress) and the result or error of the last finished task. */
  @GetMapping("/api/status")
  public BackgroundRunner.Status status() {
    return runner.status();
  }
}
