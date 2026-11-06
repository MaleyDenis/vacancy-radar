package radar.model;

/**
 * A raw offer as persisted in {@code raw-offers.json}, together with scan/detail/evaluation
 * bookkeeping. The wrapper keeps {@link RawJobOffer} itself profile-independent; per-offer processing
 * status (details fetched, evaluated under which profile) lives here.
 *
 * @param offer                the scraped offer (its {@code description}/{@code domain} are null
 *                             until the details step fills them)
 * @param firstSeenAt          ISO-8601 instant of the first scan that saw this offer; used for
 *                             retention
 * @param detailsFetchedAt     ISO-8601 instant the offer's page details were fetched, or {@code null}
 *                             if not fetched yet — the details step processes only null entries
 * @param evaluatedProfileHash the profile hash this offer was last scored under, or {@code null} if
 *                             never — the evaluate step re-scores when this differs from the current
 *                             profile hash
 */
public record StoredRawOffer(RawJobOffer offer, String firstSeenAt, String detailsFetchedAt,
    String evaluatedProfileHash) {

  /** A just-scraped offer with no details fetched and no evaluation yet. */
  public static StoredRawOffer fresh(RawJobOffer offer, String firstSeenAt) {
    return new StoredRawOffer(offer, firstSeenAt, null, null);
  }

  /** Whether the offer's page details (full description, domain) have been fetched. */
  public boolean hasDetails() {
    return detailsFetchedAt != null;
  }

  /** Whether this offer has already been scored under the given profile hash. */
  public boolean isEvaluatedFor(String profileHash) {
    return profileHash != null && profileHash.equals(evaluatedProfileHash);
  }

  /** Returns a copy with page details applied to the offer and {@code detailsFetchedAt} stamped. */
  public StoredRawOffer withDetails(String description, String domain, String fetchedAt) {
    return new StoredRawOffer(offer.withDetails(description, domain), firstSeenAt, fetchedAt,
        evaluatedProfileHash);
  }

  /** Returns a copy stamped as evaluated under {@code profileHash}. */
  public StoredRawOffer withEvaluatedProfileHash(String profileHash) {
    return new StoredRawOffer(offer, firstSeenAt, detailsFetchedAt, profileHash);
  }
}
