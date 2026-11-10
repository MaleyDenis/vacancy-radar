package radar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JobReport(
    RawJobOffer rawJobOffer,
    List<String> skills,
    String responsibilities,
    String projectDescription,
    List<String> requirements,
    String realSeniority,
    int matchScore
) {

  // Claude returns the enriched fields without the original offer
  public JobReport withRawJobOffer(RawJobOffer offer) {
    return new JobReport(offer, skills, responsibilities, projectDescription, requirements,
        realSeniority, matchScore);
  }
}
