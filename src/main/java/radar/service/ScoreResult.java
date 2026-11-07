package radar.service;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** Claude's structured answer for scoring one offer. Descriptions are sent as the schema. */
@JsonClassDescription("How well a job offer matches the candidate's profile.")
public record ScoreResult(
    @JsonPropertyDescription("Match from 1 (poor) to 10 (excellent).")
    int matchScore,
    @JsonPropertyDescription("One short sentence explaining the score: main fits and gaps.")
    String reason
) {
}
