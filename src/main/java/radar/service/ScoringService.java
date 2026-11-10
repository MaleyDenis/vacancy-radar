package radar.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import radar.model.OfferScore;
import radar.model.RawJobOffer;
import radar.model.ScoringResult;
import radar.repository.JsonRepository;

@Service
public class ScoringService {

  private static final Logger log = LoggerFactory.getLogger(ScoringService.class);
  private static final int MIN_SCORE = 1;
  private static final int MAX_SCORE = 10;

  private final ClaudeClient claude;
  private final JsonRepository repository;
  private final ResumeHasher hasher;
  private final Progress progress;

  public ScoringService(ClaudeClient claude, JsonRepository repository, ResumeHasher hasher,
      Progress progress) {
    this.claude = claude;
    this.repository = repository;
    this.hasher = hasher;
    this.progress = progress;
  }

  public ScoringResult rescoreRecent(int days) {
    var resume = requireResume();
    var resumeHash = hasher.hash(resume);
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
          scores.put(offer.id(), score(offer, resume, resumeHash));
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

  public OfferScore scoreOne(String id) {
    var resume = requireResume();
    var offer = repository.readRawOffers().stream()
        .map(stored -> stored.offer())
        .filter(candidate -> candidate.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new NoSuchElementException("No offer " + id));
    if (!hasDescription(offer)) {
      throw new IllegalStateException("Offer " + id + " has no description yet");
    }
    var score = score(offer, resume, hasher.hash(resume));
    synchronized (this) {
      var scores = repository.readScores();
      scores.put(id, score);
      repository.saveScores(scores);
    }
    log.info("Scored {} on demand: {}", id, score.matchScore());
    return score;
  }

  private String requireResume() {
    var resume = repository.readResume();
    if (resume.isBlank()) {
      throw new IllegalStateException("Resume is empty: fill it in first");
    }
    return resume;
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

  OfferScore score(RawJobOffer offer, String resume, String resumeHash) {
    var result = claude.ask(prompt(offer, resume), ScoreResult.class);
    var value = Math.max(MIN_SCORE, Math.min(MAX_SCORE, result.matchScore()));
    return new OfferScore(value, result.reason(), resumeHash, Instant.now().toString());
  }

  String prompt(RawJobOffer offer, String resume) {
    return "You are a career advisor. Score how well this job offer matches the candidate's "
        + "resume on a scale of 1 to 10, and give a reason of at most 20 words.\n\n"
        + "=== CANDIDATE RESUME ===\n" + resume + "\n\n"
        + "=== JOB OFFER ===\n" + OfferPrompt.describe(offer);
  }
}
