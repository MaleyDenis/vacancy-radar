package radar.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import radar.model.EvaluationResult;
import radar.model.JobReport;
import radar.model.UserProfile;
import radar.repository.JsonRepository;

/**
 * Scores pooled offers against the user profile with Claude and writes every report to
 * {@code jobs.json}. Incremental via a per-offer marker: an offer is re-scored only when its
 * {@code evaluatedProfileHash} differs from the current profile hash — so a profile change (new hash)
 * forces a full re-evaluation, while an unchanged profile only scores newly-scraped offers. Reports
 * for already-scored offers are carried over from the existing {@code jobs.json}.
 *
 * <p>The profile is mandatory — {@link JsonRepository#readProfile()} throws if it is missing.
 */
@Service
public class EvaluationService {

  private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

  private final EnricherService enricherService;
  private final JsonRepository repository;
  private final ObjectMapper objectMapper;

  public EvaluationService(EnricherService enricherService, JsonRepository repository,
      ObjectMapper objectMapper) {
    this.enricherService = enricherService;
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  /**
   * @param limit optional cap on how many offers to score this run (offers not yet scored under the
   *              current profile); {@code null} = no cap.
   */
  public EvaluationResult evaluate(Integer limit) {
    var profile = repository.readProfile(); // mandatory — throws if missing
    var profileHash = hash(profile);

    // Existing reports, so we can carry over ones for offers already scored under this profile.
    var existingByOffer = new HashMap<String, JobReport>();
    for (var report : repository.loadJobs()) {
      if (report.rawJobOffer() != null) {
        existingByOffer.put(report.rawJobOffer().id(), report);
      }
    }

    var pool = repository.readRawOffers();
    var updatedPool = new ArrayList<>(pool);
    var reports = new ArrayList<JobReport>();
    var evaluated = 0;

    for (var i = 0; i < updatedPool.size(); i++) {
      var stored = updatedPool.get(i);
      var offer = stored.offer();
      if (stored.isEvaluatedFor(profileHash)) {
        var existing = existingByOffer.get(offer.id());
        if (existing != null) {
          reports.add(existing);
        }
        continue;
      }
      var description = offer.description();
      if (description == null || description.isBlank()) {
        continue; // no description yet — evaluate once the details step has filled it
      }
      if (limit != null && evaluated >= limit) {
        continue; // leave for a later run — stays unmarked
      }
      reports.add(enricherService.enrich(offer, profile));
      updatedPool.set(i, stored.withEvaluatedProfileHash(profileHash));
      evaluated++;
    }

    repository.saveRawOffers(updatedPool);
    repository.saveJobs(reports);

    log.info("Evaluate: scored {}, reports {} (profileHash {})", evaluated, reports.size(),
        profileHash);
    return new EvaluationResult(evaluated, reports.size());
  }

  /** Stable SHA-256 of the profile's JSON — the key that ties a report to the profile it used. */
  private String hash(UserProfile profile) {
    try {
      var bytes = objectMapper.writeValueAsBytes(profile);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to hash profile", e);
    }
  }
}
