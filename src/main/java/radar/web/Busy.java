package radar.web;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** HTTP answers for background-task endpoints. */
final class Busy {

  private Busy() {
  }

  static ResponseEntity<Map<String, Object>> started() {
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("started", true));
  }

  static ResponseEntity<Map<String, Object>> conflict() {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("error", "A scan or rescore is already running"));
  }
}
