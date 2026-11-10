package radar.model;

import java.util.List;

public record Dashboard(
    String domain,
    int total,
    int marketTotal,
    List<DomainCount> domains,
    List<SkillStat> skills,
    int withSalary,
    Salary salary,
    Ai ai,
    List<Share> workMode,
    List<Share> companyType,
    List<Share> englishLevel,
    List<Share> polishRequired,
    List<Share> cities
) {

  public record DomainCount(String key, String label, int count) {
  }

  public record SkillStat(String name, int must, int nice, int marketMust) {
  }

  public record Salary(SalaryStat b2b, SalaryStat permanent) {
  }

  public record SalaryStat(int count, int median, int p25, int p75) {
  }

  public record Ai(int mentioned, int required, int preferred, int used, List<Share> tools) {
  }

  // value null = not stated
  public record Share(String value, int count) {
  }
}
