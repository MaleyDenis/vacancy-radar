package radar.model;

/**
 * Outcome of the evaluate step: how many offers were scored by Claude this run, and how many reports
 * exist for the current profile afterwards.
 */
public record EvaluationResult(int evaluated, int totalReports) {
}
