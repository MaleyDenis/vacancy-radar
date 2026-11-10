package radar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

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
