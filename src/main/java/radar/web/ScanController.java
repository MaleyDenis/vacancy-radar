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

  @GetMapping("/api/status")
  public BackgroundRunner.Status status() {
    return runner.status();
  }
}
