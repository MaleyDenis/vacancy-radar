package radar.model;

import java.util.List;

/**
 * {@code GET /api/analytics}: statistics over extracted offers of one domain (or all), computed on
 * the fly from the JSON files — no AI. Counts are numbers of offers; percentages are left to the UI.
 *
 * @param domain      selected domain key ({@code all}, {@code finance}, …)
 * @param total       offers (with an extraction) in the domain
 * @param marketTotal offers (with an extraction) overall, for skill comparison
 * @param domains     every domain with its offer count, biggest first
 * @param skills      top skills of the domain
 * @param withSalary  offers in the domain that state a salary
 * @param salary      monthly PLN salary stats, by contract
 * @param cities      offers per city (a multi-city offer counts in each city)
 */
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

  /**
   * @param must       offers in the domain where the skill is required
   * @param nice       offers in the domain where it is nice to have
   * @param marketMust offers overall where it is required
   */
  public record SkillStat(String name, int must, int nice, int marketMust) {
  }

  /** Per contract type; null when no offer of that type states a salary. */
  public record Salary(SalaryStat b2b, SalaryStat permanent) {
  }

  /** Monthly PLN: median and the middle half (25th–75th percentile) of offers' mid salaries. */
  public record SalaryStat(int count, int median, int p25, int p75) {
  }

  /**
   * @param mentioned offers that mention AI coding tools
   * @param required  …where they are required
   * @param preferred …where they are a plus
   * @param tools     most mentioned tools
   */
  public record Ai(int mentioned, int required, int preferred, int used, List<Share> tools) {
  }

  /** A value and how many offers have it; {@code value} null = not stated. */
  public record Share(String value, int count) {
  }
}
