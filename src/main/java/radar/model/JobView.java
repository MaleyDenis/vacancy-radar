package radar.model;

// snippet: the description around a search hit, only in search results
public record JobView(RawJobOffer offer, OfferExtraction extraction, OfferScore score,
    boolean stale, String snippet) {

  public JobView(RawJobOffer offer, OfferExtraction extraction, OfferScore score, boolean stale) {
    this(offer, extraction, score, stale, null);
  }
}
