package radar.model;

/** Outcome of the extract step: offers extracted this run, and offers still waiting for it. */
public record ExtractionResult(int extracted, int remaining) {
}
