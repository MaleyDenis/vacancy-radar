package radar.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import radar.model.RawJobOffer;

class TextSearchTest {

  private static RawJobOffer offer(String title, String company, String description) {
    return new RawJobOffer("id", title, company, null, null, null, null, null, description,
        null, null, null, null, null, null, null, "justjoin", null, null);
  }

  @Test
  void termsAreFoldedAndSplitOnSpaces() {
    assertThat(TextSearch.terms("  Kafka  ŁÓDŹ kafka ")).containsExactly("kafka", "lodz");
    assertThat(TextSearch.terms(null)).isEmpty();
    assertThat(TextSearch.terms("  ")).isEmpty();
  }

  @Test
  void everyTermMustMatchTitleCompanyOrDescription() {
    var offer = offer("Senior Java Developer", "Acme", "We use Kafka.\n- Docker");

    assertThat(TextSearch.matches(offer, TextSearch.terms("java kafka"))).isTrue();
    assertThat(TextSearch.matches(offer, TextSearch.terms("acme docker"))).isTrue();
    assertThat(TextSearch.matches(offer, TextSearch.terms("kafka python"))).isFalse();
  }

  @Test
  void polishLettersMatchTheirLatinForms() {
    var offer = offer("Dev", "Firma", "Praca we Wrocławiu, język polski");

    assertThat(TextSearch.matches(offer, TextSearch.terms("wroclaw jezyk"))).isTrue();
    assertThat(TextSearch.matches(offer, TextSearch.terms("Wrocław"))).isTrue();
  }

  @Test
  void matchesWithoutDescription() {
    assertThat(TextSearch.matches(offer("Java Dev", null, null), List.of("java"))).isTrue();
  }

  @Test
  void snippetShowsTheDescriptionAroundTheFirstHit() {
    var description = "intro ".repeat(30) + "We build payments on Kafka streams. " + "tail ".repeat(60);

    var snippet = TextSearch.snippet(offer("Dev", "Acme", description), List.of("kafka"));

    assertThat(snippet).startsWith("… ").endsWith(" …").contains("payments on Kafka streams.");
    assertThat(snippet).doesNotContain("\n");
    assertThat(snippet.length()).isLessThan(230);
  }

  @Test
  void snippetIsWholeShortDescriptionWithoutEllipsis() {
    var snippet = TextSearch.snippet(offer("Dev", "Acme", "Java\n- Kafka"), List.of("kafka"));

    assertThat(snippet).isEqualTo("Java - Kafka");
  }

  @Test
  void noSnippetWhenTheHitIsOnlyInTheTitle() {
    assertThat(TextSearch.snippet(offer("Kafka Dev", "Acme", "Java"), List.of("kafka"))).isNull();
    assertThat(TextSearch.snippet(offer("Dev", "Acme", "Java"), List.of())).isNull();
  }
}
