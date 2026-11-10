package radar.web;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import radar.service.AnalyticsService;
import radar.service.ReporterService;

@RestController
public class AnalyticsController {

  private final ReporterService reporterService;
  private final AnalyticsService analyticsService;

  public AnalyticsController(ReporterService reporterService, AnalyticsService analyticsService) {
    this.reporterService = reporterService;
    this.analyticsService = analyticsService;
  }

  @GetMapping("/api/analytics")
  public ResponseEntity<?> analytics(
      @RequestParam(defaultValue = AnalyticsService.ALL) String domain) {
    try {
      return ResponseEntity.ok(analyticsService.dashboard(domain));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
  }

  @GetMapping("/api/trends")
  public Map<String, Integer> trends() {
    return reporterService.computeTrends();
  }
}
