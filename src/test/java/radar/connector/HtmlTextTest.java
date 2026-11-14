package radar.connector;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HtmlTextTest {

  @Test
  void keepsParagraphsAndListItems() {
    var html = "<p>About us</p><p>We build things.</p>"
        + "<ul><li><p>Java</p></li><li><p>Kafka</p></li></ul><p>Benefits</p>";

    assertThat(HtmlText.toText(html))
        .isEqualTo("About us\n\nWe build things.\n\n- Java\n- Kafka\n\nBenefits");
  }

  @Test
  void separatesWordsAtTagBoundaries() {
    assertThat(HtmlText.toText("<ul><li>Java</li><li>Kafka</li></ul>")).isEqualTo("- Java\n- Kafka");
  }

  @Test
  void putsLaterParagraphsOfAnItemOnTheirOwnLines() {
    var html = "<ul><li><p>English</p><p>Nice to have</p></li><li><p>Kafka</p></li></ul>";

    assertThat(HtmlText.toText(html)).isEqualTo("- English\nNice to have\n- Kafka");
  }

  @Test
  void turnsLineBreaksIntoNewlines() {
    assertThat(HtmlText.toText("<p>one<br />two</p>")).isEqualTo("one\ntwo");
  }

  @Test
  void decodesEntitiesAndCollapsesSpaces() {
    assertThat(HtmlText.toText("<p>R&amp;D   team</p>")).isEqualTo("R&D team");
  }

  @Test
  void dropsEmptyListItems() {
    assertThat(HtmlText.toText("<ul><li>Java</li><li> </li></ul>")).isEqualTo("- Java");
  }

  @Test
  void leavesPlainTextAsIs() {
    var text = "Project\nYou will build data streams.\n\nSalary < 20k";

    assertThat(HtmlText.isHtml(text)).isFalse();
    assertThat(HtmlText.toText(text)).isEqualTo(text);
    assertThat(HtmlText.toText(null)).isNull();
  }

  @Test
  void isIdempotent() {
    var text = HtmlText.toText("<p>About</p><ul><li>Java</li></ul>");

    assertThat(HtmlText.toText(text)).isEqualTo(text);
  }
}
