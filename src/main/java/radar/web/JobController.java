package radar.web;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import radar.model.JobReport;
import radar.repository.JsonRepository;

/**
 * Serves saved job reports, filtered by the current profile's {@code minMatchScore}. Defaults to the
 * most recent snapshot when no date is given.
 */
@RestController
public class JobController {

  private final JsonRepository repository;

  public JobController(JsonRepository repository) {
    this.repository = repository;
  }

  @GetMapping("/api/jobs")
  public List<JobReport> jobs(@RequestParam(required = false) String date) {
    var target = date != null ? date : latestDate();
    if (target == null) {
      return List.of();
    }
    var profile = repository.readProfile();
    var minScore = profile.minMatchScore() == null ? 0 : profile.minMatchScore();
    return repository.loadJobs(target).stream()
        .filter(report -> report.matchScore() >= minScore)
        .toList();
  }

  private String latestDate() {
    var dates = repository.listDates(); // ascending
    return dates.isEmpty() ? null : dates.get(dates.size() - 1);
  }
}
