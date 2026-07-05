package radar.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import radar.model.Analytics;
import radar.model.JobReport;
import radar.model.StoredRawOffer;
import radar.model.UserProfile;

/**
 * Reads and writes the application's JSON files under the data directory. There is no database —
 * everything is date-partitioned JSON on disk (see {@code .claude/rules/data-storage.md}).
 *
 * <p>Layout:
 * <pre>
 *   {dataDir}/profile.json
 *   {dataDir}/raw-offers.json    (pool of scraped offers, profile-independent)
 *   {dataDir}/seen-ids.json
 *   {dataDir}/{YYYY-MM-DD}/jobs.json
 *   {dataDir}/{YYYY-MM-DD}/analytics.json
 * </pre>
 *
 * <p>All access is guarded by a {@link ReentrantReadWriteLock} so concurrent scans don't corrupt a
 * file. Writes are pretty-printed so the files stay human-inspectable.
 */
@Component
public class JsonRepository {

  private static final String PROFILE_FILE = "profile.json";
  private static final String RAW_OFFERS_FILE = "raw-offers.json";
  private static final String SEEN_IDS_FILE = "seen-ids.json";
  private static final String JOBS_FILE = "jobs.json";
  private static final String ANALYTICS_FILE = "analytics.json";
  private static final Pattern DATE_DIR = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

  private final ObjectMapper objectMapper;
  private final Path dataDir;
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

  public JsonRepository(ObjectMapper objectMapper, @Value("${radar.data.dir:data}") String dataDir) {
    this.objectMapper = objectMapper;
    this.dataDir = Path.of(dataDir);
    ensureDir(this.dataDir);
  }

  // ---- profile ----

  public UserProfile readProfile() {
    return read(dataDir.resolve(PROFILE_FILE), UserProfile.class);
  }

  public void writeProfile(UserProfile profile) {
    write(dataDir.resolve(PROFILE_FILE), profile);
  }

  // ---- raw offers ----

  /** Returns the scraped-offer pool, or an empty list if it doesn't exist yet. */
  public List<StoredRawOffer> readRawOffers() {
    lock.readLock().lock();
    try {
      var file = dataDir.resolve(RAW_OFFERS_FILE);
      if (!Files.exists(file)) {
        return new ArrayList<>();
      }
      return objectMapper.readValue(Files.readAllBytes(file),
          new TypeReference<List<StoredRawOffer>>() {});
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read " + RAW_OFFERS_FILE, e);
    } finally {
      lock.readLock().unlock();
    }
  }

  /** Overwrites the scraped-offer pool with {@code offers}. */
  public void saveRawOffers(List<StoredRawOffer> offers) {
    write(dataDir.resolve(RAW_OFFERS_FILE), offers);
  }

  // ---- seen ids ----

  /** Returns the deduplication list, or an empty list if it doesn't exist yet. */
  public List<String> readSeenIds() {
    lock.readLock().lock();
    try {
      var file = dataDir.resolve(SEEN_IDS_FILE);
      if (!Files.exists(file)) {
        return new ArrayList<>();
      }
      return objectMapper.readValue(Files.readAllBytes(file), new TypeReference<List<String>>() {});
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read " + SEEN_IDS_FILE, e);
    } finally {
      lock.readLock().unlock();
    }
  }

  /** Merges {@code newIds} into the existing list, dropping duplicates, and persists the result. */
  public void appendSeenIds(List<String> newIds) {
    lock.writeLock().lock();
    try {
      var merged = new LinkedHashSet<>(readSeenIdsUnlocked());
      merged.addAll(newIds);
      writeUnlocked(dataDir.resolve(SEEN_IDS_FILE), new ArrayList<>(merged));
    } finally {
      lock.writeLock().unlock();
    }
  }

  // ---- jobs ----

  public void saveJobs(String date, List<JobReport> jobs) {
    write(dateDir(date).resolve(JOBS_FILE), jobs);
  }

  /** Returns the saved reports for a date, or an empty list if none were saved. */
  public List<JobReport> loadJobs(String date) {
    lock.readLock().lock();
    try {
      var file = dateDir(date).resolve(JOBS_FILE);
      if (!Files.exists(file)) {
        return new ArrayList<>();
      }
      return objectMapper.readValue(Files.readAllBytes(file),
          new TypeReference<List<JobReport>>() {});
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read jobs for " + date, e);
    } finally {
      lock.readLock().unlock();
    }
  }

  /** Deletes {@code jobs.json} from every date snapshot. Returns how many files were removed. */
  public int deleteAllJobs() {
    var deleted = 0;
    for (var date : listDates()) {
      lock.writeLock().lock();
      try {
        if (Files.deleteIfExists(dateDir(date).resolve(JOBS_FILE))) {
          deleted++;
        }
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to delete jobs for " + date, e);
      } finally {
        lock.writeLock().unlock();
      }
    }
    return deleted;
  }

  // ---- analytics ----

  public void saveAnalytics(String date, Analytics analytics) {
    write(dateDir(date).resolve(ANALYTICS_FILE), analytics);
  }

  public Analytics loadAnalytics(String date) {
    return read(dateDir(date).resolve(ANALYTICS_FILE), Analytics.class);
  }

  // ---- dates ----

  /** Lists the {@code YYYY-MM-DD} snapshot directories under the data dir, ascending. */
  public List<String> listDates() {
    lock.readLock().lock();
    try {
      if (!Files.isDirectory(dataDir)) {
        return new ArrayList<>();
      }
      try (var entries = Files.list(dataDir)) {
        return entries
            .filter(Files::isDirectory)
            .map(p -> p.getFileName().toString())
            .filter(name -> DATE_DIR.matcher(name).matches())
            .sorted()
            .toList();
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to list dates in " + dataDir, e);
    } finally {
      lock.readLock().unlock();
    }
  }

  // ---- internals ----

  private List<String> readSeenIdsUnlocked() throws RuntimeException {
    var file = dataDir.resolve(SEEN_IDS_FILE);
    if (!Files.exists(file)) {
      return new ArrayList<>();
    }
    try {
      return objectMapper.readValue(Files.readAllBytes(file), new TypeReference<List<String>>() {});
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read " + SEEN_IDS_FILE, e);
    }
  }

  private Path dateDir(String date) {
    return dataDir.resolve(date);
  }

  private <T> T read(Path file, Class<T> type) {
    lock.readLock().lock();
    try {
      if (!Files.exists(file)) {
        throw new IllegalStateException("File not found: " + file);
      }
      return objectMapper.readValue(Files.readAllBytes(file), type);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read " + file, e);
    } finally {
      lock.readLock().unlock();
    }
  }

  private void write(Path file, Object value) {
    lock.writeLock().lock();
    try {
      writeUnlocked(file, value);
    } finally {
      lock.writeLock().unlock();
    }
  }

  private void writeUnlocked(Path file, Object value) {
    try {
      ensureDir(file.getParent());
      var bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
      Files.write(file, bytes);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to write " + file, e);
    }
  }

  private static void ensureDir(Path dir) {
    try {
      if (dir != null) {
        Files.createDirectories(dir);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to create directory " + dir, e);
    }
  }
}
