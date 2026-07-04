package radar.web;

import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import radar.service.ScanService;

/**
 * Starts a scan and streams progress back over Server-Sent Events. The emitter is handed straight to
 * {@link ScanService}; there is no polling endpoint.
 */
@RestController
public class ScanController {

  private static final long SCAN_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

  private final ScanService scanService;

  public ScanController(ScanService scanService) {
    this.scanService = scanService;
  }

  @PostMapping("/api/scan")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public SseEmitter scan() {
    SseEmitter emitter = new SseEmitter(SCAN_TIMEOUT_MS);
    scanService.startScan(emitter);
    return emitter;
  }
}
