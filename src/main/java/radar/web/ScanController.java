package radar.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import radar.model.ScanResponse;
import radar.service.ExtractionService;
import radar.service.OfferDetailsService;
import radar.service.ScoringService;
import radar.service.ScraperService;

/**
 * Runs a scan end to end: scrape the listing into the raw-offers pool, fetch missing page details,
 * extract facts with Claude (once per offer), then score unscored offers against the profile.
 * Synchronous; use {@code limit} to bound heavy runs. Requires a profile — scoring throws if it is missing.
 */
@RestController
public class ScanController {

  private final ScraperService scraperService;
  private final OfferDetailsService offerDetailsService;
  private final ExtractionService extractionService;
  private final ScoringService scoringService;

  public ScanController(ScraperService scraperService, OfferDetailsService offerDetailsService,
      ExtractionService extractionService, ScoringService scoringService) {
    this.scraperService = scraperService;
    this.offerDetailsService = offerDetailsService;
    this.extractionService = extractionService;
    this.scoringService = scoringService;
  }

  /**
   * @param fullFetch walk every listing page to refresh the whole pool; default {@code false} stops
   *                  early once a page brings nothing new
   * @param limit     caps each step this run (newest N scraped, N details, N extractions,
   *                  N scores); omit for no cap
   */
  @PostMapping("/api/scan")
  public ScanResponse scan(
      @RequestParam(defaultValue = "false") boolean fullFetch,
      @RequestParam(required = false) Integer limit) {
    var scan = scraperService.scan(fullFetch, limit);
    var details = offerDetailsService.fetchMissing(limit);
    var extraction = extractionService.extractMissing(limit);
    var scoring = scoringService.scoreMissing(limit);
    return ScanResponse.of(scan, details, extraction, scoring);
  }
}
