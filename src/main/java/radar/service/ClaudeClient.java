package radar.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// effort low: thinking is billed as output, and extraction/scoring are simple
@Component
public class ClaudeClient {

  private static final Logger log = LoggerFactory.getLogger(ClaudeClient.class);

  static final String MODEL = "claude-haiku-5-5";
  static final OutputConfig.Effort EFFORT = OutputConfig.Effort.LOW;
  // thinking counts towards max_tokens
  private static final long MAX_TOKENS = 16000L;

  private final AnthropicClient client;

  public ClaudeClient(AnthropicClient client) {
    this.client = client;
  }

  public <T> T ask(String prompt, Class<T> type) {
    var params = params(prompt, type);
    var message = client.messages().create(params);
    var usage = message.usage();
    log.info("Claude {} {}: {} in / {} out tokens", MODEL, type.getSimpleName(),
        usage.inputTokens(), usage.outputTokens());

    return message.content().stream()
        .flatMap(block -> block.text().stream())
        .map(text -> text.text())
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Claude returned no structured content"));
  }

  static <T> com.anthropic.models.messages.StructuredMessageCreateParams<T> params(String prompt,
      Class<T> type) {
    var builder = MessageCreateParams.builder()
        .model(MODEL)
        .maxTokens(MAX_TOKENS)
        .outputConfig(type)
        .addUserMessage(prompt);
    // outputConfig(type) derives the JSON schema; re-set the config with the same format + effort
    var format = builder.build().rawParams().outputConfig().orElseThrow().format();

    return builder
        .outputConfig(OutputConfig.builder().format(format).effort(EFFORT).build())
        .build();
  }
}
