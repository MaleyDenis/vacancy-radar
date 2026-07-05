package radar.model;

/**
 * A raw offer as persisted in {@code raw-offers.json}, together with the timestamp it was first
 * scraped. The wrapper keeps {@link RawJobOffer} itself profile-independent: enrichment (matchScore
 * and everything derived from a user profile) lives on {@code JobReport}, never here.
 *
 * @param offer       the scraped offer, unchanged
 * @param firstSeenAt ISO-8601 instant of the first scan that saw this offer; used for retention.
 *                    Stored as a String because the shared Jackson 2 {@code ObjectMapper} has no
 *                    java.time module registered.
 */
public record StoredRawOffer(RawJobOffer offer, String firstSeenAt) {
}
