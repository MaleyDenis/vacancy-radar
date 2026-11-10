package radar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserProfile(
    List<String> currentSkills,
    Integer experienceYears,
    String preferredType,
    Boolean preferredRemote,
    Integer salaryMin,
    String currency,
    String contractType,
    List<String> locations,
    Integer minMatchScore
) {

  // minMatchScore only filters the list, so changing it must not make scores stale
  public UserProfile forScoring() {
    return new UserProfile(currentSkills, experienceYears, preferredType, preferredRemote, salaryMin,
        currency, contractType, locations, null);
  }
}
