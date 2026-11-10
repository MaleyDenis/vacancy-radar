package radar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OfferScore(int matchScore, String reason, String profileHash, String scoredAt) {
}
