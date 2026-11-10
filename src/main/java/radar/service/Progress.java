package radar.service;

import org.springframework.stereotype.Component;

@Component
public class Progress {

  public record Snapshot(String step, int done, int total) {
  }

  private volatile Snapshot current = new Snapshot(null, 0, 0);

  public synchronized void begin(String step, int total) {
    current = new Snapshot(step, 0, total);
  }

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
