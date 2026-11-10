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
import radar.model.JobPage;
import radar.model.JobView;
import radar.repository.JsonRepository;
import radar.service.BackgroundRunner;
import radar.service.ProfileHasher;
import radar.service.ScoringService;

@RestController
public class JobController {

  private static final int MAX_PAGE_SIZE = 200;

  // ISO strings sort as dates
  private static final Comparator<JobView> NEWEST = Comparator.comparing(
      (JobView job) -> job.offer().publishedAt(), Comparator.nullsLast(Comparator.reverseOrder()));
  private static final Comparator<JobView> BEST = Comparator.comparing(
      (JobView job) -> job.score() == null ? null : job.score().matchScore(),
      Comparator.nullsLast(Comparator.reverseOrder()));
  private static final Comparator<JobView> NEWEST_DAY = Comparator.comparing(
      (JobView job) -> day(job.offer().publishedAt()),
      Comparator.nullsLast(Comparator.reverseOrder()));

  private static final Map<String, Comparator<JobView>> SORTS = Map.of(
      "score", BEST.thenComparing(NEWEST),
      "date", NEWEST,
      "date_score", NEWEST_DAY.thenComparing(BEST).thenComparing(NEWEST));

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

  @GetMapping("/api/jobs")
  public ResponseEntity<?> jobs(
      @RequestParam(required = false) Integer minScore,
      @RequestParam(defaultValue = "false") boolean scoredOnly,
      @RequestParam(defaultValue = "score") String sort,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "50") int size) {
    var order = SORTS.get(sort);
    if (order == null || page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
      return ResponseEntity.badRequest().body(Map.of("error",
          "sort must be one of " + SORTS.keySet() + ", page >= 1, 1 <= size <= " + MAX_PAGE_SIZE));
    }
    var all = allJobs();
    var staleCount = (int) all.stream().filter(job -> job.stale()).count();
    var matching = all.stream()
        .filter(job -> !scoredOnly || job.score() != null)
        .filter(job -> minScore == null || job.score() == null
            || job.score().matchScore() >= minScore)
        .sorted(order)
        .toList();
    var from = Math.min((page - 1) * size, matching.size());
    var to = Math.min(from + size, matching.size());
    var items = matching.subList(from, to).stream().map(job -> forCard(job)).toList();
    return ResponseEntity.ok(new JobPage(items, matching.size(), page, size, staleCount));
  }

  @GetMapping("/api/jobs/{id}")
  public ResponseEntity<JobView> job(@PathVariable String id) {
    return allJobs().stream()
        .filter(job -> job.offer().id().equals(id))
        .findFirst()
        .map(job -> ResponseEntity.ok(job))
        .orElse(ResponseEntity.notFound().build());
  }

  private List<JobView> allJobs() {
    var profileHash = hasher.hash(repository.readProfile());
    var extractions = repository.readExtractions();
    var scores = repository.readScores();
    return repository.readRawOffers().stream()
        .map(stored -> stored.offer())
        .map(offer -> view(offer, extractions.get(offer.id()), scores.get(offer.id()),
            profileHash))
        .toList();
  }

  private static JobView forCard(JobView job) {
    var offer = job.offer().withDetails(null, job.offer().domain());
    return new JobView(offer, job.extraction(), job.score(), job.stale());
  }

  private static String day(String publishedAt) {
    return publishedAt == null || publishedAt.length() < 10 ? publishedAt
        : publishedAt.substring(0, 10);
  }

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

  @PostMapping("/api/scores/rescore")
  public ResponseEntity<Map<String, Object>> rescore(
      @RequestParam(required = false) Integer days) {
    var window = days == null ? rescoreDays : days;
    var started = runner.start("rescore", () -> scoringService.rescoreRecent(window));
    return started ? Busy.started() : Busy.conflict();
  }

  @DeleteMapping("/api/scores")
  public ResponseEntity<Map<String, Object>> deleteScores() {
    if (runner.isRunning()) {
      return Busy.conflict();
    }
    return ResponseEntity.ok(Map.of("deleted", repository.deleteScores()));
  }
}
