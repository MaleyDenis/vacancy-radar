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

  public ExtractionService(ClaudeClient claude, JsonRepository repository) {
    this.claude = claude;
    this.repository = repository;
  }

  /** @param limit optional cap on how many offers to extract this run; {@code null} = no cap */
  public ExtractionResult extractMissing(Integer limit) {
    var extractions = repository.readExtractions();
    var extracted = 0;
    var pending = 0;
    try {
      for (var stored : repository.readRawOffers()) {
        var offer = stored.offer();
        if (extractions.containsKey(offer.id())
            || offer.description() == null || offer.description().isBlank()) {
          continue;
        }
        pending++;
        if (limit != null && extracted >= limit) {
          continue;
        }
        try {
          extractions.put(offer.id(), claude.ask(prompt(offer), OfferExtraction.class));
          extracted++;
        } catch (RuntimeException e) {
          log.warn("Extraction failed for {}: {}", offer.id(), e.toString());
        }
      }
    } finally {
      repository.saveExtractions(extractions);
    }
    var remaining = pending - extracted;
    log.info("Extract: extracted {}, remaining {} (limit={})", extracted, remaining, limit);
    return new ExtractionResult(extracted, remaining);
  }

  static String prompt(RawJobOffer offer) {
    return "Extract the facts from this job offer. Use only what the text says.\n\n"
        + "=== JOB OFFER ===\n" + OfferPrompt.describe(offer);
  }
}
