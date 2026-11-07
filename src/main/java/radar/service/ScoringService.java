package radar.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import radar.model.OfferScore;
import radar.model.RawJobOffer;
import radar.model.ScoringResult;
import radar.model.UserProfile;
import radar.repository.JsonRepository;

/**
 * Scores offers against the profile with Claude (raw offer text + profile → score + reason) into
 * {@code scores.json}. A scan scores only offers that have no score yet; a profile change does not
 * re-score anything by itself (scores just become stale).
 */
@Service
public class ScoringService {

  private static final Logger log = LoggerFactory.getLogger(ScoringService.class);
  private static final int MIN_SCORE = 1;
  private static final int MAX_SCORE = 10;

  private final ClaudeClient claude;
  private final JsonRepository repository;
  private final ProfileHasher hasher;
  private final ObjectMapper objectMapper;

  public ScoringService(ClaudeClient claude, JsonRepository repository, ProfileHasher hasher,
      ObjectMapper objectMapper) {
    this.claude = claude;
    this.repository = repository;
    this.hasher = hasher;
    this.objectMapper = objectMapper;
  }

  /** @param limit optional cap on how many offers to score this run; {@code null} = no cap */
  public ScoringResult scoreMissing(Integer limit) {
    var profile = repository.readProfile(); // mandatory — throws if missing
    var profileHash = hasher.hash(profile);
    var scores = repository.readScores();
    var scored = 0;
    var pending = 0;
    try {
      for (var stored : repository.readRawOffers()) {
        var offer = stored.offer();
        if (scores.containsKey(offer.id())
            || offer.description() == null || offer.description().isBlank()) {
          continue;
        }
        pending++;
        if (limit != null && scored >= limit) {
          continue;
        }
        try {
          scores.put(offer.id(), score(offer, profile, profileHash));
          scored++;
        } catch (RuntimeException e) {
          log.warn("Scoring failed for {}: {}", offer.id(), e.toString());
        }
      }
    } finally {
      repository.saveScores(scores);
    }
    var remaining = pending - scored;
    log.info("Score: scored {}, remaining {} (limit={})", scored, remaining, limit);
    return new ScoringResult(scored, remaining);
  }

  OfferScore score(RawJobOffer offer, UserProfile profile, String profileHash) {
    var result = claude.ask(prompt(offer, profile), ScoreResult.class);
    var value = Math.max(MIN_SCORE, Math.min(MAX_SCORE, result.matchScore()));
    return new OfferScore(value, result.reason(), profileHash, Instant.now().toString());
  }

  String prompt(RawJobOffer offer, UserProfile profile) {
    return "You are a career advisor. Score how well this job offer matches the candidate's "
        + "profile on a scale of 1 to 10, and give a one-sentence reason.\n\n"
        + "=== CANDIDATE PROFILE (JSON) ===\n" + writeJson(profile) + "\n\n"
        + "=== JOB OFFER ===\n" + OfferPrompt.describe(offer);
  }

  private String writeJson(UserProfile profile) {
    try {
      return objectMapper.writeValueAsString(profile);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize profile for prompt", e);
    }
  }
}
