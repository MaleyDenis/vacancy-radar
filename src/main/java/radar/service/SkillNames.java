package radar.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

// also applied when analytics reads extractions, so editing these lists needs no re-extraction
final class SkillNames {

  private static final Set<String> NOT_SKILLS = Set.of(
      "jira", "confluence", "git", "agile", "scrum", "kanban", "databases", "database",
      "sql databases", "relational databases", "nosql databases", "cloud", "code review",
      "testing", "unit testing", "design patterns", "mentoring", "technical mentoring",
      "e-commerce", "fintech", "english", "polish", "itil", "intellij idea", "intellij");

  private static final Map<String, String> SYNONYMS = Map.ofEntries(
      Map.entry("apache kafka", "Kafka"),
      Map.entry("kafka streams", "Kafka Streams"),
      Map.entry("postgres", "PostgreSQL"),
      Map.entry("postgresql", "PostgreSQL"),
      Map.entry("k8s", "Kubernetes"),
      Map.entry("amazon web services", "AWS"),
      Map.entry("aws", "AWS"),
      Map.entry("microsoft azure", "Azure"),
      Map.entry("google cloud platform", "GCP"),
      Map.entry("google cloud", "GCP"),
      Map.entry("rest api", "REST"),
      Map.entry("rest apis", "REST"),
      Map.entry("restful api", "REST"),
      Map.entry("restful apis", "REST"),
      Map.entry("restful", "REST"),
      Map.entry("spring framework", "Spring"),
      Map.entry("ms sql", "MS SQL"),
      Map.entry("mssql", "MS SQL"),
      Map.entry("sql server", "MS SQL"),
      Map.entry("microsoft sql server", "MS SQL"),
      Map.entry("mongo", "MongoDB"),
      Map.entry("mongodb", "MongoDB"),
      Map.entry("ci/cd", "CI/CD"),
      Map.entry("jenkins ci", "Jenkins"),
      Map.entry("gitlab ci/cd", "GitLab CI"),
      Map.entry("gitlab ci", "GitLab CI"),
      Map.entry("elastic search", "Elasticsearch"),
      Map.entry("elasticsearch", "Elasticsearch"),
      Map.entry("rabbit mq", "RabbitMQ"),
      Map.entry("rabbitmq", "RabbitMQ"));

  /** "Java 21", "Spring Boot 3", "Java 11+", "Python 3.12", "Angular v17". */
  private static final Pattern SPACED_VERSION = Pattern.compile("\\s+v?\\d+(\\.\\d+)*(\\.x)?\\+?$");
  /** "C++17", "C++20", "C#10". */
  private static final Pattern GLUED_VERSION = Pattern.compile("^(C\\+\\+|C#)\\d+\\+?$");

  private SkillNames() {
  }

  static List<String> clean(List<String> skills) {
    var seen = new HashSet<String>();
    var result = new ArrayList<String>();
    for (var skill : skills) {
      var name = canonical(skill);
      var key = name.toLowerCase(Locale.ROOT);
      if (!name.isEmpty() && !NOT_SKILLS.contains(key) && seen.add(key)) {
        result.add(name);
      }
    }
    return result;
  }

  static String canonical(String skill) {
    var name = skill == null ? "" : skill.strip();
    var glued = GLUED_VERSION.matcher(name);
    if (glued.matches()) {
      name = glued.group(1);
    }
    name = SPACED_VERSION.matcher(name).replaceFirst("");
    return SYNONYMS.getOrDefault(name.toLowerCase(Locale.ROOT), name);
  }
}
