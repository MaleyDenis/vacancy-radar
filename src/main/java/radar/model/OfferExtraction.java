package radar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Profile-independent facts Claude extracts from an offer's text, once per offer. Stored in
 * {@code extractions.json} keyed by offer id. Any field is null when the offer text does not say.
 * Claude answers with {@code radar.service.ExtractedFacts}; see there for what each field means.
 *
 * @param realSeniority junior | mid | senior | lead
 * @param englishLevel  A2 | B1 | B2 | C1 | C2
 * @param workMode      remote | hybrid | office
 * @param companyType   product | outsourcing | bodyleasing | agency
 * @param aiToolsUsage  required | preferred | used
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OfferExtraction(
    List<String> skills,
    List<String> niceToHaveSkills,
    List<String> requirements,
    String responsibilities,
    String projectDescription,
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
