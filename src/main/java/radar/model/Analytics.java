package radar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * Aggregated statistics across ALL scanned offers for a given date (before the match-score filter).
 * Persisted in {@code data/{date}/analytics.json}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Analytics(
    String date,
    int totalScanned,
    Map<String, Integer> skillFrequency,
    List<String> topCompanies,
    SalaryRange salaryDistribution,
    double remotePercentage
) {
}
