package radar.service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import radar.model.RawJobOffer;

// search over the site's own text: title, company, description; every term must match.
// A term matches at the start of a word ("polski" finds "polskiego", "iam" skips "Miami");
// a quoted term or phrase must match whole words ("java" in quotes skips "JavaScript").
// The UI's highlight() repeats these rules: change both together.
public final class TextSearch {

  public record Term(String text, Pattern pattern) {
  }

  private static final String NOT_AFTER_WORD = "(?<![\\p{L}\\p{N}])";
  private static final String NOT_BEFORE_WORD = "(?![\\p{L}\\p{N}])";
  private static final Pattern TOKEN = Pattern.compile("\"([^\"]*)\"?|(\\S+)");
  private static final int BEFORE = 60;
  private static final int AFTER = 140;

  private TextSearch() {
  }

  public static List<Term> terms(String query) {
    if (query == null || query.isBlank()) {
      return List.of();
    }

    var terms = new LinkedHashMap<String, Term>();
    var tokens = TOKEN.matcher(fold(query));

    while (tokens.find()) {
      var exact = tokens.group(1) != null;
      var words = (exact ? tokens.group(1) : tokens.group(2)).strip();
      if (!words.isEmpty()) {
        var term = term(words, exact);
        terms.putIfAbsent(term.pattern().pattern(), term);
      }
    }

    return List.copyOf(terms.values());
  }

  public static boolean matches(RawJobOffer offer, List<Term> terms) {
    var text = fold(searchable(offer));
    return terms.stream().allMatch(term -> term.pattern().matcher(text).find());
  }

  // the description around the first hit, so a card shows why it matched
  public static String snippet(RawJobOffer offer, List<Term> terms) {
    var description = offer.description();
    if (terms.isEmpty() || description == null) {
      return null;
    }

    var folded = fold(description);
    var hit = terms.stream()
        .map(term -> term.pattern().matcher(folded))
        .filter(matcher -> matcher.find())
        .mapToInt(matcher -> matcher.start())
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

  // a phrase's words may be split by any whitespace, line breaks included;
  // edges like "+" in "c++" or "." in ".net" are not word characters, so no boundary there
  private static Term term(String words, boolean exact) {
    var body = Arrays.stream(words.split("\\s+"))
        .map(Pattern::quote)
        .collect(Collectors.joining("\\s+"));
    var start = Character.isLetterOrDigit(words.charAt(0)) ? NOT_AFTER_WORD : "";
    var last = words.charAt(words.length() - 1);
    var end = exact && Character.isLetterOrDigit(last) ? NOT_BEFORE_WORD : "";
    var regex = start + body + end;

    return new Term(exact ? "\"" + words + "\"" : words, Pattern.compile(regex));
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
