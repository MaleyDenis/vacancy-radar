package radar.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Verifies every model round-trips through Jackson and that unknown JSON properties are ignored.
 */
class ModelSerializationTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private <T> void assertRoundTrip(T value, Class<T> type) throws Exception {
    String json = mapper.writeValueAsString(value);
    T back = mapper.readValue(json, type);
    assertThat(back).isEqualTo(value);
  }

  @Test
  void salaryRangeRoundTrips() throws Exception {
    assertRoundTrip(new SalaryRange(15000, 25000, "PLN"), SalaryRange.class);
  }

  @Test
  void rawJobOfferRoundTrips() throws Exception {
    RawJobOffer offer = new RawJobOffer(
        "jj-123", "Senior Java Dev", "Acme", "50-100", "Warsaw", true,
        "2026-07-04", "senior", "Build things", List.of("Java", "Spring"),
        18000, 26000, "PLN", "B2B", "https://justjoin.it/job/jj-123", "justjoin");
    assertRoundTrip(offer, RawJobOffer.class);
  }

  @Test
  void userProfileRoundTrips() throws Exception {
    UserProfile profile = new UserProfile(
        List.of("Java", "Spring Boot"), 3, "B2B", true, 15000, "PLN", "B2B",
        List.of("Warsaw", "Remote"), 6);
    assertRoundTrip(profile, UserProfile.class);
  }

  @Test
  void jobReportRoundTrips() throws Exception {
    RawJobOffer offer = new RawJobOffer(
        "jj-1", "Java Dev", "Acme", "50", "Kraków", false, "2026-07-04", "mid",
        "desc", List.of("Java"), 12000, 18000, "PLN", "B2B", "url", "justjoin");
    JobReport report = new JobReport(
        offer, List.of("Java", "SQL"), List.of("Kafka"), "product", "mid",
        List.of("on-call"), List.of("remote"), "system design", 8,
        new SalaryRange(12000, 18000, "PLN"));
    assertRoundTrip(report, JobReport.class);
  }

  @Test
  void analyticsRoundTrips() throws Exception {
    Analytics analytics = new Analytics(
        "2026-07-04", 42, Map.of("Java", 30, "Spring", 20), List.of("Acme", "Globex"),
        new SalaryRange(10000, 30000, "PLN"), Map.of("product", 0.6, "outsourcing", 0.4),
        66.7);
    assertRoundTrip(analytics, Analytics.class);
  }

  @Test
  void scanProgressRoundTrips() throws Exception {
    assertRoundTrip(
        new ScanProgress(ScanProgress.Type.ENRICHING, "enriching", 5, 42, 0),
        ScanProgress.class);
  }

  @Test
  void unknownPropertiesAreIgnored() throws Exception {
    String json = """
        {"id":"jj-9","title":"Java Dev","unexpectedField":"boom","anotherOne":123}
        """;
    RawJobOffer offer = mapper.readValue(json, RawJobOffer.class);
    assertThat(offer.id()).isEqualTo("jj-9");
    assertThat(offer.title()).isEqualTo("Java Dev");
  }

  @Test
  void withRawJobOfferAttachesOffer() {
    JobReport report = new JobReport(
        null, List.of("Java"), List.of(), "product", "mid", List.of(), List.of(),
        "focus", 7, new SalaryRange(10000, 20000, "PLN"));
    RawJobOffer offer = new RawJobOffer(
        "jj-2", "t", "c", null, null, null, null, null, null, null, null, null,
        null, null, null, null);
    JobReport attached = report.withRawJobOffer(offer);
    assertThat(attached.rawJobOffer()).isSameAs(offer);
    assertThat(attached.matchScore()).isEqualTo(7);
    assertThat(report.rawJobOffer()).isNull();
  }
}
