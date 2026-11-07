package radar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Claude's match of one offer against the profile, stored in {@code scores.json} keyed by offer id.
 *
 * @param matchScore  1 (poor) to 10 (excellent)
 * @param reason      one sentence on why
 * @param profileHash hash of the profile it was scored against — differs from the current one when
 *                    the profile changed since (score is stale)
 * @param scoredAt    ISO-8601 instant
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OfferScore(int matchScore, String reason, String profileHash, String scoredAt) {
}
