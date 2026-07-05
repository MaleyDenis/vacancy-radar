package radar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * A raw job offer as scraped from a source connector (e.g. JustJoin), before enrichment.
 *
 * <p>{@code description} and {@code domain} come from the offer's own page (the details step),
 * not the listing API — they are {@code null} on a freshly-scraped offer and filled in later.
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
    String sourceConnector,
    String domain
) {

  /**
   * Returns a copy with the detail-page fields filled in: the full {@code description} text and the
   * company {@code domain}. Everything else is preserved.
   */
  public RawJobOffer withDetails(String description, String domain) {
    return new RawJobOffer(id, title, companyName, companySize, location, remote, publishedAt,
        experienceLevel, description, skills, salaryMin, salaryMax, currency, contractType,
        applyUrl, sourceConnector, domain);
  }
}
