package radar.web;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import radar.service.AnalyticsService;
import radar.service.ReporterService;

/** Analytics dashboard ({@code /api/analytics}) and legacy skill trends ({@code /api/trends}). */
@RestController
public class AnalyticsController {

  private final ReporterService reporterService;
  private final AnalyticsService analyticsService;

  public AnalyticsController(ReporterService reporterService, AnalyticsService analyticsService) {
    this.reporterService = reporterService;
    this.analyticsService = analyticsService;
  }

  /**
   * Dashboard statistics for one domain ({@code all}, {@code finance}, {@code insurance}, …),
   * computed from the JSON files without AI. 400 for an unknown domain.
   */
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
