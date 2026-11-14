package radar.service;

import java.util.Arrays;
import java.util.List;
import radar.model.RawJobOffer;

// substring search over the site's own text: title, company, description; every term must match
public final class TextSearch {

  private static final int BEFORE = 60;
  private static final int AFTER = 140;

  private TextSearch() {
  }

  public static List<String> terms(String query) {
    if (query == null || query.isBlank()) {
      return List.of();
    }

    return Arrays.stream(fold(query.strip()).split("\\s+")).distinct().toList();
  }

  public static boolean matches(RawJobOffer offer, List<String> terms) {
    var text = fold(searchable(offer));
    return terms.stream().allMatch(text::contains);
  }

  // the description around the first hit, so a card shows why it matched
  public static String snippet(RawJobOffer offer, List<String> terms) {
    var description = offer.description();
    if (terms.isEmpty() || description == null) {
      return null;
    }

    var folded = fold(description);
    var hit = terms.stream()
        .mapToInt(folded::indexOf)
        .filter(i -> i >= 0)
        .min();
    if (hit.isEmpty()) {
      return null;
    }

    var from = wordStart(description, Math.max(0, hit.getAsInt() - BEFORE));
    var to = wordEnd(description, Math.min(description.length(), hit.getAsInt() + AFTER));
    var text = description.substring(from, to).replaceAll("\\s+", " ").strip();

    return (from > 0 ? "… " : "") + text + (to < description.length() ? " …" : "");
  }

  // length-preserving, so positions in the folded text match the original
  static String fold(String text) {
    var chars = text.toCharArray();
    for (var i = 0; i < chars.length; i++) {
      var lower = Character.toLowerCase(chars[i]);
      chars[i] = switch (lower) {
        case 'ą' -> 'a';
        case 'ć' -> 'c';
        case 'ę' -> 'e';
        case 'ł' -> 'l';
        case 'ń' -> 'n';
        case 'ó' -> 'o';
        case 'ś' -> 's';
        case 'ź', 'ż' -> 'z';
        default -> lower;
      };
    }

    return new String(chars);
  }

  private static String searchable(RawJobOffer offer) {
    return String.join("\n", nonNull(offer.title()), nonNull(offer.companyName()),
        nonNull(offer.description()));
  }

  private static String nonNull(String text) {
    return text == null ? "" : text;
  }

  private static int wordStart(String text, int i) {
    while (i > 0 && !Character.isWhitespace(text.charAt(i - 1))) {
      i--;
    }

    return i;
  }

  private static int wordEnd(String text, int i) {
    while (i < text.length() && !Character.isWhitespace(text.charAt(i))) {
      i++;
    }

    return i;
  }
}
