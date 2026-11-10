package radar.service;

import java.util.List;
import java.util.Locale;

public enum Domain {
  // order matters: the first matching group wins (insurance before finance)
  INSURANCE("insurance", "Страхование", List.of("insur")),
  FINANCE("finance", "Банки и финансы", List.of("bank", "fintech", "financ", "capital market",
      "trading", "asset", "payment", "lending", "credit", "fraud", "risk", "invest", "wealth",
      "compliance")),
  RETAIL("retail", "Ритейл и e-commerce", List.of("retail", "commerce", "food delivery",
      "marketplace")),
  IT("it", "IT-услуги и SaaS", List.of("it services", "consult", "saas", "software",
      "developer tools", "it ")),
  OTHER("other", "Прочее", List.of()),
  UNKNOWN("unknown", "Не указано", List.of());

  private final String key;
  private final String label;
  private final List<String> keywords;

  Domain(String key, String label, List<String> keywords) {
    this.key = key;
    this.label = label;
    this.keywords = keywords;
  }

  public String key() {
    return key;
  }

  public String label() {
    return label;
  }

  public static Domain of(String industry) {
    if (industry == null || industry.isBlank()) {
      return UNKNOWN;
    }
    var text = industry.toLowerCase(Locale.ROOT) + " ";
    for (var domain : values()) {
      if (domain.keywords.stream().anyMatch(text::contains)) {
        return domain;
      }
    }
    return OTHER;
  }

  public static Domain byKey(String key) {
    for (var domain : values()) {
      if (domain.key.equals(key)) {
        return domain;
      }
    }
    return null;
  }
}
