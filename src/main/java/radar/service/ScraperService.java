package radar.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import radar.connector.JustJoinConnector;
import radar.model.ScanResult;
import radar.model.StoredRawOffer;
import radar.repository.JsonRepository;

/**
 * Orchestrates scraping. {@link #scan(boolean, Integer)} fetches offers from the connector
 * newest-first, merges new ones into the {@code raw-offers.json} pool (deduplicating by id — the pool
 * itself is the source of truth, no separate seen-ids list). Never removes offers.
 *
 * <p>This step is profile-independent and never calls Claude — extraction and scoring happen later, reading the
 * pool this method fills.
 */
@Service
public class ScraperService {

  private static final Logger log = LoggerFactory.getLogger(ScraperService.class);

  private final JustJoinConnector connector;
  private final JsonRepository repository;

  public ScraperService(JustJoinConnector connector, JsonRepository repository) {
    this.connector = connector;
    this.repository = repository;
  }

  /**
   * Walks the connector newest-first, merging brand-new offers into the pool (preserving each
   * existing offer's {@code firstSeenAt}), and persists.
   *
   * @param fullFetch when {@code false} (default), stops paginating as soon as a whole page holds no
   *                  offer that wasn't already in the pool — cheap incremental scans. When
   *                  {@code true}, walks every page to refresh the entire pool.
   * @param limit     optional cap on how many offers to process this run (newest first); {@code null}
   *                  = no cap.
   */
  public ScanResult scan(boolean fullFetch, Integer limit) {
    var now = Instant.now().toString();

    // Existing pool, keyed by id: an offer is "new" only if absent here.
    var pool = new LinkedHashMap<String, StoredRawOffer>();
    for (var stored : repository.readRawOffers()) {
      pool.put(stored.offer().id(), stored);
    }

    var added = 0;
    var processed = 0;
    var page = 1;
    var hasNext = true;
    var limitReached = false;

    while (hasNext && page <= JustJoinConnector.MAX_PAGES && !limitReached) {
      var p = connector.fetchPage(page);
      hasNext = p.hasNext();

      var newOnPage = 0;
      for (var offer : p.offers()) {
        if (limit != null && processed >= limit) {
          limitReached = true;
          break;
        }
        // Known offers are left untouched — only brand-new ones enter the pool.
        if (!pool.containsKey(offer.id())) {
          pool.put(offer.id(), StoredRawOffer.fresh(offer, now));
          added++;
          newOnPage++;
        }
        processed++;
      }
      log.info("Scan page {}: {} offers, {} new", page, p.offers().size(), newOnPage);

      // Incremental stop: once a full page brought nothing new, older pages hold only known offers.
      if (!fullFetch && !limitReached && newOnPage == 0) {
        break;
      }
      page++;
      if (hasNext && !limitReached) {
        sleepPolitely();
      }
    }

    repository.saveRawOffers(pool.values().stream().toList());
    log.info("Scan done: added {}, pool size {} (fullFetch={}, limit={})",
        added, pool.size(), fullFetch, limit);
    return new ScanResult(added, pool.size());
  }

  private static void sleepPolitely() {
    try {
      Thread.sleep(500 + ThreadLocalRandom.current().nextInt(500));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
