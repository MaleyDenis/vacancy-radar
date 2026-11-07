package radar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A raw offer as persisted in {@code raw-offers.json}, with scrape bookkeeping only — nothing from
 * Claude lives here (see {@code extractions.json}, {@code scores.json}).
 *
 * @param offer            the scraped offer (its {@code description}/{@code domain} are null until
 *                         the details step fills them)
 * @param firstSeenAt      ISO-8601 instant of the first scan that saw this offer
 * @param detailsFetchedAt ISO-8601 instant the offer's page details were fetched, or {@code null} if
 *                         not fetched yet — the details step processes only null entries
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StoredRawOffer(RawJobOffer offer, String firstSeenAt, String detailsFetchedAt) {

  /** A just-scraped offer with no details fetched yet. */
  public static StoredRawOffer fresh(RawJobOffer offer, String firstSeenAt) {
    return new StoredRawOffer(offer, firstSeenAt, null);
  }

  /** Whether the offer's page details (full description, domain) have been fetched. */
  public boolean hasDetails() {
    return detailsFetchedAt != null;
  }

  /** Returns a copy with page details applied to the offer and {@code detailsFetchedAt} stamped. */
  public StoredRawOffer withDetails(String description, String domain, String fetchedAt) {
    return new StoredRawOffer(offer.withDetails(description, domain), firstSeenAt, fetchedAt);
  }
}
