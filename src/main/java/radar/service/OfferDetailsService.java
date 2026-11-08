package radar.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import radar.connector.JustJoinConnector;
import radar.connector.OfferDetailsParser;
import radar.model.DetailsResult;
import radar.repository.JsonRepository;

/**
 * Fills in offer page details (full description + company domain) for pooled offers that don't have
 * them yet. Reads {@code raw-offers.json}, fetches each pending offer's page, parses the JobPosting
 * JSON-LD, and writes the updated pool back. Incremental (only {@code detailsFetchedAt == null}
 * offers), bounded by {@code limit}, and never calls Claude.
 */
@Service
public class OfferDetailsService {

  private static final Logger log = LoggerFactory.getLogger(OfferDetailsService.class);

  private final JustJoinConnector connector;
  private final OfferDetailsParser parser;
  private final JsonRepository repository;
  private final Progress progress;

  public OfferDetailsService(JustJoinConnector connector, OfferDetailsParser parser,
      JsonRepository repository, Progress progress) {
    this.connector = connector;
    this.parser = parser;
    this.repository = repository;
    this.progress = progress;
  }

  /**
   * Fetches page details for offers still missing them.
   *
   * @param limit optional cap on how many offers to fetch this run; {@code null} = no cap. Offers a
   *              fetch fails on are left for a later run.
   */
  /** Progress is written to disk after every this many fetched pages, not only at the end. */
  static final int SAVE_EVERY = 10;

  public DetailsResult fetchMissing(Integer limit) {
    var pool = repository.readRawOffers();
    var updated = new ArrayList<>(pool);
    var fetched = 0;
    var missing = 0;
    var pending = (int) pool.stream()
        .filter(s -> !s.hasDetails() && s.offer().applyUrl() != null).count();
    progress.begin("details", limit == null ? pending : Math.min(limit, pending));

    for (var i = 0; i < updated.size(); i++) {
      var stored = updated.get(i);
      if (stored.hasDetails() || stored.offer().applyUrl() == null) {
        continue;
      }
      missing++;
      if (limit != null && fetched >= limit) {
        continue; // leave for a later run
      }
      try {
        var html = connector.fetchOfferPageHtml(stored.offer().applyUrl());
        var details = parser.parse(html);
        updated.set(i, stored.withDetails(details.description(), details.domain(),
            Instant.now().toString()));
        fetched++;
        if (fetched % SAVE_EVERY == 0) {
          repository.saveRawOffers(updated); // a stopped server loses at most SAVE_EVERY pages
        }
      } catch (RuntimeException e) {
        log.warn("Details fetch failed for {}: {}", stored.offer().id(), e.toString());
      }
      progress.advance();
      sleepPolitely();
    }

    repository.saveRawOffers(updated);
    var remaining = missing - fetched;
    log.info("Details: fetched {}, remaining {} (limit={})", fetched, remaining, limit);
    return new DetailsResult(fetched, remaining);
  }

  /** Rate-limits between page fetches. Package-private/overridable so tests can run without sleeping. */
  void sleepPolitely() {
    try {
      Thread.sleep(500 + ThreadLocalRandom.current().nextInt(500));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
