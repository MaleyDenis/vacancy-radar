package radar.model;

import java.util.List;

public record JobPage(List<JobView> items, int total, int page, int size, int staleCount) {
}
