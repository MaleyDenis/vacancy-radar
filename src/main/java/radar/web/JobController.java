package radar.web;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import radar.model.JobView;
import radar.repository.JsonRepository;
import radar.service.ProfileHasher;

/** Jobs = raw offers joined with their extraction and score by offer id. */
@RestController
public class JobController {

  private final JsonRepository repository;
  private final ProfileHasher hasher;

  public JobController(JsonRepository repository, ProfileHasher hasher) {
    this.repository = repository;
    this.hasher = hasher;
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

  /** Deletes {@code scores.json}; extractions stay. Returns {@code {"deleted": N}} (files). */
  @DeleteMapping("/api/scores")
  public Map<String, Integer> deleteScores() {
    return Map.of("deleted", repository.deleteScores());
  }
}
