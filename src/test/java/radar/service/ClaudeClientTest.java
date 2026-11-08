package radar.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClaudeClientTest {

  @Test
  void requestKeepsTheSchemaAndSetsLowEffort() {
    var config = ClaudeClient.params("text", ScoreResult.class).rawParams().outputConfig()
        .orElseThrow().toString();

    assertThat(config).contains("effort=low").contains("matchScore");
    assertThat(ClaudeClient.params("text", ScoreResult.class).rawParams().model().toString())
        .isEqualTo("claude-haiku-5-5");
  }
}
