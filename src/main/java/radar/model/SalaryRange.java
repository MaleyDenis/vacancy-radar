package radar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A salary range with currency. Used both on individual offers and in aggregated analytics.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SalaryRange(
    Integer min,
    Integer max,
    String currency
) {
}
