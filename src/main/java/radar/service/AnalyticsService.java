package radar.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import radar.model.Dashboard;
import radar.model.Dashboard.Ai;
import radar.model.Dashboard.DomainCount;
import radar.model.Dashboard.Salary;
import radar.model.Dashboard.SalaryStat;
import radar.model.Dashboard.Share;
import radar.model.Dashboard.SkillStat;
import radar.model.OfferExtraction;
import radar.model.RawJobOffer;
import radar.repository.JsonRepository;

@Service
public class AnalyticsService {

  public static final String ALL = "all";
  static final int TOP_SKILLS = 30;
  static final int TOP_CITIES = 10;
  static final int TOP_AI_TOOLS = 8;
  static final int HOURS_PER_MONTH = 168;
  static final int DAYS_PER_MONTH = 21;

  private final JsonRepository repository;
  private final Map<String, Double> plnPer;

  public AnalyticsService(JsonRepository repository,
      @Value("${radar.rate.eur:4.3}") double eur,
      @Value("${radar.rate.usd:4.0}") double usd) {
    this.repository = repository;
    this.plnPer = Map.of("PLN", 1.0, "EUR", eur, "USD", usd);
  }

  record Item(RawJobOffer offer, OfferExtraction facts, Domain domain) {
  }

  public Dashboard dashboard(String domainKey) {
    var extractions = repository.readExtractions();
    var all = repository.readRawOffers().stream()
        .map(stored -> stored.offer())
        .filter(offer -> extractions.containsKey(offer.id()))
        .map(offer -> {
          var facts = extractions.get(offer.id());
          return new Item(offer, facts, Domain.of(facts.industry()));
        })
        .toList();
    var domain = ALL.equals(domainKey) ? null : Domain.byKey(domainKey);
    if (!ALL.equals(domainKey) && domain == null) {
      throw new IllegalArgumentException("Unknown domain " + domainKey);
    }
    var items = domain == null ? all : all.stream().filter(i -> i.domain() == domain).toList();

    var withSalary = (int) items.stream()
        .filter(i -> i.offer().salaryMin() != null || i.offer().salaryMax() != null).count();
    return new Dashboard(domainKey, items.size(), all.size(), domains(all), skills(items, all),
        withSalary, new Salary(salary(items, "b2b"), salary(items, "permanent")), ai(items),
        shares(items, i -> i.facts().workMode()),
        shares(items, i -> i.facts().companyType()),
        shares(items, i -> i.facts().englishLevel()),
        shares(items, i -> i.facts().polishRequired() == null ? null
            : i.facts().polishRequired().toString()),
        cities(items));
  }

  private static List<DomainCount> domains(List<Item> all) {
    var counts = new LinkedHashMap<Domain, Integer>();
    for (var item : all) {
      counts.merge(item.domain(), 1, Integer::sum);
    }
    return counts.entrySet().stream()
        .sorted(Map.Entry.<Domain, Integer>comparingByValue().reversed())
        .map(e -> new DomainCount(e.getKey().key(), e.getKey().label(), e.getValue()))
        .toList();
  }

  private static final class SkillCounter {
    final Map<String, Integer> offers = new HashMap<>();
    final Map<String, Map<String, Integer>> spellings = new HashMap<>();

    void add(Collection<String> skills) {
      for (var name : skills) {
        var key = name.toLowerCase(Locale.ROOT);
        offers.merge(key, 1, Integer::sum);
        spellings.computeIfAbsent(key, k -> new HashMap<>()).merge(name, 1, Integer::sum);
      }
    }

    int get(String key) {
      return offers.getOrDefault(key, 0);
    }

    String display(String key) {
      return spellings.get(key).entrySet().stream()
          .max(Map.Entry.comparingByValue()).orElseThrow().getKey();
    }
  }

  private static List<String> cleaned(List<String> skills) {
    return skills == null ? List.of() : SkillNames.clean(skills);
  }

  private static List<SkillStat> skills(List<Item> items, List<Item> all) {
    var must = new SkillCounter();
    var nice = new SkillCounter();
    for (var item : items) {
      var required = cleaned(item.facts().skills());
      var requiredKeys = new HashSet<String>();
      required.forEach(s -> requiredKeys.add(s.toLowerCase(Locale.ROOT)));
      must.add(required);
      // a skill both required and "a plus" (old extractions) counts as required only
      nice.add(cleaned(item.facts().niceToHaveSkills()).stream()
          .filter(s -> !requiredKeys.contains(s.toLowerCase(Locale.ROOT))).toList());
    }
    var market = new SkillCounter();
    for (var item : all) {
      market.add(cleaned(item.facts().skills()));
    }
    var keys = new HashSet<>(must.offers.keySet());
    keys.addAll(nice.offers.keySet());
    return keys.stream()
        .sorted(Comparator.comparingInt((String k) -> must.get(k)).reversed()
            .thenComparing(Comparator.comparingInt((String k) -> nice.get(k)).reversed())
            .thenComparing(Comparator.naturalOrder()))
        .limit(TOP_SKILLS)
        .map(k -> new SkillStat(must.offers.containsKey(k) ? must.display(k) : nice.display(k),
            must.get(k), nice.get(k), market.get(k)))
        .toList();
  }

  Double monthlyPln(RawJobOffer offer) {
    var rate = offer.currency() == null ? null : plnPer.get(offer.currency());
    var perMonth = perMonth(offer.salaryUnit());
    if (rate == null || perMonth == null) {
      return null;
    }
    var values = new ArrayList<Integer>();
    if (offer.salaryMin() != null) {
      values.add(offer.salaryMin());
    }
    if (offer.salaryMax() != null) {
      values.add(offer.salaryMax());
    }
    if (values.isEmpty()) {
      return null;
    }
    var mid = values.stream().mapToInt(Integer::intValue).average().orElseThrow();
    return mid * perMonth * rate;
  }

  private static Double perMonth(String unit) {
    if (unit == null) {
      return null;
    }
    return switch (unit) {
      case "hour" -> (double) HOURS_PER_MONTH;
      case "day" -> (double) DAYS_PER_MONTH;
      case "month" -> 1.0;
      case "year" -> 1.0 / 12;
      default -> null;
    };
  }

  private SalaryStat salary(List<Item> items, String contract) {
    var values = items.stream()
        .filter(i -> contract.equals(i.offer().contractType()))
        .map(i -> monthlyPln(i.offer()))
        .filter(Objects::nonNull)
        .sorted()
        .toList();
    if (values.isEmpty()) {
      return null;
    }
    return new SalaryStat(values.size(), round(percentile(values, 0.5)),
        round(percentile(values, 0.25)), round(percentile(values, 0.75)));
  }

  static double percentile(List<Double> sorted, double p) {
    var pos = p * (sorted.size() - 1);
    var lower = (int) Math.floor(pos);
    var upper = (int) Math.ceil(pos);
    return sorted.get(lower) + (sorted.get(upper) - sorted.get(lower)) * (pos - lower);
  }

  private static int round(double value) {
    return (int) Math.round(value / 100) * 100;
  }

  private static Ai ai(List<Item> items) {
    var mentioning = items.stream()
        .filter(i -> i.facts().aiTools() != null && !i.facts().aiTools().isEmpty())
        .toList();
    var tools = new LinkedHashMap<String, Integer>();
    for (var item : mentioning) {
      new HashSet<>(item.facts().aiTools()).forEach(t -> tools.merge(t, 1, Integer::sum));
    }
    return new Ai(mentioning.size(), usage(mentioning, "required"),
        usage(mentioning, "preferred"), usage(mentioning, "used"), top(tools, TOP_AI_TOOLS));
  }

  private static int usage(List<Item> items, String value) {
    return (int) items.stream().filter(i -> value.equals(i.facts().aiToolsUsage())).count();
  }

  private static List<Share> shares(List<Item> items, Function<Item, String> field) {
    var counts = new HashMap<String, Integer>();
    var unknown = 0;
    for (var item : items) {
      var value = field.apply(item);
      if (value == null) {
        unknown++;
      } else {
        counts.merge(value, 1, Integer::sum);
      }
    }
    var result = new ArrayList<>(top(counts, Integer.MAX_VALUE));
    if (unknown > 0) {
      result.add(new Share(null, unknown));
    }
    return result;
  }

  private static List<Share> cities(List<Item> items) {
    var counts = new HashMap<String, Integer>();
    for (var item : items) {
      new HashSet<>(item.offer().allLocations()).forEach(c -> counts.merge(c, 1, Integer::sum));
    }
    return top(counts, TOP_CITIES);
  }

  private static List<Share> top(Map<String, Integer> counts, int limit) {
    return counts.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
            .thenComparing(Map.Entry.comparingByKey()))
        .limit(limit)
        .map(e -> new Share(e.getKey(), e.getValue()))
        .toList();
  }
}
