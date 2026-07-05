package radar.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import radar.connector.JustJoinConnector;
import radar.model.ScanResult;
import radar.model.StoredRawOffer;
import radar.repository.JsonRepository;

/**
 * Orchestrates scraping. {@link #scan(boolean, Integer)} fetches offers from the connector
 * newest-first, merges new ones into the {@code raw-offers.json} pool (deduplicating by id — the pool
 * itself is the source of truth, no separate seen-ids list), and drops offers older than the
 * configured retention window.
 *
 * <p>This step is profile-independent and never calls Claude — enrichment happens later, reading the
 * pool this method fills.
 */
@Service
public class ScraperService {

  private static final Logger log = LoggerFactory.getLogger(ScraperService.class);

  private final JustJoinConnector connector;
  private final JsonRepository repository;
  private final int retentionDays;

  public ScraperService(JustJoinConnector connector, JsonRepository repository,
      @Value("${radar.offers.retention-days:30}") int retentionDays) {
    this.connector = connector;
    this.repository = repository;
    this.retentionDays = retentionDays;
  }

  /**
   * Walks the connector newest-first, merging brand-new offers into the pool (preserving each
   * existing offer's {@code firstSeenAt}), prunes offers past the retention window, and persists.
   *
   * @param fullFetch when {@code false} (default), stops paginating as soon as a whole page holds no
   *                  offer that wasn't already in the pool — cheap incremental scans. When
   *                  {@code true}, walks every page to refresh the entire pool.
   * @param limit     optional cap on how many offers to process this run (newest first); {@code null}
   *                  = no cap.
   */
  public ScanResult scan(boolean fullFetch, Integer limit) {
    var now = Instant.now().toString();

    // Existing pool, keyed by id. knownIds is a snapshot: an offer is "new" only if absent here.
    var pool = new LinkedHashMap<String, StoredRawOffer>();
    for (var stored : repository.readRawOffers()) {
      pool.put(stored.offer().id(), stored);
    }
    var knownIds = new HashSet<>(pool.keySet());

    var added = 0;
    var processed = 0;
    var page = 1;
    var totalPages = 1;
    var limitReached = false;

    while (page <= totalPages && page <= JustJoinConnector.MAX_PAGES && !limitReached) {
      var p = connector.fetchPage(page);
      totalPages = p.totalPages();

      var newOnPage = 0;
      for (var offer : p.offers()) {
        if (limit != null && processed >= limit) {
          limitReached = true;
          break;
        }
        var existing = pool.get(offer.id());
        if (existing == null) {
          pool.put(offer.id(), StoredRawOffer.fresh(offer, now));
          added++;
          newOnPage++;
        } else {
          // Refresh list fields (salary/title may change) but keep already-fetched page details
          // and their timestamps — the list API never carries description/domain.
          var refreshed = offer.withDetails(existing.offer().description(), existing.offer().domain());
          pool.put(offer.id(),
              new StoredRawOffer(refreshed, existing.firstSeenAt(), existing.detailsFetchedAt()));
        }
        processed++;
      }
      log.info("Scan page {}/{}: {} offers, {} new", page, totalPages, p.offers().size(), newOnPage);

      // Incremental stop: once a full page brought nothing new, older pages hold only known offers.
      if (!fullFetch && !limitReached && newOnPage == 0) {
        break;
      }
      page++;
      if (page <= totalPages && !limitReached) {
        sleepPolitely();
      }
    }

    var cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
    var merged = pool.values().stream()
        .filter(stored -> !isExpired(stored, cutoff))
        .toList();

    repository.saveRawOffers(merged);
    log.info("Scan done: added {}, pool size {} (fullFetch={}, limit={}, retention {}d)",
        added, merged.size(), fullFetch, limit, retentionDays);
    return new ScanResult(added, merged.size());
  }

  private static void sleepPolitely() {
    try {
      Thread.sleep(500 + ThreadLocalRandom.current().nextInt(500));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * An offer is expired when its reference date is before {@code cutoff}. The reference date is
   * {@code publishedAt} when parseable, otherwise {@code firstSeenAt}. If neither parses, the offer
   * is kept (we never drop an offer we can't date).
   */
  private static boolean isExpired(StoredRawOffer stored, Instant cutoff) {
    var reference = parseInstant(stored.offer().publishedAt());
    if (reference == null) {
      reference = parseInstant(stored.firstSeenAt());
    }
    return reference != null && reference.isBefore(cutoff);
  }

  private static Instant parseInstant(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(value);
    } catch (RuntimeException e) {
      return null;
    }
  }
}
