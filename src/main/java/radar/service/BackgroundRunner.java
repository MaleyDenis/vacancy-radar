package radar.service;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BackgroundRunner {

  private static final Logger log = LoggerFactory.getLogger(BackgroundRunner.class);

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

  // short tasks run on the caller's thread, never alongside a background task
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
