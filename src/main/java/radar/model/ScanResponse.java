package radar.model;

/**
 * Combined result of a scan: the scrape ({@code added}/{@code total}), the details step
 * ({@code detailsFetched}/{@code detailsRemaining}), the extract step ({@code extracted}/
 * {@code extractRemaining}), and the evaluate step ({@code evaluated} = offers scored this run).
 */
public record ScanResponse(int added, int total, int detailsFetched, int detailsRemaining,
    int extracted, int extractRemaining, int evaluated) {

  public static ScanResponse of(ScanResult scan, DetailsResult details, ExtractionResult extraction,
      EvaluationResult eval) {
    return new ScanResponse(scan.added(), scan.total(), details.fetched(), details.remaining(),
        extraction.extracted(), extraction.remaining(), eval.evaluated());
  }
}
