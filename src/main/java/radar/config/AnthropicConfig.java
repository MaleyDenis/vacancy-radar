package radar.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the Anthropic client as a Spring bean, configured from {@code anthropic.api.key}
 * (which resolves to the {@code ANTHROPIC_API_KEY} environment variable).
 */
@Configuration
public class AnthropicConfig {

  @Bean
  public AnthropicClient anthropicClient(@Value("${anthropic.api.key:}") String apiKey) {
    return AnthropicOkHttpClient.builder().apiKey(apiKey).build();
  }
}
