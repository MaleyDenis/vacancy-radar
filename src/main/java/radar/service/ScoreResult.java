package radar.service;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

// field descriptions go into the JSON schema Claude sees
@JsonClassDescription("How well a job offer matches the candidate's resume.")
public record ScoreResult(
    @JsonPropertyDescription("Match from 1 (poor) to 10 (excellent).")
    int matchScore,
    @JsonPropertyDescription(
        "One short sentence, at most 20 words: the main fit and the main gap. No resume recap.")
    String reason
) {
}
