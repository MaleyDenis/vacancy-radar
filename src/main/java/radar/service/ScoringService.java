package radar.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
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
 * {@code scores.json}. Offers are scored one by one on demand ({@link #scoreOne}); scans do not
 * score. A profile change re-scores nothing by itself (scores just become stale): the card button
 * re-scores one offer, {@link #rescoreRecent(int)} (endpoint only, no UI) re-scores many.
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
  private final Progress progress;

  public ScoringService(ClaudeClient claude, JsonRepository repository, ProfileHasher hasher,
      ObjectMapper objectMapper, Progress progress) {
    this.claude = claude;
    this.repository = repository;
    this.hasher = hasher;
    this.objectMapper = objectMapper;
    this.progress = progress;
  }

  /**
   * Re-scores, against the current profile, every already scored offer published within the
   * last {@code days} days (by {@code publishedAt}), replacing existing scores. Offers that fail stay
   * as they were and are counted in {@code remaining}.
   */
  public ScoringResult rescoreRecent(int days) {
    var profile = repository.readProfile();
    var profileHash = hasher.hash(profile);
    var cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
    var scores = repository.readScores();
    var batch = repository.readRawOffers().stream()
        .map(stored -> stored.offer())
        .filter(offer -> scores.containsKey(offer.id()) && hasDescription(offer)
            && isPublishedAfter(offer, cutoff))
        .toList();
    progress.begin("rescore", batch.size());
    var scored = 0;
    var failed = 0;
    try {
      for (var offer : batch) {
        try {
          scores.put(offer.id(), score(offer, profile, profileHash));
          scored++;
        } catch (RuntimeException e) {
          failed++;
          log.warn("Rescoring failed for {}: {}", offer.id(), e.toString());
        }
        progress.advance();
      }
    } finally {
      repository.saveScores(scores);
    }
    log.info("Rescore: {} offers from the last {} days, {} failed", scored, days, failed);
    return new ScoringResult(scored, failed);
  }

  /**
   * Scores one offer now (the card button) and stores the score; safe to call for several offers
   * at once.
   *
   * @throws java.util.NoSuchElementException no such offer
   * @throws IllegalStateException            the offer has no description yet
   */
  public OfferScore scoreOne(String id) {
    var profile = repository.readProfile();
    var offer = repository.readRawOffers().stream()
        .map(stored -> stored.offer())
        .filter(candidate -> candidate.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new NoSuchElementException("No offer " + id));
    if (!hasDescription(offer)) {
      throw new IllegalStateException("Offer " + id + " has no description yet");
    }
    var score = score(offer, profile, hasher.hash(profile));
    synchronized (this) {
      var scores = repository.readScores();
      scores.put(id, score);
      repository.saveScores(scores);
    }
    log.info("Scored {} on demand: {}", id, score.matchScore());
    return score;
  }

  private static boolean hasDescription(RawJobOffer offer) {
    return offer.description() != null && !offer.description().isBlank();
  }

  private static boolean isPublishedAfter(RawJobOffer offer, Instant cutoff) {
    var published = parseInstant(offer.publishedAt());
    return published != null && !published.isBefore(cutoff);
  }

  private static Instant parseInstant(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(value);
    } catch (RuntimeException e) {
      return null;
    }
  }

  OfferScore score(RawJobOffer offer, UserProfile profile, String profileHash) {
    var result = claude.ask(prompt(offer, profile), ScoreResult.class);
    var value = Math.max(MIN_SCORE, Math.min(MAX_SCORE, result.matchScore()));
    return new OfferScore(value, result.reason(), profileHash, Instant.now().toString());
  }

  String prompt(RawJobOffer offer, UserProfile profile) {
    return "You are a career advisor. Score how well this job offer matches the candidate's "
        + "profile on a scale of 1 to 10, and give a reason of at most 20 words.\n\n"
        + "=== CANDIDATE PROFILE (JSON) ===\n" + writeJson(profile.forScoring()) + "\n\n"
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
