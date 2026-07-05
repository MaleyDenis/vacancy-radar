package radar.service;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * The structured JSON Claude returns for a single offer — every {@link radar.model.JobReport} field
 * except the raw offer, which the enricher attaches afterwards. Field descriptions are sent to the
 * model as part of the derived JSON schema, so keep them instructive.
 */
@JsonClassDescription("Analysis of a single job offer relative to the user's profile.")
public record EnrichmentResult(
    @JsonPropertyDescription(
        "Every technical skill or technology mentioned anywhere in the offer text "
            + "(languages, frameworks, tools, databases, cloud, methodologies).")
    List<String> skills,
    @JsonPropertyDescription(
        "The day-to-day responsibilities, summarised in your own words, a few short sentences.")
    String responsibilities,
    @JsonPropertyDescription(
        "What the project/product is about, summarised in your own words, a few short sentences.")
    String projectDescription,
    @JsonPropertyDescription("The concrete requirements the candidate must meet, as a list.")
    List<String> requirements,
    @JsonPropertyDescription("The real seniority the role demands: junior, mid, senior, or lead.")
    String realSeniority,
    @JsonPropertyDescription(
        "How well the offer matches the user's profile, from 1 (poor) to 10 (excellent).")
    int matchScore
) {
}
