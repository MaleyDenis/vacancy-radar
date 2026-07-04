package radar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * An enriched job offer produced by the EnricherService from a {@link RawJobOffer} and the
 * {@link UserProfile}. {@code matchScore} is on a 1-10 scale.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobReport(
    RawJobOffer rawJobOffer,
    List<String> keySkills,
    List<String> niceToHave,
    String projectType,
    String realSeniority,
    List<String> redFlags,
    List<String> greenFlags,
    String interviewFocus,
    int matchScore,
    SalaryRange salary
) {

  /**
   * Returns a copy of this report with the given raw offer attached. Claude returns the enriched
   * fields without the original offer, so the enricher stitches it back on afterwards.
   */
  public JobReport withRawJobOffer(RawJobOffer offer) {
    return new JobReport(offer, keySkills, niceToHave, projectType, realSeniority,
        redFlags, greenFlags, interviewFocus, matchScore, salary);
  }
}
