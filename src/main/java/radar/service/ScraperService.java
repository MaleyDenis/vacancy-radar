package radar.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import radar.connector.JustJoinConnector;
import radar.model.RawJobOffer;
import radar.model.ScanResult;
import radar.model.StoredRawOffer;
import radar.repository.JsonRepository;

/**
 * Orchestrates scraping. {@link #scan()} fetches every current offer from the connector, merges new
 * ones into the {@code raw-offers.json} pool (deduplicating by id — the pool itself is the source of
 * truth, no separate seen-ids list), and drops offers older than the configured retention window.
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
   * Fetches current offers, merges brand-new ones into the pool (preserving each existing offer's
   * {@code firstSeenAt}), prunes offers past the retention window, and persists the result.
   */
  public ScanResult scan() {
    String now = Instant.now().toString();
    List<RawJobOffer> fetched = connector.fetchAll();

    // Key existing pool by id so we can preserve firstSeenAt and refresh the offer payload.
    Map<String, StoredRawOffer> pool = new LinkedHashMap<>();
    for (StoredRawOffer stored : repository.readRawOffers()) {
      pool.put(stored.offer().id(), stored);
    }

    int added = 0;
    for (RawJobOffer offer : fetched) {
      StoredRawOffer existing = pool.get(offer.id());
      if (existing == null) {
        pool.put(offer.id(), new StoredRawOffer(offer, now));
        added++;
      } else {
        // Same offer seen again: refresh its fields (salary/title may change) but keep firstSeenAt.
        pool.put(offer.id(), new StoredRawOffer(offer, existing.firstSeenAt()));
      }
    }

    Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
    List<StoredRawOffer> merged = pool.values().stream()
        .filter(stored -> !isExpired(stored, cutoff))
        .toList();

    repository.saveRawOffers(merged);
    log.info("Scan: fetched {}, added {}, pool size {} (retention {}d)",
        fetched.size(), added, merged.size(), retentionDays);
    return new ScanResult(added, merged.size());
  }

  /**
   * An offer is expired when its reference date is before {@code cutoff}. The reference date is
   * {@code publishedAt} when parseable, otherwise {@code firstSeenAt}. If neither parses, the offer
   * is kept (we never drop an offer we can't date).
   */
  private static boolean isExpired(StoredRawOffer stored, Instant cutoff) {
    Instant reference = parseInstant(stored.offer().publishedAt());
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

  /** Returns only offers whose id has not been seen before. Does not persist anything. */
  @Deprecated
  public List<RawJobOffer> scrapeNew() {
    List<RawJobOffer> all = connector.fetchAll();
    Set<String> seen = new HashSet<>(repository.readSeenIds());
    List<RawJobOffer> fresh = new ArrayList<>();
    for (RawJobOffer offer : all) {
      if (!seen.contains(offer.id())) {
        fresh.add(offer);
      }
    }
    return fresh;
  }

  /** Records the given offer ids as processed so they are skipped on the next scan. */
  @Deprecated
  public void markAsSeen(List<String> ids) {
    repository.appendSeenIds(ids);
  }
}
