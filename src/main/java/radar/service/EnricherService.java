package radar.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import radar.model.JobReport;
import radar.model.RawJobOffer;
import radar.model.ScanProgress;
import radar.model.UserProfile;

/**
 * Enriches a {@link RawJobOffer} into a {@link JobReport} using Claude Haiku via the Anthropic Java
 * SDK's structured-output support: the request declares {@link EnrichmentResult} as the output type,
 * so Claude returns strictly-typed JSON matching that schema.
 *
 * <p>Only plain text is ever sent to Claude — never raw HTML (see {@code .claude/rules/claude-api.md}).
 * Filtering by {@code minMatchScore} happens upstream, not here.
 */
@Service
public class EnricherService {

  private static final String MODEL = "claude-haiku-4-5-20251001";
  private static final long MAX_TOKENS = 2048L;
  private static final int MIN_SCORE = 1;
  private static final int MAX_SCORE = 10;

  private final AnthropicClient client;
  private final ObjectMapper objectMapper;

  public EnricherService(AnthropicClient client, ObjectMapper objectMapper) {
    this.client = client;
    this.objectMapper = objectMapper;
  }

  /** Enriches a single offer against the user's profile. */
  public JobReport enrich(RawJobOffer offer, UserProfile profile) {
    var result = requestEnrichment(buildPrompt(offer, profile));
    return toReport(result, offer);
  }

  /**
   * Enriches every offer, emitting an {@code ENRICHING} progress event per offer. Returns all
   * reports (including low-scoring ones — filtering happens upstream).
   */
  public List<JobReport> enrichAll(List<RawJobOffer> offers, UserProfile profile,
      Consumer<ScanProgress> progressCallback) {
    var reports = new ArrayList<JobReport>();
    var total = offers.size();
    for (var i = 0; i < total; i++) {
      var offer = offers.get(i);
      reports.add(enrich(offer, profile));
      progressCallback.accept(new ScanProgress(
          ScanProgress.Type.ENRICHING, "Enriched " + offer.title(), i + 1, total, null));
    }
    return reports;
  }

  /**
   * Calls Claude Haiku with structured output and returns the parsed result. Package-private and
   * overridable so tests can exercise the surrounding logic without hitting the network.
   */
  EnrichmentResult requestEnrichment(String prompt) {
    var params = MessageCreateParams.builder()
        .model(MODEL)
        .maxTokens(MAX_TOKENS)
        .outputConfig(EnrichmentResult.class)
        .addUserMessage(prompt)
        .build();
    var message = client.messages().create(params);
    return message.content().stream()
        .flatMap(block -> block.text().stream())
        .map(text -> text.text())
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Claude returned no structured content"));
  }

  /** Builds the plain-text prompt. Package-private for testing (asserts no HTML leaks in). */
  String buildPrompt(RawJobOffer offer, UserProfile profile) {
    var sb = new StringBuilder();
    sb.append("You are a career advisor. Analyse the following job offer for this candidate ")
        .append("and score how well it matches their profile on a scale of 1 to 10.\n\n");
    sb.append("=== CANDIDATE PROFILE (JSON) ===\n").append(writeJson(profile)).append("\n\n");
    sb.append("=== JOB OFFER ===\n");
    append(sb, "Title", offer.title());
    append(sb, "Company", offer.companyName());
    append(sb, "Location", offer.location());
    append(sb, "Remote", offer.remote() == null ? null : offer.remote().toString());
    append(sb, "Experience level", offer.experienceLevel());
    append(sb, "Contract type", offer.contractType());
    if (offer.skills() != null && !offer.skills().isEmpty()) {
      append(sb, "Listed skills", String.join(", ", offer.skills()));
    }
    if (offer.salaryMin() != null || offer.salaryMax() != null) {
      append(sb, "Salary", offer.salaryMin() + " - " + offer.salaryMax() + " " + offer.currency());
    }
    if (offer.description() != null && !offer.description().isBlank()) {
      append(sb, "Description", offer.description());
    }
    return sb.toString();
  }

  /** Maps the model's result onto a JobReport, clamping the score to the valid 1-10 range. */
  JobReport toReport(EnrichmentResult r, RawJobOffer offer) {
    var score = Math.max(MIN_SCORE, Math.min(MAX_SCORE, r.matchScore()));
    return new JobReport(offer, r.keySkills(), r.niceToHave(), r.projectType(), r.realSeniority(),
        r.redFlags(), r.greenFlags(), r.interviewFocus(), score, r.salary());
  }

  private static void append(StringBuilder sb, String label, String value) {
    if (value != null && !value.isBlank()) {
      sb.append(label).append(": ").append(value).append('\n');
    }
  }

  private String writeJson(UserProfile profile) {
    try {
      return objectMapper.writeValueAsString(profile);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize profile for prompt", e);
    }
  }
}
