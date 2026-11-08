package radar.model;

/**
 * Combined result of a scan: scrape ({@code added}/{@code total}), details
 * ({@code detailsFetched}/{@code detailsRemaining}), extraction ({@code extracted}/
 * {@code extractRemaining}). Scoring is not part of a scan: offers are scored from their card.
 */
public record ScanResponse(int added, int total, int detailsFetched, int detailsRemaining,
    int extracted, int extractRemaining) {

  public static ScanResponse of(ScanResult scan, DetailsResult details,
      ExtractionResult extraction) {
    return new ScanResponse(scan.added(), scan.total(), details.fetched(), details.remaining(),
        extraction.extracted(), extraction.remaining());
  }
}
