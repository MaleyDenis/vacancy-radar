package radar.web;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import radar.model.OfferExtraction;
import radar.model.OfferScore;
import radar.model.RawJobOffer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import radar.model.JobView;
import radar.repository.JsonRepository;
import radar.service.BackgroundRunner;
import radar.service.ProfileHasher;
import radar.service.ScoringService;

/** Jobs = raw offers joined with their extraction and score by offer id. */
@RestController
public class JobController {

  /** Scored by score (desc), then unscored by publishedAt (desc, ISO strings sort as dates). */
  private static final Comparator<JobView> BEST_FIRST = Comparator
      .comparing((JobView job) -> job.score() == null ? null : job.score().matchScore(),
          Comparator.nullsLast(Comparator.reverseOrder()))
      .thenComparing(job -> job.offer().publishedAt(),
          Comparator.nullsLast(Comparator.reverseOrder()));

  private final JsonRepository repository;
  private final ProfileHasher hasher;
  private final ScoringService scoringService;
  private final BackgroundRunner runner;
  private final int rescoreDays;

  public JobController(JsonRepository repository, ProfileHasher hasher,
      ScoringService scoringService, BackgroundRunner runner,
      @Value("${radar.rescore.days:7}") int rescoreDays) {
    this.repository = repository;
    this.hasher = hasher;
    this.scoringService = scoringService;
    this.runner = runner;
    this.rescoreDays = rescoreDays;
  }

  /**
   * Scored offers with {@code matchScore >= minMatchScore}, best first, then unscored offers
   * (score null), newest first.
   */
  @GetMapping("/api/jobs")
  public List<JobView> jobs() {
    var profile = repository.readProfile();
    var minScore = profile.minMatchScore() == null ? 0 : profile.minMatchScore();
    var profileHash = hasher.hash(profile);
    var extractions = repository.readExtractions();
    var scores = repository.readScores();
    return repository.readRawOffers().stream()
        .map(stored -> stored.offer())
        .map(offer -> view(offer, extractions.get(offer.id()), scores.get(offer.id()),
            profileHash))
        .filter(job -> job.score() == null || job.score().matchScore() >= minScore)
        .sorted(BEST_FIRST)
        .toList();
  }

  /**
   * Scores one offer now with Claude and returns its updated view. 404 unknown offer, 400 no
   * description yet, 409 while a scan or rescore runs, 502 when Claude fails.
   */
  @PostMapping("/api/jobs/{id}/score")
  public ResponseEntity<?> scoreOne(@PathVariable String id) {
    try {
      var score = runner.runNow(() -> scoringService.scoreOne(id));
      if (score.isEmpty()) {
        return Busy.conflict();
      }
      var profileHash = score.get().profileHash();
      var offer = repository.readRawOffers().stream()
          .map(stored -> stored.offer())
          .filter(candidate -> candidate.id().equals(id))
          .findFirst()
          .orElseThrow();
      return ResponseEntity.ok(
          view(offer, repository.readExtractions().get(id), score.get(), profileHash));
    } catch (NoSuchElementException e) {
      return error(HttpStatus.NOT_FOUND, e);
    } catch (IllegalStateException e) {
      return error(HttpStatus.BAD_REQUEST, e);
    } catch (RuntimeException e) {
      return error(HttpStatus.BAD_GATEWAY, e);
    }
  }

  private static ResponseEntity<Map<String, Object>> error(HttpStatus status, RuntimeException e) {
    var message = e.getMessage() == null ? e.toString() : e.getMessage();
    return ResponseEntity.status(status).body(Map.of("error", message));
  }

  private static JobView view(RawJobOffer offer, OfferExtraction extraction, OfferScore score,
      String profileHash) {
    var stale = score != null && !profileHash.equals(score.profileHash());
    return new JobView(offer, extraction, score, stale);
  }

  /**
   * Starts, in the background, re-scoring of offers published in the last {@code days} days against
   * the current profile ({@code days} defaults to {@code radar.rescore.days}). Result (scored /
   * failed) is read from {@code GET /api/status}. 409 when a task is already running.
   */
  @PostMapping("/api/scores/rescore")
  public ResponseEntity<Map<String, Object>> rescore(
      @RequestParam(required = false) Integer days) {
    var window = days == null ? rescoreDays : days;
    var started = runner.start("rescore", () -> scoringService.rescoreRecent(window));
    return started ? Busy.started() : Busy.conflict();
  }

  /** Deletes {@code scores.json}; extractions stay. Returns {@code {"deleted": N}} (files). */
  @DeleteMapping("/api/scores")
  public ResponseEntity<Map<String, Object>> deleteScores() {
    if (runner.isRunning()) {
      return Busy.conflict();
    }
    return ResponseEntity.ok(Map.of("deleted", repository.deleteScores()));
  }
}
