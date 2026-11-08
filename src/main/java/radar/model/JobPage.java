package radar.model;

import java.util.List;

/**
 * One page of {@code GET /api/jobs}. {@code items} are card views (offer without
 * {@code description}); {@code total} counts every offer matching the filters;
 * {@code staleCount} counts stale scores across all offers, regardless of filters.
 */
public record JobPage(List<JobView> items, int total, int page, int size, int staleCount) {
}
