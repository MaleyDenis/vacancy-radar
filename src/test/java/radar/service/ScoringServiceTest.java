package radar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import radar.model.OfferScore;
import radar.model.RawJobOffer;
import radar.model.StoredRawOffer;
import radar.model.UserProfile;
import radar.repository.JsonRepository;

class ScoringServiceTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final UserProfile profile = new UserProfile(
      List.of("Java"), 5, "B2B", true, 20000, "PLN", "B2B", List.of("Remote"), 6);

  private ClaudeClient claude;
  private JsonRepository repository;
  private ScoringService service;

  @BeforeEach
  void setUp() {
    claude = mock(ClaudeClient.class);
    repository = mock(JsonRepository.class);
    service = new ScoringService(claude, repository, new ProfileHasher(mapper), mapper,
        new Progress());
    when(repository.readProfile()).thenReturn(profile);
  }

  private StoredRawOffer stored(String id, String description) {
    return stored(id, description, "2026-11-01");
  }

  private StoredRawOffer stored(String id, String description, String publishedAt) {
    var offer = new RawJobOffer(id, "Java Dev", "Acme", null, "Kraków", true, publishedAt,
        "senior", description, null, null, null, null, null, "b2b", "url", "justjoin", null);
    return new StoredRawOffer(offer, "2026-11-01T00:00:00Z", null);
  }

  @SuppressWarnings("unchecked")
  private Map<String, OfferScore> saved() {
    var captor = ArgumentCaptor.forClass(Map.class);
    verify(repository).saveScores(captor.capture());
    return captor.getValue();
  }

  @Test
  void scoresOnlyUnscoredOffersWithDescription() {
    var existing = new LinkedHashMap<String, OfferScore>();
    existing.put("done", new OfferScore(5, "old", "other-profile", "t"));
    when(repository.readScores()).thenReturn(existing);
    when(repository.readRawOffers()).thenReturn(List.of(
        stored("done", "x"), stored("new", "Kafka job"), stored("nodesc", " ")));
    when(claude.ask(contains("Kafka job"), eq(ScoreResult.class)))
        .thenReturn(new ScoreResult(42, "Great fit"));

    var result = service.scoreMissing(null);

    assertThat(result.scored()).isEqualTo(1);
    assertThat(result.remaining()).isZero();
    var scores = saved();
    assertThat(scores.get("done").reason()).isEqualTo("old"); // stale score left alone
    var fresh = scores.get("new");
    assertThat(fresh.matchScore()).isEqualTo(10); // clamped
    assertThat(fresh.reason()).isEqualTo("Great fit");
    assertThat(fresh.profileHash()).isEqualTo(new ProfileHasher(mapper).hash(profile));
  }

  @Test
  void limitAndFailuresLeaveOffersForLater() {
    when(repository.readScores()).thenReturn(new LinkedHashMap<>());
    when(repository.readRawOffers()).thenReturn(List.of(
        stored("fail", "boom"), stored("ok", "fine"), stored("later", "wait")));
    when(claude.ask(contains("boom"), any())).thenThrow(new IllegalStateException("api down"));
    when(claude.ask(contains("fine"), eq(ScoreResult.class))).thenReturn(new ScoreResult(6, "ok"));

    var result = service.scoreMissing(2); // limit counts attempts, failures included

    assertThat(result.scored()).isEqualTo(1);
    assertThat(result.remaining()).isEqualTo(2);
    assertThat(saved()).containsOnlyKeys("ok");
    verify(claude, never()).ask(contains("wait"), any());
  }

  @Test
  void rescoreReplacesScoresOfRecentOffersOnly() {
    var recent = Instant.now().minus(2, ChronoUnit.DAYS).toString();
    var old = Instant.now().minus(20, ChronoUnit.DAYS).toString();
    var existing = new LinkedHashMap<String, OfferScore>();
    existing.put("recent", new OfferScore(3, "stale", "other", "t"));
    existing.put("old", new OfferScore(4, "keep", "other", "t"));
    when(repository.readScores()).thenReturn(existing);
    when(repository.readRawOffers()).thenReturn(List.of(
        stored("recent", "new text", recent), stored("old", "old text", old),
        stored("undated", "text", null), stored("fresh", "fresh text", recent)));
    when(claude.ask(contains("new text"), eq(ScoreResult.class)))
        .thenReturn(new ScoreResult(8, "now fits"));
    when(claude.ask(contains("fresh text"), eq(ScoreResult.class)))
        .thenThrow(new IllegalStateException("api down"));

    var result = service.rescoreRecent(7);

    assertThat(result.scored()).isEqualTo(1);
    assertThat(result.remaining()).isEqualTo(1);
    var scores = saved();
    assertThat(scores.get("recent").matchScore()).isEqualTo(8);
    assertThat(scores.get("old").reason()).isEqualTo("keep");
    assertThat(scores).doesNotContainKeys("undated", "fresh");
  }

  @Test
  void minMatchScoreDoesNotAffectHashOrPrompt() {
    var hasher = new ProfileHasher(mapper);
    var stricter = new UserProfile(profile.currentSkills(), profile.experienceYears(),
        profile.preferredType(), profile.preferredRemote(), profile.salaryMin(), profile.currency(),
        profile.contractType(), profile.locations(), 9);
    var offer = stored("a", "text").offer();
    assertThat(hasher.hash(stricter)).isEqualTo(hasher.hash(profile));
    assertThat(service.prompt(offer, stricter)).isEqualTo(service.prompt(offer, profile))
        .doesNotContain("minMatchScore\":6");
  }

  @Test
  void promptHasProfileAndPlainOfferText() {
    var prompt = service.prompt(stored("a", "Plain text").offer(), profile);
    assertThat(prompt).contains("CANDIDATE PROFILE").contains("\"currentSkills\":[\"Java\"]")
        .contains("Description: Plain text").doesNotContain("<");
  }
}
