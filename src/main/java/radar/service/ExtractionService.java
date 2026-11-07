package radar.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import radar.model.ExtractionResult;
import radar.model.OfferExtraction;
import radar.model.RawJobOffer;
import radar.repository.JsonRepository;

/**
 * Extracts skills, requirements, responsibilities, project and real seniority from each offer's text
 * with Claude — once per offer, independent of the profile. Processes offers that have a description
 * and no entry in {@code extractions.json}; a failed offer is left for a later run.
 */
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

  /** @param limit optional cap on how many offers to extract this run; {@code null} = no cap */
  public ExtractionResult extractMissing(Integer limit) {
    var extractions = repository.readExtractions();
    var todo = repository.readRawOffers().stream()
        .map(stored -> stored.offer())
        .filter(offer -> !extractions.containsKey(offer.id()) && hasDescription(offer))
        .toList();
    var batch = limit == null ? todo : todo.subList(0, Math.min(limit, todo.size()));
    progress.begin("extract", batch.size());
    var extracted = 0;
    try {
      for (var offer : batch) {
        try {
          extractions.put(offer.id(), claude.ask(prompt(offer), OfferExtraction.class));
          extracted++;
        } catch (RuntimeException e) {
          log.warn("Extraction failed for {}: {}", offer.id(), e.toString());
        }
        progress.advance();
      }
    } finally {
      repository.saveExtractions(extractions);
    }
    var remaining = todo.size() - extracted;
    log.info("Extract: extracted {}, remaining {} (limit={})", extracted, remaining, limit);
    return new ExtractionResult(extracted, remaining);
  }

  private static boolean hasDescription(RawJobOffer offer) {
    return offer.description() != null && !offer.description().isBlank();
  }

  static String prompt(RawJobOffer offer) {
    return "Extract the facts from this job offer. Use only what the text says.\n\n"
        + "=== JOB OFFER ===\n" + OfferPrompt.describe(offer);
  }
}
