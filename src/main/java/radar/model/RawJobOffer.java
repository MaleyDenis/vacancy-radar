package radar.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * A raw job offer as scraped from a source connector (e.g. JustJoin).
 *
 * <p>{@code description} and {@code domain} come from the offer's own page (the details step),
 * not the listing API — they are {@code null} on a freshly-scraped offer and filled in later.
 * Salary is the offer's original (non-converted) amount; {@code salaryUnit} is the period it is
 * quoted per ({@code hour}, {@code day}, {@code month}, {@code year}) and {@code salaryGross} whether it
 * is gross ({@code true}) or net ({@code false}).
 * {@code location} is the offer's main city; {@code locations} lists every city it is open in
 * (JustJoin "multilocation": one offer, several cities — not duplicates). {@code locations} is null
 * for offers stored before it existed; use {@link #allLocations()}.
 * Skills are intentionally not carried here: the listing's skill list is unreliable, so skills are
 * extracted by Claude from the description (see {@code OfferExtraction.skills}).
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
    Integer salaryMin,
    Integer salaryMax,
    String currency,
    String salaryUnit,
    Boolean salaryGross,
    String contractType,
    String applyUrl,
    String sourceConnector,
    String domain,
    List<String> locations
) {

  /**
   * Returns a copy with the detail-page fields filled in: the full {@code description} text and the
   * company {@code domain}. Everything else is preserved.
   */
  public RawJobOffer withDetails(String description, String domain) {
    return new RawJobOffer(id, title, companyName, companySize, location, remote, publishedAt,
        experienceLevel, description, salaryMin, salaryMax, currency, salaryUnit, salaryGross,
        contractType, applyUrl, sourceConnector, domain, locations);
  }

  /** Returns a copy with {@code locations} (every city the offer is open in) replaced. */
  public RawJobOffer withLocations(List<String> locations) {
    return new RawJobOffer(id, title, companyName, companySize, location, remote, publishedAt,
        experienceLevel, description, salaryMin, salaryMax, currency, salaryUnit, salaryGross,
        contractType, applyUrl, sourceConnector, domain, locations);
  }

  /** Every city of the offer: {@code locations}, or just {@code location} for older entries. */
  @JsonIgnore
  public List<String> allLocations() {
    if (locations != null && !locations.isEmpty()) {
      return locations;
    }
    return location == null ? List.of() : List.of(location);
  }
}
