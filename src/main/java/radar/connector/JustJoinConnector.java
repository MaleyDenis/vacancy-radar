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
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import radar.model.RawJobOffer;

// No public JSON API: offers come from the Next.js flight chunks embedded in the listing HTML
@Component
public class JustJoinConnector {

  private static final String LISTING_URL = "https://justjoin.it/job-offers/all-locations/java";
  private static final String OFFER_URL = "https://justjoin.it/job-offer/";
  private static final int MAX_PAGES_SAFETY = 100;
  static final String USER_AGENT =
      "vacancy-radar/1.0 (+https://github.com/MaleyDenis/vacancy-radar)";
  private static final String SOURCE = "justjoin";

  private static final String FLIGHT_CHUNK = "self.__next_f.push([1,";
  private static final String OFFER_START = "{\"applyUrl\":";

  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final String experienceLevels;

  public JustJoinConnector(
      ObjectMapper objectMapper,
      @Value("${radar.justjoin.experience-levels:}") String experienceLevels) {
    this.objectMapper = objectMapper;
    this.experienceLevels = experienceLevels == null ? "" : experienceLevels.replace(" ", "");
    this.httpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
  }

  public record OfferPage(List<RawJobOffer> offers, boolean hasNext) {
  }

  // guards against an endless pagination loop
  public static final int MAX_PAGES = MAX_PAGES_SAFETY;

  public OfferPage fetchPage(int page) {
    var html = get(listingUrl(page), "listing page " + page);
    var offers = parseOffers(html);
    var hasNext = !offers.isEmpty() && html.contains("page=" + (page + 1) + "\"");
    return new OfferPage(offers, hasNext);
  }

  String listingUrl(int page) {
    return experienceLevels.isEmpty()
        ? LISTING_URL + "?page=" + page
        : LISTING_URL + "?experience-levels=" + experienceLevels + "&page=" + page;
  }

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

  // package-private for fixture tests
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

  // streaming parser: a regex over ~1 MB literals overflows the stack
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
    var salary = employment.hasNonNull("from") || employment.hasNonNull("to")
        ? employment : MissingNode.getInstance();
    return new RawJobOffer(
        textOrNull(n, "guid"),
        textOrNull(n, "title"),
        textOrNull(n, "companyName"),
        null, // not in the listing
        textOrNull(n, "city"),
        "remote".equalsIgnoreCase(textOrNull(n, "workplaceType")),
        textOrNull(n, "publishedAt"),
        textOrNull(n, "experienceLevel"),
        null, // filled by the details step
        intOrNull(salary, "from"),
        intOrNull(salary, "to"),
        upperOrNull(salary, "currency"),
        textOrNull(salary, "unit"),
        boolOrNull(salary, "gross"),
        textOrNull(employment, "type"), // contract type is known even without a salary
        slug == null ? null : OFFER_URL + slug,
        SOURCE,
        null, // filled by the details step
        cities(n));
  }

  // JustJoin "multilocation": one offer (one guid) open in several cities
  private static List<String> cities(JsonNode n) {
    var cities = new LinkedHashSet<String>();
    var main = textOrNull(n, "city");
    if (main != null) {
      cities.add(main);
    }
    for (var location : n.path("multilocation")) {
      var city = textOrNull(location, "city");
      if (city != null) {
        cities.add(city);
      }
    }
    return List.copyOf(cities);
  }

  // the listing also carries converted copies (currencySource "conversion"), often first
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
