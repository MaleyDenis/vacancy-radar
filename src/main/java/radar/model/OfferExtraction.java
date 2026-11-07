package radar.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Profile-independent facts Claude extracts from an offer's text, once per offer. Stored in
 * {@code extractions.json} keyed by offer id. Field descriptions are sent to Claude as the schema.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonClassDescription("Facts extracted from a single job offer.")
public record OfferExtraction(
    @JsonPropertyDescription(
        "Every technical skill or technology mentioned anywhere in the offer text "
            + "(languages, frameworks, tools, databases, cloud, methodologies).")
    List<String> skills,
    @JsonPropertyDescription("The concrete requirements the candidate must meet, as a list.")
    List<String> requirements,
    @JsonPropertyDescription(
        "The day-to-day responsibilities, summarised in your own words, a few short sentences.")
    String responsibilities,
    @JsonPropertyDescription(
        "What the project/product is about, summarised in your own words, a few short sentences.")
    String projectDescription,
    @JsonPropertyDescription("The real seniority the role demands: junior, mid, senior, or lead.")
    String realSeniority
) {
}
