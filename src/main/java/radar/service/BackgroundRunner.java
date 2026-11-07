package radar.service;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs one long task (scan or rescore) at a time on a background thread, so HTTP calls return at
 * once. {@link #status()} reports what is running, its progress and the last finished result.
 */
@Component
public class BackgroundRunner {

  private static final Logger log = LoggerFactory.getLogger(BackgroundRunner.class);

  /**
   * @param running    whether a task is running now
   * @param task       name of the running or last task ({@code scan}, {@code rescore})
   * @param step       current step of the running task
   * @param done       items done in the current step
   * @param total      items in the current step, 0 when unknown
   * @param startedAt  ISO-8601 start of the running or last task
   * @param finishedAt ISO-8601 end of the last task, null while running
   * @param result     result of the last finished task, null if it failed
   * @param error      error message of the last task, null if it succeeded
   */
  public record Status(boolean running, String task, String step, int done, int total,
      String startedAt, String finishedAt, Object result, String error) {
  }

  private final Progress progress;
  private boolean running;
  private int quickTasks;
  private String task;
  private String startedAt;
  private String finishedAt;
  private Object result;
  private String error;

  public BackgroundRunner(Progress progress) {
    this.progress = progress;
  }

  /** Starts {@code work} in the background. Returns false (and does nothing) if a task is running. */
  public synchronized boolean start(String name, Supplier<?> work) {
    if (running || quickTasks > 0) {
      return false;
    }
    running = true;
    task = name;
    startedAt = Instant.now().toString();
    finishedAt = null;
    result = null;
    error = null;
    progress.clear();
    Thread.ofVirtual().name("bg-" + name).start(() -> run(work));
    return true;
  }

  private void run(Supplier<?> work) {
    Object value = null;
    String failure = null;
    try {
      value = work.get();
    } catch (RuntimeException e) {
      log.error("Background task {} failed", task, e);
      failure = e.getMessage() == null ? e.toString() : e.getMessage();
    }
    synchronized (this) {
      result = value;
      error = failure;
      finishedAt = Instant.now().toString();
      running = false;
      progress.clear();
    }
  }

  /**
   * Runs a short task (e.g. scoring one offer) on the caller's thread. Several may run at once, but
   * never together with a background task: returns empty if one is running, and {@link #start}
   * refuses while any runs.
   */
  public <T> Optional<T> runNow(Supplier<T> work) {
    synchronized (this) {
      if (running) {
        return Optional.empty();
      }
      quickTasks++;
    }
    try {
      return Optional.of(work.get());
    } finally {
      synchronized (this) {
        quickTasks--;
      }
    }
  }

  public synchronized boolean isRunning() {
    return running || quickTasks > 0;
  }

  public synchronized Status status() {
    var p = progress.snapshot();
    return new Status(running, task, p.step(), p.done(), p.total(), startedAt, finishedAt, result,
        error);
  }
}
