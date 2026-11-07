package radar.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import radar.model.ScanResponse;
import radar.service.EvaluationService;
import radar.service.ExtractionService;
import radar.service.OfferDetailsService;
import radar.service.ScraperService;

/**
 * Runs a scan end to end: scrape the listing into the raw-offers pool, fetch page details for offers
 * that don't have them, extract facts with Claude (once per offer), then score offers against the profile with Claude. Synchronous; use
 * {@code limit} to bound heavy runs. Requires a profile — evaluation throws if it is missing.
 */
@RestController
public class ScanController {

  private final ScraperService scraperService;
  private final OfferDetailsService offerDetailsService;
  private final ExtractionService extractionService;
  private final EvaluationService evaluationService;

  public ScanController(ScraperService scraperService, OfferDetailsService offerDetailsService,
      ExtractionService extractionService, EvaluationService evaluationService) {
    this.scraperService = scraperService;
    this.offerDetailsService = offerDetailsService;
    this.extractionService = extractionService;
    this.evaluationService = evaluationService;
  }

  /**
   * @param fullFetch walk every listing page to refresh the whole pool; default {@code false} stops
   *                  early once a page brings nothing new
   * @param limit     caps the scrape (newest N), the details fetch (N pending), the extraction (N pending), and the evaluation
   *                  (N unscored) this run; omit for no cap
   */
  @PostMapping("/api/scan")
  public ScanResponse scan(
      @RequestParam(defaultValue = "false") boolean fullFetch,
      @RequestParam(required = false) Integer limit) {
    var scan = scraperService.scan(fullFetch, limit);
    var details = offerDetailsService.fetchMissing(limit);
    var extraction = extractionService.extractMissing(limit);
    var eval = evaluationService.evaluate(limit);
    return ScanResponse.of(scan, details, extraction, eval);
  }
}
