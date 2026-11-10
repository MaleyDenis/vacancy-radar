package radar.web;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;
import radar.repository.JsonRepository;
import radar.service.BackgroundRunner;

@RestController
public class DataController {

  private final JsonRepository repository;
  private final BackgroundRunner runner;

  public DataController(JsonRepository repository, BackgroundRunner runner) {
    this.repository = repository;
    this.runner = runner;
  }

  @DeleteMapping("/api/data")
  public ResponseEntity<Map<String, Object>> deleteAll() {
    if (runner.isRunning()) {
      return Busy.conflict();
    }
    return ResponseEntity.ok(Map.of("removed", repository.deleteAllData()));
  }
}
