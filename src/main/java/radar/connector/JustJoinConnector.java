package radar.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import radar.model.RawJobOffer;

/**
 * Scrapes Java job offers from justjoin.it.
 *
 * <p>Historically justjoin.it embedded offer data in a {@code __NEXT_DATA__} script tag. As of 2026
 * the site is a React Server Components app and no longer exposes that. Instead we call its public
 * JSON API ({@code api.justjoin.it/v2/user-panel/offers}), which requires a {@code Version: 2}
 * header and a {@code categories[]} filter. Java is {@code categoryId=6}.
 *
 * <p>The list endpoint does not carry the full job description (that lives only in the page's RSC
 * payload and has no stable JSON endpoint), so {@link RawJobOffer#description()} is left null here.
 * Fetching descriptions is deliberately out of scope for this connector — see {@link #DESCRIPTION_NOTE}.
 */
@Component
public class JustJoinConnector {

  static final String DESCRIPTION_NOTE =
      "Description is not available from the list API; a follow-up task will add per-offer fetching.";

  private static final Logger log = LoggerFactory.getLogger(JustJoinConnector.class);

  private static final String API_BASE = "https://api.justjoin.it/v2/user-panel/offers";
  private static final int JAVA_CATEGORY_ID = 6;
  private static final int PER_PAGE = 100;
  private static final int MAX_PAGES_SAFETY = 500;
  private static final String USER_AGENT =
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
          + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";
  private static final String SOURCE = "justjoin";

  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  public JustJoinConnector(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.httpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
  }

  /** One page of results: the parsed offers plus the total page count reported by the API. */
  public record OfferPage(List<RawJobOffer> offers, int totalPages) {
  }

  /** Upper bound on how many pages any caller will walk, as a runaway guard. */
  public static final int MAX_PAGES = MAX_PAGES_SAFETY;

  /**
   * Fetches every Java offer across all pages, newest first. Rate-limits between requests to stay
   * polite.
   */
  public List<RawJobOffer> fetchAll() {
    var all = new ArrayList<RawJobOffer>();
    var page = 1;
    var totalPages = 1;
    while (page <= totalPages && page <= MAX_PAGES_SAFETY) {
      var p = fetchPage(page);
      totalPages = p.totalPages();
      all.addAll(p.offers());
      log.info("JustJoin page {}/{}: {} offers ({} total)", page, totalPages, p.offers().size(),
          all.size());
      page++;
      if (page <= totalPages) {
        sleepPolitely();
      }
    }
    return all;
  }

  /**
   * Fetches a single page of offers (newest first). Lets callers paginate lazily — e.g. an
   * incremental scan that stops once it reaches already-known offers.
   */
  public OfferPage fetchPage(int page) {
    var root = fetchPageJson(page);
    var totalPages = root.path("meta").path("totalPages").asInt(1);
    return new OfferPage(parseOffers(root), totalPages);
  }

  /**
   * Fetches the raw HTML of a single offer's page (e.g. {@code applyUrl}). The offer detail — full
   * description and company domain — lives in a JSON-LD block on this page; parsing is done elsewhere
   * ({@code OfferDetailsParser}). Never sends any of this to Claude.
   */
  public String fetchOfferPageHtml(String url) {
    var request = HttpRequest.newBuilder(URI.create(url))
        .header("User-Agent", USER_AGENT)
        .header("Accept", "text/html")
        .header("Referer", "https://justjoin.it/")
        .timeout(Duration.ofSeconds(30))
        .GET()
        .build();
    try {
      var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new IllegalStateException(
            "Offer page returned HTTP " + response.statusCode() + " for " + url);
      }
      return response.body();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to fetch offer page " + url, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted fetching offer page " + url, e);
    }
  }

  private JsonNode fetchPageJson(int page) {
    var url = API_BASE + "?categories%5B%5D=" + JAVA_CATEGORY_ID + "&page=" + page + "&perPage="
        + PER_PAGE + "&sortBy=published&orderBy=DESC";
    var request = HttpRequest.newBuilder(URI.create(url))
        .header("User-Agent", USER_AGENT)
        .header("Accept", "application/json")
        .header("Referer", "https://justjoin.it/")
        .header("Version", "2")
        .timeout(Duration.ofSeconds(30))
        .GET()
        .build();
    try {
      var response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new IllegalStateException(
            "JustJoin API returned HTTP " + response.statusCode() + " for page " + page);
      }
      return objectMapper.readTree(response.body());
    } catch (IOException e) {
      throw new IllegalStateException("Failed to fetch JustJoin page " + page, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted fetching JustJoin page " + page, e);
    }
  }

  /**
   * Parses one API page's JSON into raw offers. Package-private so it can be unit-tested with a
   * fixture, without touching the network.
   */
  List<RawJobOffer> parseOffers(JsonNode root) {
    var offers = new ArrayList<RawJobOffer>();
    for (var node : root.path("data")) {
      offers.add(toRawJobOffer(node));
    }
    return offers;
  }

  private RawJobOffer toRawJobOffer(JsonNode n) {
    var slug = textOrNull(n, "slug");
    var salary = n.path("employmentTypes").path(0);
    return new RawJobOffer(
        textOrNull(n, "guid"),
        textOrNull(n, "title"),
        textOrNull(n, "companyName"),
        null, // companySize — not present in the list API
        textOrNull(n, "city"),
        "remote".equalsIgnoreCase(textOrNull(n, "workplaceType")),
        textOrNull(n, "publishedAt"),
        textOrNull(n, "experienceLevel"),
        null, // description — see DESCRIPTION_NOTE
        readSkills(n.path("requiredSkills")),
        intOrNull(salary, "from"),
        intOrNull(salary, "to"),
        upperOrNull(salary, "currency"),
        textOrNull(salary, "type"),
        slug == null ? null : "https://justjoin.it/job-offer/" + slug,
        SOURCE,
        null); // domain — filled by the details step, not the list API
  }

  private static List<String> readSkills(JsonNode skillsNode) {
    var skills = new ArrayList<String>();
    if (skillsNode.isArray()) {
      for (var s : skillsNode) {
        // requiredSkills may be an array of strings or of objects with a "name" field
        if (s.isTextual()) {
          skills.add(s.asText());
        } else if (s.hasNonNull("name")) {
          skills.add(s.get("name").asText());
        }
      }
    }
    return skills;
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

  private void sleepPolitely() {
    try {
      Thread.sleep(500 + ThreadLocalRandom.current().nextInt(500));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
