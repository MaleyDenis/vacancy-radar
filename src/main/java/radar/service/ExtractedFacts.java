package radar.service;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import radar.model.OfferExtraction;

/**
 * Claude's structured answer for extraction. {@code Optional} fields become nullable in the JSON
 * schema (Claude answers null when the text does not say). Enum-like fields are strings with the
 * allowed values in their description (the SDK rejects nullable enums); {@link #toExtraction()}
 * drops values outside the allowed set.
 */
@JsonClassDescription("Facts extracted from a single job offer. Use null when the text does not say.")
public record ExtractedFacts(
    @JsonPropertyDescription("Required (must-have) technologies only: languages, frameworks, "
        + "databases, cloud, tools. Canonical names without versions ('Java', 'Spring Boot', "
        + "'PostgreSQL'). No soft skills, methodologies or AI coding tools.")
    Optional<List<String>> skills,
    @JsonPropertyDescription("Nice-to-have technologies ('a plus', 'nice to have'), same naming "
        + "rules as skills. Never repeat a skill listed in skills.")
    Optional<List<String>> niceToHaveSkills,
    @JsonPropertyDescription("The concrete requirements the candidate must meet, as a list.")
    Optional<List<String>> requirements,
    @JsonPropertyDescription(
        "The day-to-day responsibilities, summarised in your own words, a few short sentences.")
    Optional<String> responsibilities,
    @JsonPropertyDescription(
        "What the project/product is about, summarised in your own words, a few short sentences.")
    Optional<String> projectDescription,
    @JsonPropertyDescription("The real seniority the role demands. One of: junior, mid, senior, lead.")
    Optional<String> realSeniority,
    @JsonPropertyDescription("Required English level. One of: A2, B1, B2, C1, C2. "
        + "Null if English is not mentioned.")
    Optional<String> englishLevel,
    @JsonPropertyDescription("True if Polish is required, false if the text says it is not needed. "
        + "Null if Polish is not mentioned.")
    Optional<Boolean> polishRequired,
    @JsonPropertyDescription("One of: remote, hybrid, office.")
    Optional<String> workMode,
    @JsonPropertyDescription("Benefits offered, as short names ('Multisport', 'private medical "
        + "care', 'paid leave on B2B').")
    Optional<List<String>> benefits,
    @JsonPropertyDescription("The recruitment process steps, in your own words, short "
        + "('HR call -> technical interview with live coding -> meeting with the client').")
    Optional<String> recruitmentSteps,
    @JsonPropertyDescription("The business domain / industry, short ('fintech', 'e-commerce', "
        + "'healthcare').")
    Optional<String> industry,
    @JsonPropertyDescription("Who the employer is. One of: product (own product), outsourcing "
        + "(builds projects for clients), bodyleasing (the candidate is placed at a client), agency "
        + "(recruitment agency, real employer hidden).")
    Optional<String> companyType,
    @JsonPropertyDescription("True if the role expects mentoring or leading people.")
    Optional<Boolean> leadership,
    @JsonPropertyDescription("AI coding tools mentioned for the developer's own work "
        + "('GitHub Copilot', 'Cursor', 'Claude Code', 'ChatGPT'). Canonical names.")
    Optional<List<String>> aiTools,
    @JsonPropertyDescription("How AI coding tools are expected. One of: required, preferred (a "
        + "plus), used (the team just uses them). Null if no AI coding tools are mentioned.")
    Optional<String> aiToolsUsage
) {

  private static final Set<String> SENIORITY = Set.of("junior", "mid", "senior", "lead");
  private static final Set<String> ENGLISH = Set.of("A2", "B1", "B2", "C1", "C2");
  private static final Set<String> WORK_MODE = Set.of("remote", "hybrid", "office");
  private static final Set<String> COMPANY_TYPE =
      Set.of("product", "outsourcing", "bodyleasing", "agency");
  private static final Set<String> AI_USAGE = Set.of("required", "preferred", "used");

  /** The stored form: Optionals unwrapped to nulls, enum-like values checked. */
  public OfferExtraction toExtraction() {
    var must = list(skills);
    var nice = list(niceToHaveSkills);
    if (must != null && nice != null) {
      nice = nice.stream().filter(skill -> !must.contains(skill)).toList();
    }
    return new OfferExtraction(must, nice, list(requirements), text(responsibilities),
        text(projectDescription), oneOf(realSeniority, SENIORITY, false),
        oneOf(englishLevel, ENGLISH, true), polishRequired.orElse(null),
        oneOf(workMode, WORK_MODE, false), list(benefits), text(recruitmentSteps), text(industry),
        oneOf(companyType, COMPANY_TYPE, false), leadership.orElse(null), list(aiTools),
        oneOf(aiToolsUsage, AI_USAGE, false));
  }

  private static List<String> list(Optional<List<String>> value) {
    return value.map(items -> items.stream().filter(item -> item != null && !item.isBlank())
        .map(String::strip).distinct().toList()).filter(items -> !items.isEmpty()).orElse(null);
  }

  private static String text(Optional<String> value) {
    return value.map(String::strip).filter(s -> !s.isEmpty()).orElse(null);
  }

  private static String oneOf(Optional<String> value, Set<String> allowed, boolean upper) {
    return value.map(String::strip)
        .map(s -> upper ? s.toUpperCase(Locale.ROOT) : s.toLowerCase(Locale.ROOT))
        .filter(allowed::contains)
        .orElse(null);
  }
}
