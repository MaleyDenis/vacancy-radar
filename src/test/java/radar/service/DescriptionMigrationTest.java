package radar.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import radar.model.RawJobOffer;
import radar.model.StoredRawOffer;
import radar.repository.JsonRepository;

class DescriptionMigrationTest {

  @TempDir
  Path tmp;

  private JsonRepository repository;
  private DescriptionMigration migration;

  @BeforeEach
  void setUp() {
    repository = new JsonRepository(new ObjectMapper(), tmp.toString());
    migration = new DescriptionMigration(repository);
  }

  private static StoredRawOffer stored(String id, String description) {
    var offer = new RawJobOffer(id, "Java Dev", "Acme", null, "Kraków", true, "2026-07-04", "mid",
        description, null, null, null, null, null, "b2b", "https://justjoin.it/job-offer/" + id,
        "justjoin", "acme.com", null);

    return new StoredRawOffer(offer, "2026-07-04T10:00:00Z", "2026-07-04T11:00:00Z");
  }

  @Test
  void convertsHtmlDescriptionsAndKeepsTheRest() {
    repository.saveRawOffers(List.of(
        stored("a", "<p>About</p><ul><li>Java</li></ul>"),
        stored("b", "Already text"),
        stored("c", null)));

    var changed = migration.migrate();

    var offers = repository.readRawOffers();
    assertThat(changed).isEqualTo(1);
    assertThat(offers).extracting(s -> s.offer().description())
        .containsExactly("About\n\n- Java", "Already text", null);
    assertThat(offers.getFirst().offer().domain()).isEqualTo("acme.com");
    assertThat(offers.getFirst().detailsFetchedAt()).isEqualTo("2026-07-04T11:00:00Z");
    assertThat(tmp.resolve("raw-offers.json.bak")).exists();
  }

  @Test
  void secondRunChangesNothing() throws Exception {
    repository.saveRawOffers(List.of(stored("a", "<p>About</p>")));
    migration.migrate();
    Files.delete(tmp.resolve("raw-offers.json.bak"));

    var changed = migration.migrate();

    assertThat(changed).isZero();
    assertThat(tmp.resolve("raw-offers.json.bak")).doesNotExist();
  }

  @Test
  void emptyPoolIsFine() {
    assertThat(migration.migrate()).isZero();
  }
}
