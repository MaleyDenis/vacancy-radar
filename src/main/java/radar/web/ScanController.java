package radar.web;

import org.springframework.web.bind.annotation.PostMapping;
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

  /** Fetches current offers into the raw-offers pool and returns how many were added / the total. */
  @PostMapping("/api/scan")
  public ScanResult scan() {
    return scraperService.scan();
  }
}
