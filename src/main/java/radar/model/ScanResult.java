package radar.model;

/**
 * Outcome of a scrape: how many brand-new offers were added to {@code raw-offers.json} this run and
 * how many offers the pool holds afterwards (post-retention).
 */
public record ScanResult(int added, int total) {
}
