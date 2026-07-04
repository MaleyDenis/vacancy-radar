package radar.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import radar.model.Analytics;
import radar.model.JobReport;
import radar.model.SalaryRange;
import radar.repository.JsonRepository;

/**
 * Aggregates {@link Analytics} across ALL scanned offers for a date (before any match-score filter),
 * and computes cross-snapshot skill trends. Does not scrape, call Claude, or filter by score.
 */
@Service
public class ReporterService {

  private static final int TOP_COMPANIES = 10;

  private final JsonRepository repository;

  public ReporterService(JsonRepository repository) {
    this.repository = repository;
  }

  /** Builds the analytics snapshot for a date from every enriched report (no score filtering). */
  public Analytics buildAnalytics(String date, List<JobReport> allReports) {
    int total = allReports.size();
    return new Analytics(
        date,
        total,
        skillFrequency(allReports),
        topCompanies(allReports),
        salaryDistribution(allReports),
        projectTypeBreakdown(allReports, total),
        remotePercentage(allReports, total));
  }

  /**
   * Merges {@code skillFrequency} across every saved snapshot, summing counts per skill, sorted by
   * total frequency descending.
   */
  public Map<String, Integer> computeTrends() {
    Map<String, Integer> merged = new HashMap<>();
    for (String date : repository.listDates()) {
      Analytics analytics;
      try {
        analytics = repository.loadAnalytics(date);
      } catch (RuntimeException e) {
        continue; // a date dir without analytics.json — skip it
      }
      if (analytics.skillFrequency() != null) {
        analytics.skillFrequency().forEach((skill, count) -> merged.merge(skill, count, Integer::sum));
      }
    }
    return sortByValueDesc(merged);
  }

  private Map<String, Integer> skillFrequency(List<JobReport> reports) {
    Map<String, Integer> counts = new HashMap<>();
    for (JobReport report : reports) {
      if (report.keySkills() != null) {
        for (String skill : report.keySkills()) {
          counts.merge(skill, 1, Integer::sum);
        }
      }
    }
    return sortByValueDesc(counts);
  }

  private List<String> topCompanies(List<JobReport> reports) {
    Map<String, Integer> counts = new HashMap<>();
    for (JobReport report : reports) {
      String company = report.rawJobOffer() == null ? null : report.rawJobOffer().companyName();
      if (company != null && !company.isBlank()) {
        counts.merge(company, 1, Integer::sum);
      }
    }
    return counts.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .limit(TOP_COMPANIES)
        .map(Map.Entry::getKey)
        .toList();
  }

  private SalaryRange salaryDistribution(List<JobReport> reports) {
    List<Integer> mins = reports.stream()
        .map(JobReport::salary)
        .filter(s -> s != null && s.min() != null)
        .map(SalaryRange::min)
        .sorted()
        .toList();
    if (mins.isEmpty()) {
      return new SalaryRange(null, null, null);
    }
    String currency = reports.stream()
        .map(JobReport::salary)
        .filter(s -> s != null && s.currency() != null)
        .map(SalaryRange::currency)
        .findFirst()
        .orElse(null);
    return new SalaryRange(mins.get(0), mins.get(mins.size() - 1), currency);
  }

  private Map<String, Double> projectTypeBreakdown(List<JobReport> reports, int total) {
    if (total == 0) {
      return new LinkedHashMap<>();
    }
    Map<String, Long> counts = reports.stream()
        .map(JobReport::projectType)
        .filter(t -> t != null && !t.isBlank())
        .collect(Collectors.groupingBy(t -> t, Collectors.counting()));
    LinkedHashMap<String, Double> breakdown = new LinkedHashMap<>();
    counts.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .forEach(e -> breakdown.put(e.getKey(), e.getValue() * 100.0 / total));
    return breakdown;
  }

  private double remotePercentage(List<JobReport> reports, int total) {
    if (total == 0) {
      return 0.0;
    }
    long remote = reports.stream()
        .map(JobReport::rawJobOffer)
        .filter(o -> o != null && Boolean.TRUE.equals(o.remote()))
        .count();
    return remote * 100.0 / total;
  }

  private static Map<String, Integer> sortByValueDesc(Map<String, Integer> counts) {
    LinkedHashMap<String, Integer> sorted = new LinkedHashMap<>();
    counts.entrySet().stream()
        .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
            .thenComparing(Map.Entry::getKey))
        .forEach(e -> sorted.put(e.getKey(), e.getValue()));
    return sorted;
  }
}
