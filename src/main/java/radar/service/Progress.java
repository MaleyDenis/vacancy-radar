package radar.service;

import org.springframework.stereotype.Component;

/**
 * Live progress of the running background task: current step and how many of its items are done.
 * Services report into it; {@link BackgroundRunner} exposes it via {@code GET /api/status}.
 */
@Component
public class Progress {

  /** @param total items in this step, 0 when unknown (e.g. listing pages) */
  public record Snapshot(String step, int done, int total) {
  }

  private volatile Snapshot current = new Snapshot(null, 0, 0);

  public synchronized void begin(String step, int total) {
    current = new Snapshot(step, 0, total);
  }

  /** Safe to call from several threads at once (parallel extraction). */
  public synchronized void advance() {
    var c = current;
    current = new Snapshot(c.step(), c.done() + 1, c.total());
  }

  public void clear() {
    current = new Snapshot(null, 0, 0);
  }

  public Snapshot snapshot() {
    return current;
  }
}
