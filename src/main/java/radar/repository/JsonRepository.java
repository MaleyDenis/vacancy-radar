package radar.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import radar.model.Analytics;
import radar.model.OfferExtraction;
import radar.model.OfferScore;
import radar.model.StoredRawOffer;

// one lock for all files: concurrent tasks must not corrupt them
@Component
public class JsonRepository {

  private static final String RESUME_FILE = "resume.txt";
  private static final String RAW_OFFERS_FILE = "raw-offers.json";
  private static final String EXTRACTIONS_FILE = "extractions.json";
  private static final String SCORES_FILE = "scores.json";
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

  public String readResume() {
    lock.readLock().lock();
    try {
      var file = dataDir.resolve(RESUME_FILE);
      // stripped: a hand-edited file and a UI save of the same text must hash the same
      return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8).strip() : "";
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read " + RESUME_FILE, e);
    } finally {
      lock.readLock().unlock();
    }
  }

  public void writeResume(String resume) {
    lock.writeLock().lock();
    try {
      Files.writeString(dataDir.resolve(RESUME_FILE), resume, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to write " + RESUME_FILE, e);
    } finally {
      lock.writeLock().unlock();
    }
  }

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

  public void saveRawOffers(List<StoredRawOffer> offers) {
    write(dataDir.resolve(RAW_OFFERS_FILE), offers);
  }

  public void backupRawOffers() {
    lock.writeLock().lock();
    try {
      var file = dataDir.resolve(RAW_OFFERS_FILE);
      if (Files.exists(file)) {
        Files.copy(file, dataDir.resolve(RAW_OFFERS_FILE + ".bak"),
            StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to back up " + RAW_OFFERS_FILE, e);
    } finally {
      lock.writeLock().unlock();
    }
  }

  public LinkedHashMap<String, OfferExtraction> readExtractions() {
    return readMap(EXTRACTIONS_FILE, new TypeReference<LinkedHashMap<String, OfferExtraction>>() {});
  }

  public void saveExtractions(Map<String, OfferExtraction> extractions) {
    write(dataDir.resolve(EXTRACTIONS_FILE), extractions);
  }

  public LinkedHashMap<String, OfferScore> readScores() {
    return readMap(SCORES_FILE, new TypeReference<LinkedHashMap<String, OfferScore>>() {});
  }

  public void saveScores(Map<String, OfferScore> scores) {
    write(dataDir.resolve(SCORES_FILE), scores);
  }

  public int deleteScores() {
    lock.writeLock().lock();
    try {
      return Files.deleteIfExists(dataDir.resolve(SCORES_FILE)) ? 1 : 0;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to delete " + SCORES_FILE, e);
    } finally {
      lock.writeLock().unlock();
    }
  }

  public int deleteAllData() {
    lock.writeLock().lock();
    try {
      if (!Files.isDirectory(dataDir)) {
        return 0;
      }

      var removed = 0;

      try (var entries = Files.list(dataDir)) {
        for (var entry : entries.toList()) {
          var name = entry.getFileName().toString();
          // keep the resume and any dotfile scaffolding (e.g. .gitkeep)
          if (name.equals(RESUME_FILE) || name.startsWith(".")) {
            continue;
          }

          deleteRecursively(entry);
          removed++;
        }
      }

      return removed;
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to delete data", e);
    } finally {
      lock.writeLock().unlock();
    }
  }

  private static void deleteRecursively(Path path) throws IOException {
    if (Files.isDirectory(path)) {
      try (var children = Files.list(path)) {
        for (var child : children.toList()) {
          deleteRecursively(child);
        }
      }
    }

    Files.deleteIfExists(path);
  }

  public void saveAnalytics(String date, Analytics analytics) {
    write(dateDir(date).resolve(ANALYTICS_FILE), analytics);
  }

  public Analytics loadAnalytics(String date) {
    return read(dateDir(date).resolve(ANALYTICS_FILE), Analytics.class);
  }

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

  private Path dateDir(String date) {
    return dataDir.resolve(date);
  }

  private <V> LinkedHashMap<String, V> readMap(String name,
      TypeReference<LinkedHashMap<String, V>> type) {
    lock.readLock().lock();
    try {
      var file = dataDir.resolve(name);
      if (!Files.exists(file)) {
        return new LinkedHashMap<>();
      }

      return objectMapper.readValue(Files.readAllBytes(file), type);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read " + name, e);
    } finally {
      lock.readLock().unlock();
    }
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
