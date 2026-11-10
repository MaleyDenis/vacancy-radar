package radar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SalaryRange(
    Integer min,
    Integer max,
    String currency
) {
}
