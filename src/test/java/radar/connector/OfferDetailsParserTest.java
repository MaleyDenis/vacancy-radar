package radar.connector;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Parses the JobPosting JSON-LD out of an offer page — no network, uses a bundled fixture. */
class OfferDetailsParserTest {

  private final OfferDetailsParser parser = new OfferDetailsParser(new ObjectMapper());

  private String fixture() throws Exception {
    try (var in = getClass().getResourceAsStream("/offer-page.html")) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void extractsDescriptionAndDomainFromJobPostingLdJson() throws Exception {
    var details = parser.parse(fixture());

    // picks the JobPosting block, not the WebSite one
    assertThat(details.description()).contains("You will build data streams.");
    assertThat(details.description()).contains("Responsibilities you'll have"); // &apos; decoded
    assertThat(details.description()).contains("\n"); // newlines preserved
    // domain from hiringOrganization.sameAs, with www. stripped
    assertThat(details.domain()).isEqualTo("jit.team");
  }

  @Test
  void returnsNullsWhenNoJobPostingPresent() {
    var details = parser.parse("<html><head></head><body>no ld+json here</body></html>");

    assertThat(details.description()).isNull();
    assertThat(details.domain()).isNull();
  }
}
