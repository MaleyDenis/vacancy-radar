package radar.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import radar.connector.JustJoinConnector;
import radar.model.RawJobOffer;
import radar.repository.JsonRepository;

/**
 * Orchestrates scraping: fetches all offers from the connector and filters out the ones already
 * processed (tracked in {@code seen-ids.json}). Seen ids are only persisted after enrichment
 * succeeds, so {@link #markAsSeen(List)} is a separate step the caller invokes at the end of a scan.
 */
@Service
public class ScraperService {

  private final JustJoinConnector connector;
  private final JsonRepository repository;

  public ScraperService(JustJoinConnector connector, JsonRepository repository) {
    this.connector = connector;
    this.repository = repository;
  }

  /** Returns only offers whose id has not been seen before. Does not persist anything. */
  public List<RawJobOffer> scrapeNew() {
    List<RawJobOffer> all = connector.fetchAll();
    Set<String> seen = new HashSet<>(repository.readSeenIds());
    return all.stream()
        .filter(offer -> !seen.contains(offer.id()))
        .toList();
  }

  /** Records the given offer ids as processed so they are skipped on the next scan. */
  public void markAsSeen(List<String> ids) {
    repository.appendSeenIds(ids);
  }
}
