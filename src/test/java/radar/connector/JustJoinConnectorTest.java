package radar.connector;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for listing-page parsing (no network). Fixtures mimic justjoin.it's server-rendered
 * listing: offer objects live inside Next.js flight chunks {@code self.__next_f.push([1,"..."])}.
 */
class JustJoinConnectorTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final JustJoinConnector connector = new JustJoinConnector(mapper);

  private static final String FULL_OFFER = """
      {"applyUrl":null,"guid":"abc-123","slug":"acme-senior-java-dev-krakow-java",
       "title":"Senior Java Developer","companyName":"Acme","city":"Kraków",
       "workplaceType":"remote","experienceLevel":"senior",
       "publishedAt":"2026-11-06T17:09:51.121419Z",
       "employmentTypes":[
         {"from":28,"to":38,"currency":"USD","currencySource":"conversion","type":"b2b","unit":"hour","gross":false},
         {"from":140,"to":170,"currency":"pln","currencySource":"original","type":"b2b","unit":"hour","gross":false}
       ]}""";

  private static final String BARE_OFFER = """
      {"applyUrl":null,"guid":"def-456","slug":"x-java-dev-warszawa-java","title":"Java Dev",
       "companyName":"Globex","city":"Warszawa","workplaceType":"hybrid","experienceLevel":"mid",
       "employmentTypes":[]}""";

  /** Wraps a flight payload into script chunks, split in two to exercise concatenation. */
  private String html(String payload) throws Exception {
    var mid = payload.length() / 2;
    return "<html><body>"
        + "<script>self.__next_f.push([1," + mapper.writeValueAsString(payload.substring(0, mid))
        + "])</script>"
        + "<script>self.__next_f.push([1," + mapper.writeValueAsString(payload.substring(mid))
        + "])</script>"
        + "</body></html>";
  }

  @Test
  void mapsAFullyPopulatedOfferUsingOriginalSalary() throws Exception {
    var offers = connector.parseOffers(html("b1:[" + FULL_OFFER + "]\n"));

    assertThat(offers).hasSize(1);
    var o = offers.get(0);
    assertThat(o.id()).isEqualTo("abc-123");
    assertThat(o.title()).isEqualTo("Senior Java Developer");
    assertThat(o.companyName()).isEqualTo("Acme");
    assertThat(o.location()).isEqualTo("Kraków");
    assertThat(o.remote()).isTrue();
    assertThat(o.experienceLevel()).isEqualTo("senior");
    assertThat(o.publishedAt()).isEqualTo("2026-11-06T17:09:51.121419Z");
    assertThat(o.salaryMin()).isEqualTo(140); // original PLN, not the USD conversion listed first
    assertThat(o.salaryMax()).isEqualTo(170);
    assertThat(o.currency()).isEqualTo("PLN");
    assertThat(o.salaryUnit()).isEqualTo("hour");
    assertThat(o.salaryGross()).isFalse();
    assertThat(o.contractType()).isEqualTo("b2b");
    assertThat(o.applyUrl()).isEqualTo("https://justjoin.it/job-offer/acme-senior-java-dev-krakow-java");
    assertThat(o.sourceConnector()).isEqualTo("justjoin");
    assertThat(o.description()).isNull();
    assertThat(o.companySize()).isNull();
  }

  @Test
  void handlesMissingSalaryAndNonRemote() throws Exception {
    var o = connector.parseOffers(html("b1:[" + BARE_OFFER + "]\n")).get(0);

    assertThat(o.remote()).isFalse();
    assertThat(o.salaryMin()).isNull();
    assertThat(o.salaryMax()).isNull();
    assertThat(o.currency()).isNull();
    assertThat(o.salaryUnit()).isNull();
    assertThat(o.salaryGross()).isNull();
    assertThat(o.contractType()).isNull();
  }

  @Test
  void undisclosedSalaryLeavesAllSalaryFieldsNullButKeepsContractType() throws Exception {
    var offer = BARE_OFFER.replace("\"employmentTypes\":[]",
        "\"employmentTypes\":[{\"from\":null,\"to\":null,\"currency\":\"PLN\","
            + "\"currencySource\":\"original\",\"type\":\"permanent\",\"unit\":\"month\",\"gross\":true}]");

    var o = connector.parseOffers(html("b1:[" + offer + "]\n")).get(0);

    assertThat(o.salaryMin()).isNull();
    assertThat(o.currency()).isNull();
    assertThat(o.salaryUnit()).isNull();
    assertThat(o.salaryGross()).isNull();
    assertThat(o.contractType()).isEqualTo("permanent");
  }

  @Test
  void deduplicatesByGuidAndKeepsPageOrder() throws Exception {
    var payload = "b1:[" + FULL_OFFER + "," + BARE_OFFER + "]\nb2:[" + FULL_OFFER + "]\n";

    var offers = connector.parseOffers(html(payload));

    assertThat(offers).extracting("id").containsExactly("abc-123", "def-456");
  }

  @Test
  void handlesMegabyteSizedChunks() throws Exception {
    var filler = "x\\\"y".repeat(400_000); // escaped quotes inside a ~2 MB literal
    var payload = "b0:\"" + filler + "\"\nb1:[" + FULL_OFFER + "]\n";

    assertThat(connector.parseOffers(html(payload))).extracting("id").containsExactly("abc-123");
  }

  @Test
  void pageWithoutOffersYieldsEmptyList() throws Exception {
    assertThat(connector.parseOffers(html("b1:[]\n"))).isEmpty();
    assertThat(connector.parseOffers("<html></html>")).isEmpty();
  }
}
