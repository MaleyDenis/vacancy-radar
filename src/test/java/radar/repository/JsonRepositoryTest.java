package radar.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import radar.model.Analytics;
import radar.model.JobReport;
import radar.model.RawJobOffer;
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
  void jobsRoundTrip() {
    var offer = new RawJobOffer(
        "jj-1", "Java Dev", "Acme", "50", "Kraków", true, "2026-07-04", "mid",
        null, List.of("Java"), 12000, 18000, "PLN", "b2b",
        "https://justjoin.it/job-offer/jj-1", "justjoin", null);
    var report = new JobReport(
        offer, List.of("Java", "SQL"), List.of("Kafka"), "product", "mid",
        List.of("on-call"), List.of("remote"), "system design", 8,
        new SalaryRange(12000, 18000, "PLN"));

    repo.saveJobs("2026-07-04", List.of(report));
    assertThat(repo.loadJobs("2026-07-04")).containsExactly(report);
  }

  @Test
  void loadJobsReturnsEmptyWhenMissing() {
    assertThat(repo.loadJobs("2026-01-01")).isEmpty();
  }

  @Test
  void deleteAllJobsRemovesEverySnapshotsJobsFile() {
    var report = new JobReport(
        new RawJobOffer("jj-1", "Java Dev", "Acme", null, "Kraków", true, "2026-07-04", "mid",
            null, List.of("Java"), 12000, 18000, "PLN", "b2b", "url", "justjoin", null),
        List.of("Java"), List.of(), "product", "mid", List.of(), List.of(), "focus", 8,
        new SalaryRange(12000, 18000, "PLN"));
    repo.saveJobs("2026-07-04", List.of(report));
    repo.saveJobs("2026-07-05", List.of(report));
    // a snapshot with only analytics (no jobs.json) must not be counted
    repo.saveAnalytics("2026-07-06",
        new Analytics("2026-07-06", 0, Map.of(), List.of(), null, Map.of(), 0.0));

    var deleted = repo.deleteAllJobs();

    assertThat(deleted).isEqualTo(2);
    assertThat(repo.loadJobs("2026-07-04")).isEmpty();
    assertThat(repo.loadJobs("2026-07-05")).isEmpty();
  }

  @Test
  void deleteAllDataRemovesEverythingExceptProfileAndDotfiles() throws Exception {
    repo.writeProfile(new UserProfile(List.of(), 0, null, false, 0, "PLN", null, List.of(), 0));
    repo.saveRawOffers(List.of());
    repo.saveJobs("2026-07-04", List.of());
    repo.saveAnalytics("2026-07-05",
        new Analytics("2026-07-05", 0, Map.of(), List.of(), null, Map.of(), 0.0));
    var gitkeep = tmp.resolve("data").resolve(".gitkeep");
    Files.createFile(gitkeep);

    // top-level entries besides profile.json and .gitkeep: raw-offers.json + two date dirs
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
        new SalaryRange(10000, 30000, "PLN"), Map.of("product", 0.6), 66.7);

    repo.saveAnalytics("2026-07-04", analytics);
    assertThat(repo.loadAnalytics("2026-07-04")).isEqualTo(analytics);
  }

  @Test
  void saveCreatesDateDirectory() {
    repo.saveAnalytics("2026-07-04",
        new Analytics("2026-07-04", 1, Map.of(), List.of(), null, Map.of(), 0.0));
    assertThat(Files.isDirectory(tmp.resolve("data").resolve("2026-07-04"))).isTrue();
  }

  @Test
  void listDatesReturnsOnlyDateDirectoriesSorted() throws Exception {
    repo.saveAnalytics("2026-07-06",
        new Analytics("2026-07-06", 0, Map.of(), List.of(), null, Map.of(), 0.0));
    repo.saveAnalytics("2026-07-04",
        new Analytics("2026-07-04", 0, Map.of(), List.of(), null, Map.of(), 0.0));
    // noise that must be ignored
    Files.createDirectories(tmp.resolve("data").resolve("not-a-date"));
    repo.writeProfile(new UserProfile(List.of(), 0, null, false, 0, "PLN", null, List.of(), 0));

    assertThat(repo.listDates()).containsExactly("2026-07-04", "2026-07-06");
  }
}
