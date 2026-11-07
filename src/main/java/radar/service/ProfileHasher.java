package radar.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import radar.model.UserProfile;

/**
 * Stable SHA-256 of the profile's scoring fields (all but {@code minMatchScore}) — ties a score to the
 * profile it was made for.
 */
@Component
public class ProfileHasher {

  private final ObjectMapper objectMapper;

  public ProfileHasher(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String hash(UserProfile profile) {
    try {
      var bytes = objectMapper.writeValueAsBytes(profile.forScoring());
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to hash profile", e);
    }
  }
}
