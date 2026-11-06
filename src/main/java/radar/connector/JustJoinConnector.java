package radar.connector;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Component;
import radar.model.RawJobOffer;

/**
 * Scrapes Java job offers from justjoin.it.
 *
 * <p>The old JSON API ({@code api.justjoin.it/v2/user-panel/offers}) is gone (HTTP 503). The listing
 * page {@code justjoin.it/job-offers/all-locations/java?page=N} is server-rendered by Next.js and
 * embeds the same offer objects in its React Server Components payload
 * ({@code self.__next_f.push([1,"..."])} script chunks). We concatenate those chunks and pick every
 * JSON object that carries a {@code guid}. 50 offers per page, newest first by {@code publishedAt}.
 *
 * <p>The listing has no description — the details step fetches it from the offer page's JSON-LD.
 */
@Component
public class JustJoinConnector {

  private static final String LISTING_URL = "https://justjoin.it/job-offers/all-locations/java";
  private static final String OFFER_URL = "https://justjoin.it/job-offer/";
  private static final int MAX_PAGES_SAFETY = 100;
  static final String USER_AGENT =
      "vacancy-radar/1.0 (+https://github.com/MaleyDenis/vacancy-radar)";
  private static final String SOURCE = "justjoin";

  /** Prefix of a Next.js flight chunk {@code self.__next_f.push([1,"<json string>"])}. */
  private static final String FLIGHT_CHUNK = "self.__next_f.push([1,";
  private static final String OFFER_START = "{\"applyUrl\":";

  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  public JustJoinConnector(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.httpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
  }

  /**
   * One listing page: the parsed offers, and whether the page links to the next one (the last page
   * has no {@code ?page=N+1} link).
   */
  public record OfferPage(List<RawJobOffer> offers, boolean hasNext) {
  }

  /** Upper bound on how many pages any caller will walk, as a runaway guard. */
  public static final int MAX_PAGES = MAX_PAGES_SAFETY;

  /** Fetches one listing page (1-based), newest first. */
  public OfferPage fetchPage(int page) {
    var html = get(LISTING_URL + "?page=" + page, "listing page " + page);
    var offers = parseOffers(html);
    var hasNext = !offers.isEmpty() && html.contains("java?page=" + (page + 1) + "\"");
    return new OfferPage(offers, hasNext);
  }

  /**
   * Fetches the raw HTML of a single offer's page (e.g. {@code applyUrl}). The offer detail — full
   * description and company domain — lives in a JSON-LD block on this page; parsing is done elsewhere
   * ({@code OfferDetailsParser}). Never sends any of this to Claude.
   */
  public String fetchOfferPageHtml(String url) {
    return get(url, "offer page " + url);
  }

  private String get(String url, String what) {
    var request = HttpRequest.newBuilder(URI.create(url))
        .header("User-Agent", USER_AGENT)
        .header("Accept", "text/html")
        .timeout(Duration.ofSeconds(30))
        .GET()
        .build();
    try {
      var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new IllegalStateException(
            "JustJoin returned HTTP " + response.statusCode() + " for " + what);
      }
      return response.body();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to fetch " + what, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted fetching " + what, e);
    }
  }

  /**
   * Extracts offers from a listing page's HTML. Package-private so it can be unit-tested with a
   * fixture, without touching the network. Deduplicates by {@code guid}, keeping page order.
   */
  List<RawJobOffer> parseOffers(String html) {
    var flight = flightPayload(html);
    var chars = flight.toCharArray();
    var byGuid = new LinkedHashMap<String, RawJobOffer>();
    var from = 0;
    while ((from = flight.indexOf(OFFER_START, from)) >= 0) {
      var node = readObjectAt(chars, from);
      from += OFFER_START.length();
      if (node != null && node.hasNonNull("guid")) {
        byGuid.putIfAbsent(node.get("guid").asText(), toRawJobOffer(node));
      }
    }
    return new ArrayList<>(byGuid.values());
  }

  /**
   * Concatenates all Next.js flight chunks into one decoded string. Each chunk's string literal is
   * read with Jackson's streaming parser (a regex over ~1 MB literals overflows the stack).
   */
  private String flightPayload(String html) {
    var chars = html.toCharArray();
    var sb = new StringBuilder();
    var from = 0;
    while ((from = html.indexOf(FLIGHT_CHUNK, from)) >= 0) {
      from += FLIGHT_CHUNK.length();
      try (var parser = objectMapper.getFactory()
          .createParser(chars, from, chars.length - from)) {
        if (parser.nextToken() == JsonToken.VALUE_STRING) {
          sb.append(parser.getText());
        }
      } catch (IOException e) {
        // a malformed chunk only loses the offers inside it
      }
    }
    return sb.toString();
  }

  /** Reads the single JSON object starting at {@code start}, or null if it isn't valid JSON. */
  private JsonNode readObjectAt(char[] chars, int start) {
    try (var parser = objectMapper.getFactory().createParser(chars, start, chars.length - start)) {
      return objectMapper.readTree(parser);
    } catch (IOException e) {
      return null;
    }
  }

  private RawJobOffer toRawJobOffer(JsonNode n) {
    var slug = textOrNull(n, "slug");
    var employment = originalEmployment(n.path("employmentTypes"));
    // Undisclosed salary (from/to both null): amount, currency, unit and gross are all null.
    var salary = employment.hasNonNull("from") || employment.hasNonNull("to")
        ? employment : MissingNode.getInstance();
    return new RawJobOffer(
        textOrNull(n, "guid"),
        textOrNull(n, "title"),
        textOrNull(n, "companyName"),
        null, // companySize — not present in the listing
        textOrNull(n, "city"),
        "remote".equalsIgnoreCase(textOrNull(n, "workplaceType")),
        textOrNull(n, "publishedAt"),
        textOrNull(n, "experienceLevel"),
        null, // description — filled by the details step
        intOrNull(salary, "from"),
        intOrNull(salary, "to"),
        upperOrNull(salary, "currency"),
        textOrNull(salary, "unit"),
        boolOrNull(salary, "gross"),
        textOrNull(employment, "type"), // contract type is known even without a salary
        slug == null ? null : OFFER_URL + slug,
        SOURCE,
        null); // domain — filled by the details step
  }

  /**
   * Picks the employment type quoted in its original currency. The listing also carries converted
   * copies (GBP/USD/EUR/CHF, {@code currencySource: "conversion"}) — often first in the array.
   */
  private static JsonNode originalEmployment(JsonNode types) {
    for (var t : types) {
      if ("original".equals(t.path("currencySource").asText())) {
        return t;
      }
    }
    return types.path(0);
  }

  private static String textOrNull(JsonNode node, String field) {
    var v = node.get(field);
    return v == null || v.isNull() ? null : v.asText();
  }

  private static String upperOrNull(JsonNode node, String field) {
    var v = textOrNull(node, field);
    return v == null ? null : v.toUpperCase();
  }

  private static Integer intOrNull(JsonNode node, String field) {
    var v = node.get(field);
    return v == null || v.isNull() || !v.isNumber() ? null : v.asInt();
  }

  private static Boolean boolOrNull(JsonNode node, String field) {
    var v = node.get(field);
    return v == null || !v.isBoolean() ? null : v.asBoolean();
  }
}
