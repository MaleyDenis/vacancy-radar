package radar.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Safety net after Claude for skill lists: strips versions ("C++17" → "C++", "Java 21" → "Java"),
 * drops non-technology words (Jira, Agile, "databases", …) and case-insensitive duplicates.
 */
final class SkillNames {

  /** Lower-case names that are not technologies; extend as junk shows up in extractions. */
  private static final Set<String> NOT_SKILLS = Set.of(
      "jira", "confluence", "git", "agile", "scrum", "kanban", "databases", "database",
      "sql databases", "relational databases", "nosql databases", "cloud", "code review",
      "testing", "unit testing", "design patterns", "mentoring", "technical mentoring",
      "e-commerce", "fintech", "english", "polish", "itil", "intellij idea", "intellij");

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
      return glued.group(1);
    }
    return SPACED_VERSION.matcher(name).replaceFirst("");
  }
}
