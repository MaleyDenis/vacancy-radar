package radar.model;

/** Outcome of a scoring run: offers scored this run, and offers still waiting for a score. */
public record ScoringResult(int scored, int remaining) {
}
