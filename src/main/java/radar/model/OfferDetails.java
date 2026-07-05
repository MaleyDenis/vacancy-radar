package radar.model;

/**
 * The "as written" details lifted from an offer's own page (no AI): the full job description text and
 * the hiring company's domain. Either field may be {@code null} if the page didn't carry it.
 */
public record OfferDetails(String description, String domain) {
}
