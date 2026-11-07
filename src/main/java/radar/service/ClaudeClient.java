package radar.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper over the Anthropic SDK: sends a plain-text prompt to Claude Haiku and returns the
 * structured output parsed into {@code type} (its JSON schema is derived from the class). Never send
 * HTML here.
 */
@Component
public class ClaudeClient {

  static final String MODEL = "claude-haiku-4-5-20251001";
  private static final long MAX_TOKENS = 2048L;

  private final AnthropicClient client;

  public ClaudeClient(AnthropicClient client) {
    this.client = client;
  }

  public <T> T ask(String prompt, Class<T> type) {
    var params = MessageCreateParams.builder()
        .model(MODEL)
        .maxTokens(MAX_TOKENS)
        .outputConfig(type)
        .addUserMessage(prompt)
        .build();
    var message = client.messages().create(params);
    return message.content().stream()
        .flatMap(block -> block.text().stream())
        .map(text -> text.text())
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Claude returned no structured content"));
  }
}
