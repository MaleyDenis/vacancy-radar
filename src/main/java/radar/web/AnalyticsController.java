package radar.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import radar.service.ReporterService;

/** Exposes cross-snapshot skill trends. */
@RestController
public class AnalyticsController {

  private final ReporterService reporterService;

  public AnalyticsController(ReporterService reporterService) {
    this.reporterService = reporterService;
  }

  @GetMapping("/api/trends")
  public Map<String, Integer> trends() {
    return reporterService.computeTrends();
  }
}
