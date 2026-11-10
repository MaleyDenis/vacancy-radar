package radar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OfferExtraction(
    List<String> skills,
    List<String> niceToHaveSkills,
    List<String> requirements,
    List<String> responsibilities,
    List<String> projectDescription,
    String realSeniority,
    String englishLevel,
    Boolean polishRequired,
    String workMode,
    List<String> benefits,
    String recruitmentSteps,
    String industry,
    String companyType,
    Boolean leadership,
    List<String> aiTools,
    String aiToolsUsage
) {
}
