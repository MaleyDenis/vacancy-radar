package radar.service;

import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import radar.connector.HtmlText;
import radar.model.StoredRawOffer;
import radar.repository.JsonRepository;

// on startup, so every deployed copy converts its own data; a no-op once nothing is HTML
@Component
public class DescriptionMigration implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DescriptionMigration.class);

  private final JsonRepository repository;

  public DescriptionMigration(JsonRepository repository) {
    this.repository = repository;
  }

  @Override
  public void run(ApplicationArguments args) {
    migrate();
  }

  int migrate() {
    var offers = repository.readRawOffers();
    var migrated = new ArrayList<StoredRawOffer>(offers.size());
    var changed = 0;

    for (var stored : offers) {
      var offer = stored.offer();
      if (HtmlText.isHtml(offer.description())) {
        var text = HtmlText.toText(offer.description());
        migrated.add(new StoredRawOffer(offer.withDetails(text, offer.domain()),
            stored.firstSeenAt(), stored.detailsFetchedAt()));
        changed++;
      } else {
        migrated.add(stored);
      }
    }

    if (changed == 0) {
      return 0;
    }

    repository.backupRawOffers();
    repository.saveRawOffers(migrated);
    log.info("Descriptions: converted {} of {} from HTML to text", changed, offers.size());

    return changed;
  }
}
