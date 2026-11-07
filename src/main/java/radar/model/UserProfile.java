package radar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * The user's preferences, used to score and filter offers. Persisted in {@code data/profile.json}.
 */
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

  /**
   * The profile without {@code minMatchScore} — what scoring depends on. {@code minMatchScore} only
   * filters what {@code GET /api/jobs} shows, so changing it must not make scores stale.
   */
  public UserProfile forScoring() {
    return new UserProfile(currentSkills, experienceYears, preferredType, preferredRemote, salaryMin,
        currency, contractType, locations, null);
  }
}
