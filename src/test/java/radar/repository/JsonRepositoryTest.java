package radar.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import radar.model.Analytics;
import radar.model.OfferExtraction;
import radar.model.OfferScore;
import radar.model.SalaryRange;
import radar.model.UserProfile;

class JsonRepositoryTest {

  @TempDir
  Path tmp;

  private JsonRepository repo;

  @BeforeEach
  void setUp() {
    // point the repo at a fresh sub-dir that does NOT exist yet, to prove auto-creation
    repo = new JsonRepository(new ObjectMapper(), tmp.resolve("data").toString());
  }

  @Test
  void createsDataDirIfMissing() {
    assertThat(Files.isDirectory(tmp.resolve("data"))).isTrue();
  }

  @Test
  void profileRoundTrips() {
    var profile = new UserProfile(
        List.of("Java", "Spring"), 3, "B2B", true, 15000, "PLN", "B2B",
        List.of("Warsaw", "Remote"), 6);
    repo.writeProfile(profile);
    assertThat(repo.readProfile()).isEqualTo(profile);
  }

  @Test
  void readProfileThrowsWhenMissing() {
    assertThatThrownBy(() -> repo.readProfile()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void extractionsRoundTripKeyedByOfferId() {
    assertThat(repo.readExtractions()).isEmpty();
    var map = new LinkedHashMap<String, OfferExtraction>();
    map.put("b", new OfferExtraction(List.of("Java"), null, List.of(), "r", "p", "mid", "B2", true, "remote", null, null, null, "product", null, List.of("Cursor"), "used"));
    map.put("a", new OfferExtraction(List.of(), null, List.of("5y"), null, null, "senior", null, null, null, null, null, null, null, null, null, null));
    repo.saveExtractions(map);
    var read = repo.readExtractions();
    assertThat(read).containsExactlyEntriesOf(map);
  }

  @Test
  void scoresRoundTripAndDelete() {
    assertThat(repo.readScores()).isEmpty();
    var map = new LinkedHashMap<String, OfferScore>();
    map.put("a", new OfferScore(8, "fits", "h1", "2026-11-07T00:00:00Z"));
    repo.saveScores(map);
    assertThat(repo.readScores()).containsExactlyEntriesOf(map);

    assertThat(repo.deleteScores()).isEqualTo(1);
    assertThat(repo.readScores()).isEmpty();
    assertThat(repo.deleteScores()).isZero();
  }

  @Test
  void deleteAllDataRemovesEverythingExceptProfileAndDotfiles() throws Exception {
    repo.writeProfile(new UserProfile(List.of(), 0, null, false, 0, "PLN", null, List.of(), 0));
    repo.saveRawOffers(List.of());
    repo.saveScores(Map.of());
    repo.saveAnalytics("2026-07-05",
        new Analytics("2026-07-05", 0, Map.of(), List.of(), null, 0.0));
    var gitkeep = tmp.resolve("data").resolve(".gitkeep");
    Files.createFile(gitkeep);

    // top-level entries besides profile.json and .gitkeep: raw-offers.json, scores.json, one date dir
    var removed = repo.deleteAllData();

    assertThat(removed).isEqualTo(3);
    assertThat(repo.readProfile()).isNotNull();
    assertThat(Files.exists(gitkeep)).isTrue(); // dotfiles are preserved
    assertThat(repo.readRawOffers()).isEmpty();
    assertThat(repo.listDates()).isEmpty();
  }

  @Test
  void analyticsRoundTrip() {
    var analytics = new Analytics(
        "2026-07-04", 42, Map.of("Java", 30, "Spring", 20), List.of("Acme"),
        new SalaryRange(10000, 30000, "PLN"), 66.7);

    repo.saveAnalytics("2026-07-04", analytics);
    assertThat(repo.loadAnalytics("2026-07-04")).isEqualTo(analytics);
  }

  @Test
  void saveCreatesDateDirectory() {
    repo.saveAnalytics("2026-07-04",
        new Analytics("2026-07-04", 1, Map.of(), List.of(), null, 0.0));
    assertThat(Files.isDirectory(tmp.resolve("data").resolve("2026-07-04"))).isTrue();
  }

  @Test
  void listDatesReturnsOnlyDateDirectoriesSorted() throws Exception {
    repo.saveAnalytics("2026-07-06",
        new Analytics("2026-07-06", 0, Map.of(), List.of(), null, 0.0));
    repo.saveAnalytics("2026-07-04",
        new Analytics("2026-07-04", 0, Map.of(), List.of(), null, 0.0));
    // noise that must be ignored
    Files.createDirectories(tmp.resolve("data").resolve("not-a-date"));
    repo.writeProfile(new UserProfile(List.of(), 0, null, false, 0, "PLN", null, List.of(), 0));

    assertThat(repo.listDates()).containsExactly("2026-07-04", "2026-07-06");
  }
}
