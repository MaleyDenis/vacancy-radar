package radar.web;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import radar.model.JobReport;
import radar.repository.JsonRepository;

/**
 * Serves evaluated job reports, filtered by the current profile's {@code minMatchScore}. All reports
 * live in a single {@code jobs.json}; the endpoint filters on read.
 */
@RestController
public class JobController {

  private final JsonRepository repository;

  public JobController(JsonRepository repository) {
    this.repository = repository;
  }

  @GetMapping("/api/jobs")
  public List<JobReport> jobs() {
    var profile = repository.readProfile();
    var minScore = profile.minMatchScore() == null ? 0 : profile.minMatchScore();
    return repository.loadJobs().stream()
        .filter(report -> report.matchScore() >= minScore)
        .toList();
  }

  /**
   * Forgets all evaluations: deletes {@code jobs.json} and clears the per-offer evaluation marks so
   * the next scan re-scores. Returns {@code {"deleted": N}} (files removed).
   */
  @DeleteMapping("/api/jobs")
  public Map<String, Integer> deleteAll() {
    var deleted = repository.deleteAllJobs();
    repository.clearEvaluationMarks();
    return Map.of("deleted", deleted);
  }
}
