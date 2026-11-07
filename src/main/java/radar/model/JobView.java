package radar.model;

/**
 * One offer as returned by {@code GET /api/jobs}: the raw offer joined with its extraction and score
 * by offer id. {@code extraction} may be null (not extracted yet); {@code stale} is true when the
 * score was made for a different profile than the current one.
 */
public record JobView(RawJobOffer offer, OfferExtraction extraction, OfferScore score,
    boolean stale) {
}
