package radar.model;

/**
 * Combined result of a scan: the scrape ({@code added}/{@code total}) plus the details step
 * ({@code detailsFetched}/{@code detailsRemaining}).
 */
public record ScanResponse(int added, int total, int detailsFetched, int detailsRemaining) {

  public static ScanResponse of(ScanResult scan, DetailsResult details) {
    return new ScanResponse(scan.added(), scan.total(), details.fetched(), details.remaining());
  }
}
