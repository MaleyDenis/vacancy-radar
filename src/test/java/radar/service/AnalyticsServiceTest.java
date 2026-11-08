package radar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import radar.model.Dashboard.Share;
import radar.model.OfferExtraction;
import radar.model.RawJobOffer;
import radar.model.StoredRawOffer;
import radar.repository.JsonRepository;

class AnalyticsServiceTest {

  private JsonRepository repository;
  private AnalyticsService service;
  private final List<StoredRawOffer> raw = new java.util.ArrayList<>();
  private final LinkedHashMap<String, OfferExtraction> extractions = new LinkedHashMap<>();

  @BeforeEach
  void setUp() {
    repository = mock(JsonRepository.class);
    service = new AnalyticsService(repository, 4.0, 4.0);
    when(repository.readRawOffers()).thenReturn(raw);
    when(repository.readExtractions()).thenReturn(extractions);
  }

  private RawJobOffer offer(String id, Integer min, Integer max, String currency, String unit,
      String contract, List<String> cities) {
    return new RawJobOffer(id, "Java Dev", "Acme", null, cities.get(0), true, "2026-11-01",
        "senior", "text", min, max, currency, unit, contract.equals("permanent"), contract, "url",
        "justjoin", null, cities);
  }

  private void add(RawJobOffer offer, String industry, List<String> skills, List<String> nice,
      String workMode, List<String> aiTools, String aiUsage) {
    raw.add(new StoredRawOffer(offer, "t", "t"));
    extractions.put(offer.id(), new OfferExtraction(skills, nice, null, null, null, "senior",
        "B2", null, workMode, null, null, industry, "outsourcing", null, aiTools, aiUsage));
  }

  private void givenData() {
    add(offer("a", 100, 140, "PLN", "hour", "b2b", List.of("Kraków", "Warszawa")), "banking",
        List.of("Java", "Apache Kafka", "Jira"), List.of("Docker"), "remote",
        List.of("Cursor"), "required");
    add(offer("b", 20000, 24000, "PLN", "month", "b2b", List.of("Kraków")), "Fintech",
        List.of("Java", "Kafka", "Spring Boot 3"), List.of("Kafka"), "hybrid", null, null);
    add(offer("c", 5000, 5000, "EUR", "month", "b2b", List.of("Wrocław")), "insurance",
        List.of("Java", "Oracle"), null, "office", null, null);
    add(offer("d", null, null, null, null, "permanent", List.of("Gdańsk")), null,
        List.of("Java"), null, null, null, null);
    // no extraction yet: ignored everywhere
    raw.add(new StoredRawOffer(offer("x", 1, 1, "PLN", "month", "b2b", List.of("Łódź")), "t",
        null));
  }

  @Test
  void groupsDomainsAndFiltersByThem() {
    givenData();

    var all = service.dashboard("all");
    assertThat(all.total()).isEqualTo(4);
    assertThat(all.domains()).extracting(d -> d.key() + "=" + d.count())
        .containsExactly("finance=2", "insurance=1", "unknown=1");

    var finance = service.dashboard("finance");
    assertThat(finance.total()).isEqualTo(2);
    assertThat(finance.marketTotal()).isEqualTo(4);
    assertThatThrownBy(() -> service.dashboard("mars")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void mergesSkillSynonymsDropsJunkAndKeepsMarketCounts() {
    givenData();

    var finance = service.dashboard("finance");
    var java = finance.skills().get(0);
    assertThat(java.name()).isEqualTo("Java");
    assertThat(java.must()).isEqualTo(2);
    assertThat(java.marketMust()).isEqualTo(4);
    var kafka = finance.skills().stream().filter(s -> s.name().equals("Kafka")).findFirst()
        .orElseThrow();
    assertThat(kafka.must()).isEqualTo(2);  // "Apache Kafka" + "Kafka"
    assertThat(kafka.nice()).isZero();      // "a plus" where already required is not counted
    assertThat(finance.skills()).extracting(s -> s.name())
        .contains("Spring Boot", "Docker")  // version stripped; nice-only skill listed
        .doesNotContain("Jira", "Apache Kafka");
  }

  @Test
  void convertsSalariesToMonthlyPlnPerContract() {
    givenData();

    var salary = service.dashboard("all").salary();
    // hour 120*168=20160, month 22000, EUR 5000*4=20000 -> median 20160 -> rounded to 100s
    assertThat(salary.b2b().count()).isEqualTo(3);
    assertThat(salary.b2b().median()).isEqualTo(20200);
    assertThat(salary.permanent()).isNull(); // the permanent offer states no salary
    assertThat(service.dashboard("all").withSalary()).isEqualTo(3);
  }

  @Test
  void countsAiWorkModeAndEveryCityOfAnOffer() {
    givenData();

    var all = service.dashboard("all");
    assertThat(all.ai().mentioned()).isEqualTo(1);
    assertThat(all.ai().required()).isEqualTo(1);
    assertThat(all.ai().tools()).containsExactly(new Share("Cursor", 1));
    assertThat(all.workMode()).contains(new Share("remote", 1), new Share(null, 1));
    assertThat(all.cities().get(0)).isEqualTo(new Share("Kraków", 2));
    assertThat(all.cities()).contains(new Share("Warszawa", 1)).noneMatch(s -> s.value().equals("Łódź"));
  }

  @Test
  void domainGroupsByKeywords() {
    assertThat(Domain.of("Investment Banking")).isEqualTo(Domain.FINANCE);
    assertThat(Domain.of("insurtech")).isEqualTo(Domain.INSURANCE);
    assertThat(Domain.of("finance and insurance")).isEqualTo(Domain.INSURANCE);
    assertThat(Domain.of("e-commerce")).isEqualTo(Domain.RETAIL);
    assertThat(Domain.of("IT services")).isEqualTo(Domain.IT);
    assertThat(Domain.of("aviation")).isEqualTo(Domain.OTHER);
    assertThat(Domain.of(null)).isEqualTo(Domain.UNKNOWN);
  }
}
