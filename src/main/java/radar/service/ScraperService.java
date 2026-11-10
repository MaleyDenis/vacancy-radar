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

@Service
public class ScraperService {

  private static final Logger log = LoggerFactory.getLogger(ScraperService.class);

  private final JustJoinConnector connector;
  private final JsonRepository repository;
  private final Progress progress;

  public ScraperService(JustJoinConnector connector, JsonRepository repository, Progress progress) {
    this.connector = connector;
    this.repository = repository;
    this.progress = progress;
  }

  public ScanResult scan(boolean fullFetch, Integer limit) {
    var now = Instant.now().toString();

    var pool = new LinkedHashMap<String, StoredRawOffer>();
    for (var stored : repository.readRawOffers()) {
      pool.put(stored.offer().id(), stored);
    }

    var added = 0;
    var processed = 0;
    var page = 1;
    var hasNext = true;
    var limitReached = false;
    progress.begin("scrape", 0);

    while (hasNext && page <= JustJoinConnector.MAX_PAGES && !limitReached) {
      var p = connector.fetchPage(page);
      hasNext = p.hasNext();
      progress.advance();

      var newOnPage = 0;
      for (var offer : p.offers()) {
        if (limit != null && processed >= limit) {
          limitReached = true;
          break;
        }
        var known = pool.get(offer.id());
        if (known == null) {
          pool.put(offer.id(), StoredRawOffer.fresh(offer, now));
          added++;
          newOnPage++;
        } else if (offer.locations() != null
            && !offer.locations().equals(known.offer().locations())) {
          // Known offers keep everything except their city list, which the listing may update
          // (or which older entries lack).
          pool.put(offer.id(), new StoredRawOffer(known.offer().withLocations(offer.locations()),
              known.firstSeenAt(), known.detailsFetchedAt()));
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
