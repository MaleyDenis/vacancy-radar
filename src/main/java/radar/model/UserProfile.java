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
}
