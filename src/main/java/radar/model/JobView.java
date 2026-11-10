package radar.model;

public record JobView(RawJobOffer offer, OfferExtraction extraction, OfferScore score,
    boolean stale) {
}
