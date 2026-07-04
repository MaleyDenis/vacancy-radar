package radar.connector;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import radar.model.RawJobOffer;

/**
 * Unit tests for the JSON-API parsing (no network). Fixtures mirror the shape of
 * {@code api.justjoin.it/v2/user-panel/offers}.
 */
class JustJoinConnectorTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final JustJoinConnector connector = new JustJoinConnector(mapper);

  private JsonNode json(String s) throws Exception {
    return mapper.readTree(s);
  }

  @Test
  void mapsAFullyPopulatedOffer() throws Exception {
    JsonNode page = json("""
        {
          "meta": {"page": 1, "totalPages": 1},
          "data": [
            {
              "guid": "abc-123",
              "slug": "acme-senior-java-dev-krakow-java",
              "title": "Senior Java Developer",
              "companyName": "Acme",
              "city": "Kraków",
              "workplaceType": "remote",
              "experienceLevel": "senior",
              "publishedAt": "2026-06-10T16:39:31.585Z",
              "requiredSkills": ["Java", "Spring Boot", "AWS"],
              "employmentTypes": [
                {"from": 18000, "to": 26000, "currency": "pln", "type": "b2b"}
              ]
            }
          ]
        }
        """);

    List<RawJobOffer> offers = connector.parseOffers(page);

    assertThat(offers).hasSize(1);
    RawJobOffer o = offers.get(0);
    assertThat(o.id()).isEqualTo("abc-123");
    assertThat(o.title()).isEqualTo("Senior Java Developer");
    assertThat(o.companyName()).isEqualTo("Acme");
    assertThat(o.location()).isEqualTo("Kraków");
    assertThat(o.remote()).isTrue();
    assertThat(o.experienceLevel()).isEqualTo("senior");
    assertThat(o.publishedAt()).isEqualTo("2026-06-10T16:39:31.585Z");
    assertThat(o.skills()).containsExactly("Java", "Spring Boot", "AWS");
    assertThat(o.salaryMin()).isEqualTo(18000);
    assertThat(o.salaryMax()).isEqualTo(26000);
    assertThat(o.currency()).isEqualTo("PLN");
    assertThat(o.contractType()).isEqualTo("b2b");
    assertThat(o.applyUrl()).isEqualTo("https://justjoin.it/job-offer/acme-senior-java-dev-krakow-java");
    assertThat(o.sourceConnector()).isEqualTo("justjoin");
    assertThat(o.description()).isNull(); // not available from the list API
    assertThat(o.companySize()).isNull();
  }

  @Test
  void handlesMissingSkillsSalaryAndNonRemote() throws Exception {
    JsonNode page = json("""
        {
          "meta": {"page": 1, "totalPages": 1},
          "data": [
            {
              "guid": "def-456",
              "slug": "x-java-dev-warszawa-java",
              "title": "Java Dev",
              "companyName": "Globex",
              "city": "Warszawa",
              "workplaceType": "hybrid",
              "experienceLevel": "mid",
              "requiredSkills": null,
              "employmentTypes": []
            }
          ]
        }
        """);

    RawJobOffer o = connector.parseOffers(page).get(0);
    assertThat(o.remote()).isFalse();
    assertThat(o.skills()).isEmpty();
    assertThat(o.salaryMin()).isNull();
    assertThat(o.salaryMax()).isNull();
    assertThat(o.currency()).isNull();
    assertThat(o.contractType()).isNull();
  }

  @Test
  void readsSkillsGivenAsObjects() throws Exception {
    JsonNode page = json("""
        {
          "data": [
            {
              "guid": "g", "slug": "s", "title": "t", "companyName": "c",
              "requiredSkills": [{"name": "Java", "level": 4}, {"name": "Kafka", "level": 2}],
              "employmentTypes": []
            }
          ]
        }
        """);

    RawJobOffer o = connector.parseOffers(page).get(0);
    assertThat(o.skills()).containsExactly("Java", "Kafka");
  }

  @Test
  void emptyDataYieldsEmptyList() throws Exception {
    assertThat(connector.parseOffers(json("{\"data\": []}"))).isEmpty();
    assertThat(connector.parseOffers(json("{}"))).isEmpty();
  }
}
