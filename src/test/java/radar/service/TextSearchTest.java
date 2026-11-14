package radar.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import radar.model.RawJobOffer;

class TextSearchTest {

  private static RawJobOffer offer(String title, String company, String description) {
    return new RawJobOffer("id", title, company, null, null, null, null, null, description,
        null, null, null, null, null, null, null, "justjoin", null, null);
  }

  private static boolean finds(String description, String query) {
    return TextSearch.matches(offer("Dev", "Acme", description), TextSearch.terms(query));
  }

  @Test
  void termsAreFoldedDedupedAndQuotedOnesKept() {
    assertThat(TextSearch.terms("  Kafka  ŁÓDŹ kafka \"Spring Boot\" "))
        .extracting(TextSearch.Term::text)
        .containsExactly("kafka", "lodz", "\"spring boot\"");
    assertThat(TextSearch.terms(null)).isEmpty();
    assertThat(TextSearch.terms("  \"\" ")).isEmpty();
  }

  @Test
  void termMatchesOnlyAtTheStartOfAWord() {
    assertThat(finds("cloud networking, IAM, Linux", "iam")).isTrue();
    assertThat(finds("my zapewniamy stabilne warunki", "iam")).isFalse();
    assertThat(finds("2026: Miami, Florida", "iam")).isFalse();
    assertThat(finds("Biegła znajomość języka polskiego", "polski")).isTrue();
    assertThat(finds("We use SpringBoot", "spring")).isTrue();
    assertThat(finds("Frontend in JavaScript", "java")).isTrue();
  }

  @Test
  void quotedTermMatchesWholeWordsOnly() {
    assertThat(finds("Frontend in JavaScript", "\"java\"")).isFalse();
    assertThat(finds("Backend in Java, frontend in JavaScript", "\"java\"")).isTrue();
    assertThat(finds("Experience with Spring\nBoot 3", "\"spring boot\"")).isTrue();
    assertThat(finds("Spring and Boot", "\"spring boot\"")).isFalse();
  }

  @Test
  void symbolsAtTheEdgesNeedNoWordBoundary() {
    assertThat(finds("Modern C++ and Rust", "c++")).isTrue();
    assertThat(finds("Modern C++ and Rust", "\"c++\"")).isTrue();
    assertThat(finds("Backend in ASP.NET", ".net")).isTrue();
  }

  @Test
  void everyTermMustMatchTitleCompanyOrDescription() {
    var offer = offer("Senior Java Developer", "Acme", "We use Kafka.\n- Docker");

    assertThat(TextSearch.matches(offer, TextSearch.terms("java kafka"))).isTrue();
    assertThat(TextSearch.matches(offer, TextSearch.terms("acme docker"))).isTrue();
    assertThat(TextSearch.matches(offer, TextSearch.terms("kafka python"))).isFalse();
    assertThat(TextSearch.matches(offer("Java Dev", null, null), TextSearch.terms("java"))).isTrue();
  }

  @Test
  void polishLettersMatchTheirLatinForms() {
    assertThat(finds("Praca we Wrocławiu, język polski", "wroclaw jezyk")).isTrue();
    assertThat(finds("Praca we Wrocławiu", "Wrocław")).isTrue();
  }

  @Test
  void snippetShowsTheDescriptionAroundTheFirstHit() {
    var description = "intro ".repeat(30) + "We build payments on Kafka streams. " + "tail ".repeat(60);

    var snippet = TextSearch.snippet(offer("Dev", "Acme", description), TextSearch.terms("kafka"));

    assertThat(snippet).startsWith("… ").endsWith(" …").contains("payments on Kafka streams.");
    assertThat(snippet).doesNotContain("\n");
    assertThat(snippet.length()).isLessThan(230);
  }

  @Test
  void snippetSkipsHitsInsideWords() {
    var description = "Team trip to Miami. " + "filler ".repeat(40) + "Strong IAM knowledge.";

    var snippet = TextSearch.snippet(offer("Dev", "Acme", description), TextSearch.terms("iam"));

    assertThat(snippet).contains("Strong IAM knowledge.").doesNotContain("Miami");
  }

  @Test
  void snippetIsWholeShortDescriptionWithoutEllipsis() {
    var snippet = TextSearch.snippet(offer("Dev", "Acme", "Java\n- Kafka"), TextSearch.terms("kafka"));

    assertThat(snippet).isEqualTo("Java - Kafka");
  }

  @Test
  void noSnippetWhenTheHitIsOnlyInTheTitle() {
    var terms = TextSearch.terms("kafka");

    assertThat(TextSearch.snippet(offer("Kafka Dev", "Acme", "Java"), terms)).isNull();
    assertThat(TextSearch.snippet(offer("Dev", "Acme", "Java"), TextSearch.terms(""))).isNull();
  }
}
