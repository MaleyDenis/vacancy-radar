package radar.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.models.messages.MessageCreateParams;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExtractedFactsTest {

  @Test
  void sdkAcceptsTheSchemaAndAllowsNulls() {
    var params = MessageCreateParams.builder().model(ClaudeClient.MODEL).maxTokens(1)
        .outputConfig(ExtractedFacts.class).addUserMessage("x").build();

    var schema = params.rawParams().outputConfig().orElseThrow().toString();
    assertThat(schema).contains("aiToolsUsage={type=[string, null]")
        .contains("polishRequired={type=[boolean, null]");
  }

  @Test
  void toExtractionNormalisesValues() {
    var facts = new ExtractedFacts(
        Optional.of(List.of("Java", " Spring Boot ", "Java", "")),
        Optional.of(List.of("Kafka", "Java")),
        Optional.of(List.of()),
        Optional.of("  "),
        Optional.of("Payments platform"),
        Optional.of("Senior"),
        Optional.of("b2"),
        Optional.of(false),
        Optional.of("partly remote"),
        Optional.empty(),
        Optional.of("HR call -> tech interview"),
        Optional.of("fintech"),
        Optional.of("Outsourcing"),
        Optional.of(true),
        Optional.of(List.of("Cursor")),
        Optional.of("preferred"));

    var extraction = facts.toExtraction();

    assertThat(extraction.skills()).containsExactly("Java", "Spring Boot");
    assertThat(extraction.niceToHaveSkills()).containsExactly("Kafka"); // no overlap with skills
    assertThat(extraction.requirements()).isNull();      // empty list -> null
    assertThat(extraction.responsibilities()).isNull();  // blank -> null
    assertThat(extraction.realSeniority()).isEqualTo("senior");
    assertThat(extraction.englishLevel()).isEqualTo("B2");
    assertThat(extraction.polishRequired()).isFalse();
    assertThat(extraction.workMode()).isNull();          // not an allowed value
    assertThat(extraction.benefits()).isNull();
    assertThat(extraction.companyType()).isEqualTo("outsourcing");
    assertThat(extraction.leadership()).isTrue();
    assertThat(extraction.aiTools()).containsExactly("Cursor");
    assertThat(extraction.aiToolsUsage()).isEqualTo("preferred");
  }
}
