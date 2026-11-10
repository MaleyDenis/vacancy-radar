package radar.model;

public record ScanResponse(int added, int total, int detailsFetched, int detailsRemaining,
    int extracted, int extractRemaining) {

  public static ScanResponse of(ScanResult scan, DetailsResult details,
      ExtractionResult extraction) {
    return new ScanResponse(scan.added(), scan.total(), details.fetched(), details.remaining(),
        extraction.extracted(), extraction.remaining());
  }
}
