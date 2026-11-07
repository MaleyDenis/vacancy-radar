package radar.model;

/**
 * Combined result of a scan: scrape ({@code added}/{@code total}), details
 * ({@code detailsFetched}/{@code detailsRemaining}), extraction ({@code extracted}/
 * {@code extractRemaining}) and scoring ({@code scored}/{@code scoreRemaining}).
 */
public record ScanResponse(int added, int total, int detailsFetched, int detailsRemaining,
    int extracted, int extractRemaining, int scored, int scoreRemaining) {

  public static ScanResponse of(ScanResult scan, DetailsResult details, ExtractionResult extraction,
      ScoringResult scoring) {
    return new ScanResponse(scan.added(), scan.total(), details.fetched(), details.remaining(),
        extraction.extracted(), extraction.remaining(), scoring.scored(), scoring.remaining());
  }
}
