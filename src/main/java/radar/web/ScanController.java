package radar.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import radar.model.ScanResponse;
import radar.service.OfferDetailsService;
import radar.service.ScraperService;

/**
 * Runs a scan: scrape the listing into the raw-offers pool, then fetch page details for offers that
 * don't have them yet. No Claude here — enrichment/evaluation is a separate step. Synchronous; use
 * {@code limit} to bound heavy runs.
 */
@RestController
public class ScanController {

  private final ScraperService scraperService;
  private final OfferDetailsService offerDetailsService;

  public ScanController(ScraperService scraperService, OfferDetailsService offerDetailsService) {
    this.scraperService = scraperService;
    this.offerDetailsService = offerDetailsService;
  }

  /**
   * @param fullFetch walk every listing page to refresh the whole pool; default {@code false} stops
   *                  early once a page brings nothing new
   * @param limit     caps both the scrape (newest N) and the details fetch (N pending) this run;
   *                  omit for no cap
   */
  @PostMapping("/api/scan")
  public ScanResponse scan(
      @RequestParam(defaultValue = "false") boolean fullFetch,
      @RequestParam(required = false) Integer limit) {
    var scan = scraperService.scan(fullFetch, limit);
    var details = offerDetailsService.fetchMissing(limit);
    return ScanResponse.of(scan, details);
  }
}
