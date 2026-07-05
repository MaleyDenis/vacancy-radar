package radar.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import radar.model.ScanResult;
import radar.service.ScraperService;

/**
 * Triggers a scrape. Scraping is fast (no enrichment, no Claude), so this endpoint runs
 * synchronously and returns the {@link ScanResult} directly. Enrichment/evaluation is a separate
 * endpoint.
 */
@RestController
public class ScanController {

  private final ScraperService scraperService;

  public ScanController(ScraperService scraperService) {
    this.scraperService = scraperService;
  }

  /**
   * Fetches current offers into the raw-offers pool and returns how many were added / the total.
   *
   * @param fullFetch walk every page to refresh the whole pool; default {@code false} stops early
   *                  once a page brings nothing new (cheap incremental scan)
   * @param limit     cap on how many offers to process this run (newest first); omit for no cap
   */
  @PostMapping("/api/scan")
  public ScanResult scan(
      @RequestParam(defaultValue = "false") boolean fullFetch,
      @RequestParam(required = false) Integer limit) {
    return scraperService.scan(fullFetch, limit);
  }
}
