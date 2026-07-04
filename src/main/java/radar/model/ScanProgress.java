package radar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A progress event emitted over SSE during a scan.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScanProgress(
    Type type,
    String message,
    Integer current,
    Integer total,
    Integer saved
) {

  public enum Type {
    SCANNING,
    ENRICHING,
    SAVING,
    DONE,
    ERROR
  }
}
