package radar.service;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import radar.model.SalaryRange;

/**
 * The structured JSON Claude Haiku returns for a single offer — every {@link radar.model.JobReport}
 * field except the raw offer, which the enricher attaches afterwards. Field descriptions are sent to
 * the model as part of the derived JSON schema, so keep them instructive.
 */
@JsonClassDescription("Analysis of a single job offer relative to the user's profile.")
public record EnrichmentResult(
    @JsonPropertyDescription("The core technologies the role genuinely requires day-to-day.")
    List<String> keySkills,
    @JsonPropertyDescription("Skills that are a bonus but not required.")
    List<String> niceToHave,
    @JsonPropertyDescription("Type of work, e.g. product, outsourcing, consulting, startup.")
    String projectType,
    @JsonPropertyDescription("The real seniority the role demands: junior, mid, senior, or lead.")
    String realSeniority,
    @JsonPropertyDescription("Concerning signals: vague scope, unrealistic stack, low pay, churn.")
    List<String> redFlags,
    @JsonPropertyDescription("Positive signals: clear tech, good pay, remote, strong team.")
    List<String> greenFlags,
    @JsonPropertyDescription("What the candidate should prepare for interviews at this role.")
    String interviewFocus,
    @JsonPropertyDescription("How well the offer matches the user's profile, from 1 (poor) to 10 (excellent).")
    int matchScore,
    @JsonPropertyDescription("The offer's salary range as understood from the posting.")
    SalaryRange salary
) {
}
