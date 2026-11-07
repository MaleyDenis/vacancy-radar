package radar.web;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import radar.model.JobView;
import radar.model.ScoringResult;
import radar.repository.JsonRepository;
import radar.service.ProfileHasher;
import radar.service.ScoringService;

/** Jobs = raw offers joined with their extraction and score by offer id. */
@RestController
public class JobController {

  private final JsonRepository repository;
  private final ProfileHasher hasher;
  private final ScoringService scoringService;
  private final int rescoreDays;

  public JobController(JsonRepository repository, ProfileHasher hasher,
      ScoringService scoringService, @Value("${radar.rescore.days:7}") int rescoreDays) {
    this.repository = repository;
    this.hasher = hasher;
    this.scoringService = scoringService;
    this.rescoreDays = rescoreDays;
  }

  /** Scored offers with {@code matchScore >= minMatchScore}, best first. */
  @GetMapping("/api/jobs")
  public List<JobView> jobs() {
    var profile = repository.readProfile();
    var minScore = profile.minMatchScore() == null ? 0 : profile.minMatchScore();
    var profileHash = hasher.hash(profile);
    var extractions = repository.readExtractions();
    var scores = repository.readScores();
    return repository.readRawOffers().stream()
        .map(stored -> stored.offer())
        .filter(offer -> scores.containsKey(offer.id()))
        .map(offer -> {
          var score = scores.get(offer.id());
          return new JobView(offer, extractions.get(offer.id()), score,
              !profileHash.equals(score.profileHash()));
        })
        .filter(job -> job.score().matchScore() >= minScore)
        .sorted(Comparator.comparingInt((JobView job) -> job.score().matchScore()).reversed())
        .toList();
  }

  /**
   * Re-scores offers published in the last {@code days} days against the current profile.
   * {@code days} defaults to {@code radar.rescore.days}. Returns scored / failed counts.
   */
  @PostMapping("/api/scores/rescore")
  public ScoringResult rescore(@RequestParam(required = false) Integer days) {
    return scoringService.rescoreRecent(days == null ? rescoreDays : days);
  }

  /** Deletes {@code scores.json}; extractions stay. Returns {@code {"deleted": N}} (files). */
  @DeleteMapping("/api/scores")
  public Map<String, Integer> deleteScores() {
    return Map.of("deleted", repository.deleteScores());
  }
}
