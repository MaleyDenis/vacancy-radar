package radar.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper over the Anthropic SDK: sends a plain-text prompt to Claude Haiku and returns the
 * structured output parsed into {@code type} (its JSON schema is derived from the class). Never send
 * HTML here.
 *
 * <p>Haiku 5.5 thinks adaptively by default and thinking is billed as output, so effort is set to
 * {@code low}: extraction and scoring are simple, low effort keeps thinking (and cost) small.
 */
@Component
public class ClaudeClient {

  private static final Logger log = LoggerFactory.getLogger(ClaudeClient.class);

  static final String MODEL = "claude-haiku-5-5";
  static final OutputConfig.Effort EFFORT = OutputConfig.Effort.LOW;
  /** Room for thinking + the JSON answer; thinking counts towards max_tokens. */
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

  /** Structured output for {@code type} plus {@link #EFFORT} in one output config. */
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
