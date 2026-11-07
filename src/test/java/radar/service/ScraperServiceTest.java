package radar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
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
import radar.connector.JustJoinConnector.OfferPage;
import radar.model.RawJobOffer;
import radar.model.ScanResult;
import radar.model.StoredRawOffer;
import radar.repository.JsonRepository;

@ExtendWith(MockitoExtension.class)
class ScraperServiceTest {

  @Mock
  JustJoinConnector connector;

  @Mock
  JsonRepository repository;

  ScraperService scraperService;

  @BeforeEach
  void setUp() {
    scraperService = new ScraperService(connector, repository);
  }

  private RawJobOffer offer(String id) {
    return new RawJobOffer(id, "Java Dev", "Acme", null, "Kraków", true, "2026-07-04", "mid",
        null, null, null, null, null, null, "b2b", "https://justjoin.it/job-offer/" + id,
        "justjoin", null);
  }

  private StoredRawOffer stored(String id, String firstSeenAt) {
    return new StoredRawOffer(offer(id), firstSeenAt, null);
  }

  private OfferPage page(boolean hasNext, String... ids) {
    return new OfferPage(IntStream.range(0, ids.length).mapToObj(i -> offer(ids[i])).toList(),
        hasNext);
  }

  @SuppressWarnings("unchecked")
  private List<StoredRawOffer> capturePersisted() {
    ArgumentCaptor<List<StoredRawOffer>> captor = ArgumentCaptor.forClass(List.class);
    verify(repository).saveRawOffers(captor.capture());
    return captor.getValue();
  }

  @Test
  void scanAddsBrandNewOffersToEmptyPool() {
    when(repository.readRawOffers()).thenReturn(List.of());
    when(connector.fetchPage(1)).thenReturn(page(false, "a", "b"));

    ScanResult result = scraperService.scan(false, null);

    assertThat(result).isEqualTo(new ScanResult(2, 2));
    assertThat(capturePersisted()).extracting(s -> s.offer().id()).containsExactly("a", "b");
  }

  @Test
  void scanPreservesFirstSeenForOffersAlreadyInPool() {
    String seen = Instant.now().minus(5, ChronoUnit.DAYS).toString();
    when(repository.readRawOffers()).thenReturn(List.of(stored("a", seen)));
    when(connector.fetchPage(1)).thenReturn(page(false, "a", "b"));

    ScanResult result = scraperService.scan(true, null);

    assertThat(result).isEqualTo(new ScanResult(1, 2));
    assertThat(capturePersisted()).filteredOn(s -> s.offer().id().equals("a"))
        .allSatisfy(s -> assertThat(s.firstSeenAt()).isEqualTo(seen));
  }

  @Test
  void scanStopsEarlyOnceAPageBringsNothingNew() {
    when(repository.readRawOffers()).thenReturn(List.of(stored("x", Instant.now().toString())));
    when(connector.fetchPage(1)).thenReturn(page(true, "n1"));   // n1 is new -> keep going
    when(connector.fetchPage(2)).thenReturn(page(true, "x"));    // all known -> stop

    ScanResult result = scraperService.scan(false, null);

    assertThat(result).isEqualTo(new ScanResult(1, 2));
    verify(connector).fetchPage(1);
    verify(connector).fetchPage(2);
    verify(connector, never()).fetchPage(3);
  }

  @Test
  void fullFetchKeepsWalkingPastAnAllKnownPage() {
    when(repository.readRawOffers()).thenReturn(List.of(stored("x", Instant.now().toString())));
    when(connector.fetchPage(1)).thenReturn(page(true, "x"));    // all known, but fullFetch -> continue
    when(connector.fetchPage(2)).thenReturn(page(false, "n1"));

    ScanResult result = scraperService.scan(true, null);

    assertThat(result).isEqualTo(new ScanResult(1, 2));
    verify(connector).fetchPage(2);
  }

  @Test
  void limitCapsHowManyOffersAreProcessed() {
    when(repository.readRawOffers()).thenReturn(List.of());
    when(connector.fetchPage(1)).thenReturn(page(false, "a", "b", "c"));

    ScanResult result = scraperService.scan(false, 2);

    assertThat(result).isEqualTo(new ScanResult(2, 2));
    assertThat(capturePersisted()).extracting(s -> s.offer().id()).containsExactly("a", "b");
    verify(connector, never()).fetchPage(2);
  }

  @Test
  void scanKeepsOldOffers() {
    var old = Instant.now().minus(400, ChronoUnit.DAYS).toString();
    var recent = Instant.now().minus(2, ChronoUnit.DAYS).toString();
    when(repository.readRawOffers())
        .thenReturn(List.of(stored("old", old), stored("recent", recent)));
    when(connector.fetchPage(1)).thenReturn(new OfferPage(List.of(), false));

    var result = scraperService.scan(true, null);

    assertThat(result).isEqualTo(new ScanResult(0, 2));
    assertThat(capturePersisted()).extracting(s -> s.offer().id()).containsExactly("old", "recent");
  }

}
