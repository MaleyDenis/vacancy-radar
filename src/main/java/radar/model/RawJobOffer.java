package radar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * A raw job offer as scraped from a source connector (e.g. JustJoin), before enrichment.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RawJobOffer(
    String id,
    String title,
    String companyName,
    String companySize,
    String location,
    Boolean remote,
    String publishedAt,
    String experienceLevel,
    String description,
    List<String> skills,
    Integer salaryMin,
    Integer salaryMax,
    String currency,
    String contractType,
    String applyUrl,
    String sourceConnector
) {
}
