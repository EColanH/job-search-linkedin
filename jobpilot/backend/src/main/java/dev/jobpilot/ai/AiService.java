package dev.jobpilot.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** A model may select existing fact IDs, but can never introduce factual claims. */
@Service
public class AiService {
  public static final String PROMPT_VERSION = "fact-selection-v1";
  private final ObjectMapper mapper;

  public AiService(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public record Selection(List<String> factIds, String status, String promptVersion) {}

  public Selection selectFacts(
      Map<String, Object> candidate, Map<String, Object> job, Map<String, Object> settings) {
    if (settings.get("ai") instanceof Map<?, ?> nested) {
      Map<String, Object> flattened = new HashMap<>();
      nested.forEach((k, v) -> flattened.put(k.toString(), v));
      settings = flattened;
    }
    String provider =
        Objects.toString(settings.getOrDefault("provider", "ollama")).toLowerCase(Locale.ROOT);
    if (provider.equals("disabled")) return new Selection(List.of(), "DISABLED", PROMPT_VERSION);
    List<Map<String, Object>> facts = facts(candidate);
    if (facts.isEmpty()) return new Selection(List.of(), "NO_VERIFIED_FACTS", PROMPT_VERSION);
    try {
      ChatModel model = model(settings, provider);
      String payload =
          mapper.writeValueAsString(
              Map.of(
                  "verifiedFacts",
                  facts,
                  "untrustedJob",
                  Map.of(
                      "title",
                      Objects.toString(job.get("title"), ""),
                      "description",
                      Objects.toString(job.get("description"), "")
                          .substring(
                              0,
                              Math.min(
                                  18000, Objects.toString(job.get("description"), "").length())))));
      String response =
          model
              .call(
                  new Prompt(
                      List.of(
                          new SystemMessage(
                              new org.springframework.core.io.ClassPathResource(
                                      "prompts/fact-selection-v1.txt")
                                  .getContentAsString(java.nio.charset.StandardCharsets.UTF_8)),
                          new UserMessage(payload))))
              .getResult()
              .getOutput()
              .getText();
      return new Selection(validateSelection(response, facts), "AVAILABLE", PROMPT_VERSION);
    } catch (IllegalArgumentException e) {
      return new Selection(List.of(), "CONFIGURATION_OR_OUTPUT_REJECTED", PROMPT_VERSION);
    } catch (Exception e) {
      // Never log requests, CVs, credentials, model response, or provider exception bodies.
      return new Selection(List.of(), "UNAVAILABLE", PROMPT_VERSION);
    }
  }

  List<String> validateSelection(String output, List<Map<String, Object>> facts) throws Exception {
    if (output == null || output.length() > 8192)
      throw new IllegalArgumentException("Invalid model output");
    JsonNode json = mapper.readTree(output);
    if (!json.isObject()
        || json.size() != 1
        || !json.has("factIds")
        || !json.get("factIds").isArray()
        || json.get("factIds").size() > 20)
      throw new IllegalArgumentException("Invalid structured output");
    Set<String> permitted = new HashSet<>();
    facts.forEach(f -> permitted.add(Objects.toString(f.get("id"), "")));
    List<String> selected = new ArrayList<>();
    for (JsonNode id : json.get("factIds")) {
      if (!id.isTextual() || !permitted.contains(id.asText()))
        throw new IllegalArgumentException("Unknown fact ID");
      if (!selected.contains(id.asText())) selected.add(id.asText());
    }
    return selected;
  }

  private ChatModel model(Map<String, Object> settings, String provider) {
    String name = Objects.toString(settings.getOrDefault("model", "qwen2.5:7b"));
    if (name.isBlank() || name.length() > 120 || !name.matches("[a-zA-Z0-9._:/@-]+"))
      throw new IllegalArgumentException("Invalid model name");
    double temperature = settings.get("temperature") instanceof Number n ? n.doubleValue() : 0.1;
    if (!Double.isFinite(temperature) || temperature < 0 || temperature > 1)
      throw new IllegalArgumentException("Invalid temperature");
    int maxTokens = settings.get("maxTokens") instanceof Number n ? n.intValue() : 512;
    if (maxTokens < 128 || maxTokens > 4096)
      throw new IllegalArgumentException("maxTokens must be 128–4096");
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build());
    factory.setReadTimeout(Duration.ofSeconds(45));
    RestClient.Builder http = RestClient.builder().requestFactory(factory);
    if (provider.equals("ollama")) {
      String base =
          System.getenv()
              .getOrDefault(
                  "OLLAMA_BASE_URL",
                  System.getenv()
                      .getOrDefault("JOBPILOT_OLLAMA_BASE_URL", "http://localhost:11434"));
      URI uri = URI.create(base);
      if (!List.of("localhost", "127.0.0.1", "host.docker.internal", "ollama")
              .contains(uri.getHost())
          || !List.of("http", "https").contains(uri.getScheme()))
        throw new IllegalArgumentException("Ollama must be local");
      return OllamaChatModel.builder()
          .ollamaApi(OllamaApi.builder().baseUrl(base).restClientBuilder(http).build())
          .retryTemplate(
              org.springframework.retry.support.RetryTemplate.builder()
                  .maxAttempts(1)
                  .fixedBackoff(100)
                  .build())
          .defaultOptions(
              OllamaOptions.builder()
                  .model(name)
                  .temperature(temperature)
                  .numPredict(maxTokens)
                  .build())
          .build();
    }
    if (!provider.equals("openai-compatible") && !provider.equals("openai"))
      throw new IllegalArgumentException("Unsupported AI provider");
    if (!Boolean.TRUE.equals(settings.get("externalConsent")))
      throw new IllegalArgumentException("External AI consent required");
    String base =
        System.getenv().getOrDefault("JOBPILOT_OPENAI_BASE_URL", "https://api.openai.com");
    URI uri = URI.create(base);
    if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
      throw new IllegalArgumentException("External AI requires a configured HTTPS endpoint");
    String key = System.getenv("JOBPILOT_OPENAI_API_KEY");
    if (key == null || key.isBlank()) throw new IllegalArgumentException("External AI key missing");
    return OpenAiChatModel.builder()
        .openAiApi(OpenAiApi.builder().baseUrl(base).apiKey(key).restClientBuilder(http).build())
        .retryTemplate(
            org.springframework.retry.support.RetryTemplate.builder()
                .maxAttempts(1)
                .fixedBackoff(100)
                .build())
        .defaultOptions(
            OpenAiChatOptions.builder()
                .model(name)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build())
        .build();
  }

  @SuppressWarnings("unchecked")
  public static List<Map<String, Object>> facts(Map<String, Object> candidate) {
    Object raw = candidate.get("verifiedFacts");
    if (!(raw instanceof List<?> list)) return List.of();
    List<Map<String, Object>> facts = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> m
          && m.get("id") instanceof String id
          && !id.isBlank()
          && m.get("text") instanceof String text
          && !text.isBlank()
          && !Boolean.FALSE.equals(m.get("verified"))) {
        facts.add(
            Map.of(
                "id",
                id,
                "text",
                text,
                "section",
                Objects.toString(m.get("section"), "experience")));
      }
    }
    return facts;
  }
}
