package radar.service;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import radar.model.ExtractionResult;
import radar.model.OfferExtraction;
import radar.model.RawJobOffer;
import radar.repository.JsonRepository;

@Service
public class ExtractionService {

  private static final Logger log = LoggerFactory.getLogger(ExtractionService.class);

  private final ClaudeClient claude;
  private final JsonRepository repository;
  private final Progress progress;

  public ExtractionService(ClaudeClient claude, JsonRepository repository, Progress progress) {
    this.claude = claude;
    this.repository = repository;
    this.progress = progress;
  }

  static final int SAVE_EVERY = 10;
  static final int PARALLEL = 5;

  public ExtractionResult extractMissing(Integer limit) {
    var extractions = repository.readExtractions();
    var todo = repository.readRawOffers().stream()
        .map(stored -> stored.offer())
        .filter(offer -> !extractions.containsKey(offer.id()) && hasDescription(offer))
        .toList();

    var batch = limit == null ? todo : todo.subList(0, Math.min(limit, todo.size()));
    progress.begin("extract", batch.size());
    var extracted = new AtomicInteger();

    // close() waits for every submitted extraction to finish
    try (var pool = Executors.newFixedThreadPool(PARALLEL, Thread.ofVirtual().factory())) {
      for (var offer : batch) {
        pool.submit(() -> extractOne(offer, extractions, extracted));
      }
    } finally {
      synchronized (extractions) {
        repository.saveExtractions(extractions);
      }
    }

    var remaining = todo.size() - extracted.get();
    log.info("Extract: extracted {}, remaining {} (limit={})", extracted.get(), remaining, limit);

    return new ExtractionResult(extracted.get(), remaining);
  }

  // Claude call outside the lock, so requests run in parallel
  private void extractOne(RawJobOffer offer, Map<String, OfferExtraction> extractions,
      AtomicInteger extracted) {
    try {
      var facts = claude.ask(prompt(offer), ExtractedFacts.class);
      synchronized (extractions) {
        extractions.put(offer.id(), facts.toExtraction());
        if (extracted.incrementAndGet() % SAVE_EVERY == 0) {
          repository.saveExtractions(extractions); // a stopped server loses at most SAVE_EVERY
        }
      }
    } catch (RuntimeException e) {
      log.warn("Extraction failed for {}: {}", offer.id(), e.toString());
    }

    progress.advance();
  }

  private static boolean hasDescription(RawJobOffer offer) {
    return offer.description() != null && !offer.description().isBlank();
  }

  static String prompt(RawJobOffer offer) {
    return "Extract the facts from this job offer. Use only what the text says; "
        + "answer null for anything the text does not mention. Do not guess.\n\n"
        + "=== JOB OFFER ===\n" + OfferPrompt.describe(offer);
  }
}
