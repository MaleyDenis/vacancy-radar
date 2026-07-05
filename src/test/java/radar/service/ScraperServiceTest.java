package radar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import radar.connector.JustJoinConnector;
import radar.model.RawJobOffer;
import radar.model.ScanResult;
import radar.model.StoredRawOffer;
import radar.repository.JsonRepository;

@ExtendWith(MockitoExtension.class)
class ScraperServiceTest {

  private static final int RETENTION_DAYS = 30;

  @Mock
  JustJoinConnector connector;

  @Mock
  JsonRepository repository;

  ScraperService scraperService;

  @BeforeEach
  void setUp() {
    scraperService = new ScraperService(connector, repository, RETENTION_DAYS);
  }

  private RawJobOffer offer(String id) {
    return new RawJobOffer(id, "Java Dev", "Acme", null, "Kraków", true, "2026-07-04", "mid",
        null, List.of("Java"), null, null, null, "b2b", "https://justjoin.it/job-offer/" + id,
        "justjoin");
  }

  private StoredRawOffer stored(String id, String firstSeenAt) {
    return new StoredRawOffer(offer(id), firstSeenAt);
  }

  @SuppressWarnings("unchecked")
  private List<StoredRawOffer> capturePersisted() {
    ArgumentCaptor<List<StoredRawOffer>> captor = ArgumentCaptor.forClass(List.class);
    verify(repository).saveRawOffers(captor.capture());
    return captor.getValue();
  }

  // ---- scan ----

  @Test
  void scanAddsBrandNewOffersToEmptyPool() {
    when(repository.readRawOffers()).thenReturn(List.of());
    when(connector.fetchAll()).thenReturn(List.of(offer("a"), offer("b")));

    ScanResult result = scraperService.scan();

    assertThat(result).isEqualTo(new ScanResult(2, 2));
    List<StoredRawOffer> saved = capturePersisted();
    assertThat(saved).extracting(s -> s.offer().id()).containsExactly("a", "b");
    assertThat(saved).allSatisfy(s -> assertThat(s.firstSeenAt()).isNotBlank());
  }

  @Test
  void scanPreservesFirstSeenForOffersAlreadyInPool() {
    String seen = Instant.now().minus(5, ChronoUnit.DAYS).toString();
    when(repository.readRawOffers()).thenReturn(List.of(stored("a", seen)));
    when(connector.fetchAll()).thenReturn(List.of(offer("a"), offer("b")));

    ScanResult result = scraperService.scan();

    assertThat(result).isEqualTo(new ScanResult(1, 2));
    List<StoredRawOffer> saved = capturePersisted();
    assertThat(saved).extracting(s -> s.offer().id()).containsExactly("a", "b");
    assertThat(saved).filteredOn(s -> s.offer().id().equals("a"))
        .allSatisfy(s -> assertThat(s.firstSeenAt()).isEqualTo(seen));
  }

  @Test
  void scanPrunesOffersPastRetentionWindow() {
    String old = Instant.now().minus(RETENTION_DAYS + 10L, ChronoUnit.DAYS).toString();
    String recent = Instant.now().minus(2, ChronoUnit.DAYS).toString();
    when(repository.readRawOffers())
        .thenReturn(List.of(stored("old", old), stored("recent", recent)));
    when(connector.fetchAll()).thenReturn(List.of());

    ScanResult result = scraperService.scan();

    assertThat(result).isEqualTo(new ScanResult(0, 1));
    assertThat(capturePersisted()).extracting(s -> s.offer().id()).containsExactly("recent");
  }

  // ---- legacy scrapeNew (kept until evaluation is split out) ----

  @Test
  void scrapeNewFiltersOutSeenOffers() {
    List<RawJobOffer> all = IntStream.rangeClosed(1, 10).mapToObj(i -> offer("id-" + i)).toList();
    when(connector.fetchAll()).thenReturn(all);
    when(repository.readSeenIds()).thenReturn(List.of("id-2", "id-5", "id-9"));

    List<RawJobOffer> result = scraperService.scrapeNew();

    assertThat(result).hasSize(7);
    assertThat(result).extracting(RawJobOffer::id)
        .containsExactly("id-1", "id-3", "id-4", "id-6", "id-7", "id-8", "id-10");
  }

  @Test
  void markAsSeenDelegatesToRepository() {
    scraperService.markAsSeen(List.of("x", "y"));
    verify(repository).appendSeenIds(List.of("x", "y"));
  }
}
