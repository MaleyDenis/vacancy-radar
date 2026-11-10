package radar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StoredRawOffer(RawJobOffer offer, String firstSeenAt, String detailsFetchedAt) {

  public static StoredRawOffer fresh(RawJobOffer offer, String firstSeenAt) {
    return new StoredRawOffer(offer, firstSeenAt, null);
  }

  public boolean hasDetails() {
    return detailsFetchedAt != null;
  }

  public StoredRawOffer withDetails(String description, String domain, String fetchedAt) {
    return new StoredRawOffer(offer.withDetails(description, domain), firstSeenAt, fetchedAt);
  }
}
