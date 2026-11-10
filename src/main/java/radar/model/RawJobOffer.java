package radar.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

// No skills: the listing's skill list is unreliable; Claude extracts them from the description
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

  public RawJobOffer withDetails(String description, String domain) {
    return new RawJobOffer(id, title, companyName, companySize, location, remote, publishedAt,
        experienceLevel, description, salaryMin, salaryMax, currency, salaryUnit, salaryGross,
        contractType, applyUrl, sourceConnector, domain, locations);
  }

  public RawJobOffer withLocations(List<String> locations) {
    return new RawJobOffer(id, title, companyName, companySize, location, remote, publishedAt,
        experienceLevel, description, salaryMin, salaryMax, currency, salaryUnit, salaryGross,
        contractType, applyUrl, sourceConnector, domain, locations);
  }

  @JsonIgnore
  public List<String> allLocations() {
    if (locations != null && !locations.isEmpty()) {
      return locations;
    }

    return location == null ? List.of() : List.of(location);
  }
}
