package radar.service;

import radar.model.RawJobOffer;

/** Renders a {@link RawJobOffer} as the plain-text block used in Claude prompts. */
final class OfferPrompt {

  private OfferPrompt() {
  }

  static String describe(RawJobOffer offer) {
    var sb = new StringBuilder();
    append(sb, "Title", offer.title());
    append(sb, "Company", offer.companyName());
    append(sb, "Location", offer.location());
    append(sb, "Remote", offer.remote() == null ? null : offer.remote().toString());
    append(sb, "Experience level", offer.experienceLevel());
    append(sb, "Contract type", offer.contractType());
    if (offer.salaryMin() != null || offer.salaryMax() != null) {
      var gross = offer.salaryGross() == null ? "" : offer.salaryGross() ? " gross" : " net";
      var unit = offer.salaryUnit() == null ? "" : " per " + offer.salaryUnit();
      append(sb, "Salary",
          offer.salaryMin() + " - " + offer.salaryMax() + " " + offer.currency() + gross + unit);
    }
    append(sb, "Description", offer.description());
    return sb.toString();
  }

  private static void append(StringBuilder sb, String label, String value) {
    if (value != null && !value.isBlank()) {
      sb.append(label).append(": ").append(value).append('\n');
    }
  }
}
