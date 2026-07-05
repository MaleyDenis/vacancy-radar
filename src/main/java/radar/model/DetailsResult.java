package radar.model;

/**
 * Outcome of the details step: how many offers had their page details fetched this run, and how many
 * still lack details afterwards (skipped by the limit or left after a fetch failure).
 */
public record DetailsResult(int fetched, int remaining) {
}
