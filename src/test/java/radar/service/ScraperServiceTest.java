package radar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import radar.connector.JustJoinConnector;
import radar.model.RawJobOffer;
import radar.repository.JsonRepository;

@ExtendWith(MockitoExtension.class)
class ScraperServiceTest {

  @Mock
  JustJoinConnector connector;

  @Mock
  JsonRepository repository;

  @InjectMocks
  ScraperService scraperService;

  private RawJobOffer offer(String id) {
    return new RawJobOffer(id, "Java Dev", "Acme", null, "Kraków", true, "2026-07-04", "mid",
        null, List.of("Java"), null, null, null, "b2b", "https://justjoin.it/job-offer/" + id,
        "justjoin");
  }

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
  void scrapeNewReturnsAllWhenNothingSeen() {
    when(connector.fetchAll()).thenReturn(List.of(offer("a"), offer("b")));
    when(repository.readSeenIds()).thenReturn(List.of());

    assertThat(scraperService.scrapeNew()).extracting(RawJobOffer::id).containsExactly("a", "b");
  }

  @Test
  void markAsSeenDelegatesToRepository() {
    scraperService.markAsSeen(List.of("x", "y"));
    verify(repository).appendSeenIds(List.of("x", "y"));
  }
}
