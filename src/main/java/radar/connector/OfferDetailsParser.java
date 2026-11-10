package radar.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;
import radar.model.OfferDetails;

@Component
public class OfferDetailsParser {

  private final ObjectMapper objectMapper;

  public OfferDetailsParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public OfferDetails parse(String html) {
    var doc = Jsoup.parse(html);

    for (var script : doc.select("script[type=application/ld+json]")) {
      var node = readJson(script.data());
      if (node != null && "JobPosting".equals(node.path("@type").asText())) {
        var description = text(node.path("description"));
        var domain = domainFrom(node.path("hiringOrganization").path("sameAs").asText(null));
        return new OfferDetails(description, domain);
      }
    }

    return new OfferDetails(null, null);
  }

  private JsonNode readJson(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (Exception e) {
      return null;
    }
  }

  private static String text(JsonNode node) {
    if (node.isMissingNode() || node.isNull()) {
      return null;
    }

    var raw = node.asText();
    return raw.isBlank() ? null : Parser.unescapeEntities(raw, false);
  }

  private static String domainFrom(String url) {
    if (url == null || url.isBlank()) {
      return null;
    }

    try {
      var host = URI.create(url.trim()).getHost();
      if (host == null) {
        return null;
      }

      return host.startsWith("www.") ? host.substring(4) : host;
    } catch (RuntimeException e) {
      return null;
    }
  }
}
