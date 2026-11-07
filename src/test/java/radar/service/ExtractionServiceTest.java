package radar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import radar.model.OfferExtraction;
import radar.model.RawJobOffer;
import radar.model.StoredRawOffer;
import radar.repository.JsonRepository;

class ExtractionServiceTest {

  private ClaudeClient claude;
  private JsonRepository repository;
  private ExtractionService service;

  private final OfferExtraction extraction =
      new OfferExtraction(List.of("Java"), List.of("5y"), "build", "payments", "senior");

  @BeforeEach
  void setUp() {
    claude = mock(ClaudeClient.class);
    repository = mock(JsonRepository.class);
    service = new ExtractionService(claude, repository);
  }

  private StoredRawOffer stored(String id, String description) {
    var offer = new RawJobOffer(id, "Java Dev", "Acme", null, "Kraków", true, "2026-11-01",
        "senior", description, null, null, null, null, null, "b2b", "url", "justjoin", null);
    return new StoredRawOffer(offer, "2026-11-01T00:00:00Z", null);
  }

  @SuppressWarnings("unchecked")
  private Map<String, OfferExtraction> saved() {
    var captor = ArgumentCaptor.forClass(Map.class);
    verify(repository).saveExtractions(captor.capture());
    return captor.getValue();
  }

  @Test
  void extractsOnlyOffersWithDescriptionAndNoExtraction() {
    var existing = new LinkedHashMap<String, OfferExtraction>();
    existing.put("done", extraction);
    when(repository.readExtractions()).thenReturn(existing);
    when(repository.readRawOffers()).thenReturn(List.of(
        stored("done", "text"), stored("new", "Spring job"), stored("nodesc", null)));
    when(claude.ask(contains("Spring job"), eq(OfferExtraction.class))).thenReturn(extraction);

    var result = service.extractMissing(null);

    assertThat(result.extracted()).isEqualTo(1);
    assertThat(result.remaining()).isZero();
    assertThat(saved()).containsOnlyKeys("done", "new");
  }

  @Test
  void limitAndFailuresLeaveOffersForLater() {
    when(repository.readExtractions()).thenReturn(new LinkedHashMap<>());
    when(repository.readRawOffers()).thenReturn(List.of(
        stored("fail", "boom"), stored("ok", "fine"), stored("later", "wait")));
    when(claude.ask(contains("boom"), any())).thenThrow(new IllegalStateException("api down"));
    when(claude.ask(contains("fine"), eq(OfferExtraction.class))).thenReturn(extraction);

    var result = service.extractMissing(1);

    assertThat(result.extracted()).isEqualTo(1);
    assertThat(result.remaining()).isEqualTo(2);
    assertThat(saved()).containsOnlyKeys("ok");
    verify(claude, never()).ask(contains("wait"), any());
  }

  @Test
  void promptHasOfferTextButNoProfile() {
    var prompt = ExtractionService.prompt(stored("a", "Plain description").offer());
    assertThat(prompt).contains("Plain description").contains("Title: Java Dev")
        .doesNotContain("PROFILE");
  }
}
