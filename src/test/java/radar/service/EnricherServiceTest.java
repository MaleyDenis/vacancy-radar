package radar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.anthropic.client.AnthropicClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import radar.model.RawJobOffer;
import radar.model.ScanProgress;
import radar.model.SalaryRange;
import radar.model.UserProfile;

class EnricherServiceTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private final UserProfile profile = new UserProfile(
      List.of("Java", "Spring Boot"), 3, "B2B", true, 15000, "PLN", "B2B",
      List.of("Kraków", "Remote"), 6);

  private RawJobOffer offer(String id, String title) {
    return new RawJobOffer(id, title, "Acme", null, "Kraków", true, "2026-07-04", "senior",
        null, 18000, 26000, "PLN", "b2b",
        "https://justjoin.it/job-offer/" + id, "justjoin", null);
  }

  /**
   * An EnricherService whose Claude call is replaced by parsing a canned JSON string — exercising
   * the real prompt building, JSON→EnrichmentResult mapping, and toReport logic without the network.
   */
  private EnricherService withCannedJson(String json) {
    return new EnricherService(mock(AnthropicClient.class), mapper) {
      @Override
      EnrichmentResult requestEnrichment(String prompt) {
        try {
          return mapper.readValue(json, EnrichmentResult.class);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  @Test
  void validJsonProducesJobReportWithOfferAttached() {
    var json = """
        {
          "keySkills": ["Java", "Spring Boot"],
          "niceToHave": ["Kafka"],
          "projectType": "product",
          "realSeniority": "senior",
          "redFlags": ["on-call"],
          "greenFlags": ["remote", "clear stack"],
          "interviewFocus": "system design",
          "matchScore": 8,
          "salary": {"min": 18000, "max": 26000, "currency": "PLN"}
        }
        """;
    var o = offer("jj-1", "Senior Java Dev");

    var report = withCannedJson(json).enrich(o, profile);

    assertThat(report.rawJobOffer()).isSameAs(o);
    assertThat(report.keySkills()).containsExactly("Java", "Spring Boot");
    assertThat(report.niceToHave()).containsExactly("Kafka");
    assertThat(report.projectType()).isEqualTo("product");
    assertThat(report.realSeniority()).isEqualTo("senior");
    assertThat(report.redFlags()).containsExactly("on-call");
    assertThat(report.greenFlags()).containsExactly("remote", "clear stack");
    assertThat(report.interviewFocus()).isEqualTo("system design");
    assertThat(report.matchScore()).isEqualTo(8);
    assertThat(report.salary()).isEqualTo(new SalaryRange(18000, 26000, "PLN"));
  }

  @Test
  void matchScoreIsClampedIntoOneToTenRange() {
    var svc = new EnricherService(mock(AnthropicClient.class), mapper);
    var o = offer("jj-1", "Java Dev");

    var tooHigh = result(15);
    var tooLow = result(0);

    assertThat(svc.toReport(tooHigh, o).matchScore()).isEqualTo(10);
    assertThat(svc.toReport(tooLow, o).matchScore()).isEqualTo(1);
    assertThat(svc.toReport(result(7), o).matchScore()).isEqualTo(7);
  }

  @Test
  void enrichAllEmitsOneEnrichingProgressPerOffer() {
    var json = """
        {"keySkills":[],"niceToHave":[],"projectType":"product","realSeniority":"mid",
         "redFlags":[],"greenFlags":[],"interviewFocus":"basics","matchScore":5,
         "salary":{"min":10000,"max":15000,"currency":"PLN"}}
        """;
    var svc = withCannedJson(json);
    var offers = List.of(offer("a", "A"), offer("b", "B"), offer("c", "C"));
    var events = new ArrayList<ScanProgress>();

    var reports = svc.enrichAll(offers, profile, events::add);

    assertThat(reports).hasSize(3);
    assertThat(events).hasSize(3);
    assertThat(events).allMatch(e -> e.type() == ScanProgress.Type.ENRICHING);
    assertThat(events).extracting(ScanProgress::current).containsExactly(1, 2, 3);
    assertThat(events).allMatch(e -> e.total() == 3);
  }

  @Test
  void promptContainsProfileAndOfferAndNoHtml() {
    var svc = new EnricherService(mock(AnthropicClient.class), mapper);
    var o = offer("jj-1", "Senior Java Dev");

    var prompt = svc.buildPrompt(o, profile);

    assertThat(prompt).contains("Senior Java Dev").contains("Acme").contains("Kraków");
    assertThat(prompt).contains("Spring Boot"); // from profile JSON
    assertThat(prompt).contains("18000").contains("26000");
    assertThat(prompt).doesNotContain("<").doesNotContain(">"); // plain text only, never HTML
  }

  private EnrichmentResult result(int score) {
    return new EnrichmentResult(List.of("Java"), List.of(), "product", "mid", List.of(), List.of(),
        "focus", score, new SalaryRange(10000, 20000, "PLN"));
  }
}
