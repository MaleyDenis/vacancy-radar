package radar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import radar.model.Analytics;
import radar.model.JobReport;
import radar.model.RawJobOffer;
import radar.repository.JsonRepository;

@ExtendWith(MockitoExtension.class)
class ReporterServiceTest {

  @Mock
  JsonRepository repository;

  @InjectMocks
  ReporterService reporter;

  private JobReport report(String company, boolean remote, String projectType, Integer salaryMin,
      List<String> keySkills) {
    var offer = new RawJobOffer("id-" + company + salaryMin, "Java Dev", company, null,
        "Kraków", remote, "2026-07-04", "senior", null, salaryMin,
        salaryMin == null ? null : salaryMin + 5000, "PLN", "b2b", "url", "justjoin", null);
    return new JobReport(offer, keySkills, "resp", "proj", List.of(), "senior", 7);
  }

  @Test
  void skillFrequencyCountsAndSortsDescending() {
    var reports = List.of(
        report("A", true, "product", 10000, List.of("Java", "Spring")),
        report("B", true, "product", 12000, List.of("Java", "AWS")),
        report("C", false, "outsourcing", 11000, List.of("Java", "Spring")),
        report("D", true, "product", 13000, List.of("Java")),
        report("E", false, "product", 9000, List.of("SQL")));

    var analytics = reporter.buildAnalytics("2026-07-04", reports);

    assertThat(analytics.totalScanned()).isEqualTo(5);
    assertThat(analytics.skillFrequency())
        .containsExactly(
            Map.entry("Java", 4),
            Map.entry("Spring", 2),
            Map.entry("AWS", 1),
            Map.entry("SQL", 1));
    // Java first (highest count); ties broken alphabetically
    assertThat(analytics.skillFrequency().keySet().iterator().next()).isEqualTo("Java");
  }

  @Test
  void remotePercentageIsBetweenZeroAndHundred() {
    var reports = List.of(
        report("A", true, "product", 10000, List.of("Java")),
        report("B", true, "product", 12000, List.of("Java")),
        report("C", false, "product", 11000, List.of("Java")),
        report("D", false, "product", 13000, List.of("Java")));

    var analytics = reporter.buildAnalytics("2026-07-04", reports);

    assertThat(analytics.remotePercentage()).isBetween(0.0, 100.0).isEqualTo(50.0);
  }

  @Test
  void topCompaniesRankedByOfferCount() {
    var reports = List.of(
        report("Acme", true, "product", 10000, List.of("Java")),
        report("Acme", true, "product", 12000, List.of("Java")),
        report("Acme", true, "product", 12000, List.of("Java")),
        report("Globex", false, "product", 11000, List.of("Java")),
        report("Initech", false, "product", 9000, List.of("Java")));

    var analytics = reporter.buildAnalytics("2026-07-04", reports);

    assertThat(analytics.topCompanies()).first().isEqualTo("Acme");
    assertThat(analytics.topCompanies()).containsExactlyInAnyOrder("Acme", "Globex", "Initech");
  }

  @Test
  void salaryDistributionUsesMinAndMaxOfSalaryMins() {
    var reports = List.of(
        report("A", true, "product", 9000, List.of("Java")),
        report("B", true, "product", 18000, List.of("Java")),
        report("C", false, "product", 12000, List.of("Java")));

    var dist = reporter.buildAnalytics("2026-07-04", reports).salaryDistribution();

    assertThat(dist.min()).isEqualTo(9000);
    assertThat(dist.max()).isEqualTo(18000);
    assertThat(dist.currency()).isEqualTo("PLN");
  }

  @Test
  void computeTrendsMergesSkillFrequencyAcrossSnapshots() {
    when(repository.listDates()).thenReturn(List.of("2026-07-04", "2026-07-05"));
    when(repository.loadAnalytics("2026-07-04")).thenReturn(analytics(Map.of("Java", 3, "Spring", 2)));
    when(repository.loadAnalytics("2026-07-05")).thenReturn(analytics(Map.of("Java", 5, "AWS", 1)));

    var trends = reporter.computeTrends();

    assertThat(trends).isNotEmpty();
    assertThat(trends).containsExactly(
        Map.entry("Java", 8),
        Map.entry("Spring", 2),
        Map.entry("AWS", 1));
  }

  @Test
  void computeTrendsSkipsDatesWithoutAnalytics() {
    when(repository.listDates()).thenReturn(List.of("2026-07-04", "2026-07-05"));
    when(repository.loadAnalytics("2026-07-04")).thenReturn(analytics(Map.of("Java", 2)));
    when(repository.loadAnalytics("2026-07-05"))
        .thenThrow(new IllegalStateException("File not found"));

    assertThat(reporter.computeTrends()).containsExactly(Map.entry("Java", 2));
  }

  private Analytics analytics(Map<String, Integer> skills) {
    return new Analytics("2026-07-04", 0, skills, List.of(), null, 0.0);
  }
}
