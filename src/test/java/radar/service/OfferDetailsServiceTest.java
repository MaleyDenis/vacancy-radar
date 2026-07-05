package radar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import radar.connector.JustJoinConnector;
import radar.connector.OfferDetailsParser;
import radar.model.OfferDetails;
import radar.model.RawJobOffer;
import radar.model.StoredRawOffer;
import radar.repository.JsonRepository;

@ExtendWith(MockitoExtension.class)
class OfferDetailsServiceTest {

  @Mock
  JustJoinConnector connector;

  @Mock
  OfferDetailsParser parser;

  @Mock
  JsonRepository repository;

  OfferDetailsService service;

  @BeforeEach
  void setUp() {
    // override the polite sleep so the test doesn't actually wait
    service = new OfferDetailsService(connector, parser, repository) {
      @Override
      void sleepPolitely() {
      }
    };
  }

  private RawJobOffer offer(String id) {
    return new RawJobOffer(id, "Java Dev", "Acme", null, "Kraków", true, "2026-07-04", "mid",
        null, null, null, null, "b2b", "https://justjoin.it/job-offer/" + id,
        "justjoin", null);
  }

  private String url(String id) {
    return "https://justjoin.it/job-offer/" + id;
  }

  private StoredRawOffer pending(String id) {
    return StoredRawOffer.fresh(offer(id), "2026-07-05T10:00:00Z");
  }

  private StoredRawOffer withDetails(String id) {
    return new StoredRawOffer(offer(id).withDetails("existing", "acme.com"),
        "2026-07-05T10:00:00Z", "2026-07-05T11:00:00Z");
  }

  @SuppressWarnings("unchecked")
  private List<StoredRawOffer> capturePersisted() {
    ArgumentCaptor<List<StoredRawOffer>> captor = ArgumentCaptor.forClass(List.class);
    verify(repository).saveRawOffers(captor.capture());
    return captor.getValue();
  }

  @Test
  void fillsDescriptionAndDomainForOffersLackingDetails() {
    when(repository.readRawOffers()).thenReturn(List.of(pending("a"), pending("b")));
    when(connector.fetchOfferPageHtml(url("a"))).thenReturn("<a>");
    when(connector.fetchOfferPageHtml(url("b"))).thenReturn("<b>");
    when(parser.parse("<a>")).thenReturn(new OfferDetails("desc a", "a.com"));
    when(parser.parse("<b>")).thenReturn(new OfferDetails("desc b", "b.com"));

    var result = service.fetchMissing(null);

    assertThat(result.fetched()).isEqualTo(2);
    assertThat(result.remaining()).isEqualTo(0);
    var saved = capturePersisted();
    assertThat(saved).allSatisfy(s -> assertThat(s.hasDetails()).isTrue());
    assertThat(saved.get(0).offer().description()).isEqualTo("desc a");
    assertThat(saved.get(0).offer().domain()).isEqualTo("a.com");
  }

  @Test
  void skipsOffersThatAlreadyHaveDetails() {
    when(repository.readRawOffers()).thenReturn(List.of(withDetails("a"), pending("b")));
    when(connector.fetchOfferPageHtml(url("b"))).thenReturn("<b>");
    when(parser.parse("<b>")).thenReturn(new OfferDetails("desc b", "b.com"));

    var result = service.fetchMissing(null);

    assertThat(result.fetched()).isEqualTo(1);
    assertThat(result.remaining()).isEqualTo(0);
    verify(connector, never()).fetchOfferPageHtml(url("a"));
  }

  @Test
  void limitCapsHowManyDetailsAreFetched() {
    when(repository.readRawOffers()).thenReturn(List.of(pending("a"), pending("b"), pending("c")));
    when(connector.fetchOfferPageHtml(url("a"))).thenReturn("<a>");
    when(parser.parse("<a>")).thenReturn(new OfferDetails("desc a", "a.com"));

    var result = service.fetchMissing(1);

    assertThat(result.fetched()).isEqualTo(1);
    assertThat(result.remaining()).isEqualTo(2);
    verify(connector, never()).fetchOfferPageHtml(url("b"));
    verify(connector, never()).fetchOfferPageHtml(url("c"));
  }

  @Test
  void failedFetchLeavesOfferForALaterRun() {
    when(repository.readRawOffers()).thenReturn(List.of(pending("a")));
    when(connector.fetchOfferPageHtml(url("a"))).thenThrow(new IllegalStateException("boom"));

    var result = service.fetchMissing(null);

    assertThat(result.fetched()).isEqualTo(0);
    assertThat(result.remaining()).isEqualTo(1);
    assertThat(capturePersisted().get(0).hasDetails()).isFalse();
  }
}
