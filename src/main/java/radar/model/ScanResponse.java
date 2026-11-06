package radar.model;

/**
 * Combined result of a scan: the scrape ({@code added}/{@code total}), the details step
 * ({@code detailsFetched}/{@code detailsRemaining}), and the evaluate step ({@code evaluated} =
 * offers scored by Claude this run).
 */
public record ScanResponse(int added, int total, int detailsFetched, int detailsRemaining,
    int evaluated) {

  public static ScanResponse of(ScanResult scan, DetailsResult details, EvaluationResult eval) {
    return new ScanResponse(scan.added(), scan.total(), details.fetched(), details.remaining(),
        eval.evaluated());
  }
}
