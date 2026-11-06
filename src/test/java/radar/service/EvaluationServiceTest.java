package radar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import radar.model.JobReport;
import radar.model.RawJobOffer;
import radar.model.StoredRawOffer;
import radar.model.UserProfile;
import radar.repository.JsonRepository;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {

  @Mock
  EnricherService enricher;

  @Mock
  JsonRepository repository;

  private final ObjectMapper mapper = new ObjectMapper();

  EvaluationService service;

  private final UserProfile profile = new UserProfile(
      List.of("Java"), 3, "B2B", true, 15000, "PLN", "B2B", List.of("Kraków"), 6);

  @BeforeEach
  void setUp() {
    service = new EvaluationService(enricher, repository, mapper);
  }

  private String hashOf(UserProfile p) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
        mapper.writeValueAsBytes(p)));
  }

  private RawJobOffer offer(String id) {
    return new RawJobOffer(id, "Java Dev", "Acme", null, "Kraków", true, "2026-07-04", "mid",
        "desc", null, null, "PLN", "b2b", "url", "justjoin", null);
  }

  private JobReport report(String id, int score) {
    return new JobReport(offer(id), List.of("Java"), "resp", "proj", List.of(), "mid", score);
  }

  private StoredRawOffer pending(String id) {
    return StoredRawOffer.fresh(offer(id), "2026-07-05T10:00:00Z");
  }

  @SuppressWarnings("unchecked")
  private List<JobReport> captureJobs() {
    ArgumentCaptor<List<JobReport>> captor = ArgumentCaptor.forClass(List.class);
    verify(repository).saveJobs(captor.capture());
    return captor.getValue();
  }

  @SuppressWarnings("unchecked")
  private List<StoredRawOffer> captureRawOffers() {
    ArgumentCaptor<List<StoredRawOffer>> captor = ArgumentCaptor.forClass(List.class);
    verify(repository).saveRawOffers(captor.capture());
    return captor.getValue();
  }

  @Test
  void scoresOffersNotYetEvaluatedAndMarksThem() {
    when(repository.readProfile()).thenReturn(profile);
    when(repository.loadJobs()).thenReturn(List.of());
    when(repository.readRawOffers()).thenReturn(List.of(pending("a"), pending("b")));
    when(enricher.enrich(any(), eq(profile))).thenReturn(report("a", 8), report("b", 5));

    var result = service.evaluate(null);

    assertThat(result.evaluated()).isEqualTo(2);
    assertThat(captureJobs()).hasSize(2);
    assertThat(captureRawOffers())
        .allSatisfy(s -> assertThat(s.evaluatedProfileHash()).isNotNull());
  }

  @Test
  void skipsAlreadyEvaluatedOffersAndReusesTheirReports() throws Exception {
    var alreadyScored = pending("a").withEvaluatedProfileHash(hashOf(profile));
    when(repository.readProfile()).thenReturn(profile);
    when(repository.loadJobs()).thenReturn(List.of(report("a", 8))); // existing report for a
    when(repository.readRawOffers()).thenReturn(List.of(alreadyScored, pending("b")));
    when(enricher.enrich(any(), eq(profile))).thenReturn(report("b", 5));

    var result = service.evaluate(null);

    assertThat(result.evaluated()).isEqualTo(1); // only b scored
    verify(enricher, times(1)).enrich(any(), eq(profile));
    assertThat(captureJobs()).hasSize(2); // reused a + new b
  }

  @Test
  void limitCapsHowManyOffersAreScored() {
    when(repository.readProfile()).thenReturn(profile);
    when(repository.loadJobs()).thenReturn(List.of());
    when(repository.readRawOffers())
        .thenReturn(List.of(pending("a"), pending("b"), pending("c")));
    when(enricher.enrich(any(), eq(profile))).thenReturn(report("a", 8));

    var result = service.evaluate(1);

    assertThat(result.evaluated()).isEqualTo(1);
    verify(enricher, times(1)).enrich(any(), eq(profile));
  }
}
