package radar.web;

import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;
import radar.repository.JsonRepository;

/** Bulk data operations. */
@RestController
public class DataController {

  private final JsonRepository repository;

  public DataController(JsonRepository repository) {
    this.repository = repository;
  }

  /**
   * Deletes all data — raw offers and every snapshot (jobs + analytics) — keeping only the profile.
   * Returns {@code {"removed": N}} where N is the number of top-level entries removed.
   */
  @DeleteMapping("/api/data")
  public Map<String, Integer> deleteAll() {
    return Map.of("removed", repository.deleteAllData());
  }
}
