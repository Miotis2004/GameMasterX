package com.gamemasterx.server.ai.local;

import com.gamemasterx.server.ai.AiCancellation;
import com.gamemasterx.server.ai.AiGenerationRequest;
import com.gamemasterx.server.ai.AiGenerationResult;
import com.gamemasterx.server.ai.AiProvider;
import com.gamemasterx.server.ai.AiProviderException;
import com.gamemasterx.server.ai.config.AiProviderProperties;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;

import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configurable OpenAI-compatible adapter for a local model endpoint.
 *
 * <p>This implementation talks to any OpenAI-compatible server — for example a
 * locally running Llama.cpp, vLLM, Ollama, LM Studio or similar — by issuing the
 * standard {@code POST /chat/completions} request and reading the
 * {@code choices[0].message.content} field back. It depends only on the JDK
 * {@link HttpClient} and never on any extra dependency, so it requires
 * <em>no</em> internet connectivity to build and can be switched on purely
 * through configuration.</p>
 *
 * <p>Graceful degradation is a first-class behaviour: when the endpoint cannot
 * be reached (connection refused, DNS failure, timeout, transport error or a
 * server-side {@code 5xx} response) the completion does not fail the request.
 * Instead it logs a warning and returns a clearly-marked offline placeholder, so
 * the application keeps working even when the local model server is down. Only
 * client-side errors (for example {@code 400 Bad Request} from a wrong model
 * name) and malformed responses surface as {@link AiProviderException}, because
 * those indicate a configuration problem rather than an unavailable endpoint.</p>
 *
 * <p>Per the {@link AiProvider} contract this provider never reads a secret at
 * construction time. The API key, when configured, is resolved from the named
 * environment variable at call time and is only ever sent over the wire — it is
 * never stored on the instance nor returned in a result.</p>
 */
public class AiLocalOpenAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(AiLocalOpenAiProvider.class);

    private static final String SYSTEM_PROMPT =
            "You are a helpful, in-character tabletop game master assistant.";

    private static final String UNAVAILABLE_STATUS = "unavailable";
    private static final String OK_STATUS = "available";

    private final String endpoint;
    private final String model;
    private final int contextSize;
    private final int maxTokens;
    private final Duration timeout;
    private final String apiKeyEnv;

    public AiLocalOpenAiProvider(AiProviderProperties properties) {
        this.endpoint = normaliseEndpoint(properties.getEndpoint());
        this.model = properties.getModel();
        this.contextSize = properties.getContextSize();
        this.maxTokens = Math.max(1, properties.getMaxTokens());
        this.timeout = properties.getTimeout() != null ? properties.getTimeout() : Duration.ofSeconds(15);
        this.apiKeyEnv = properties.getApiKeyEnv();
    }

    private static String normaliseEndpoint(String endpoint) {
        if (endpoint == null) {
            return "";
        }
        String trimmed = endpoint.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    @Override
    public String providerName() {
        return "openai-compatible";
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public int contextSize() {
        return contextSize;
    }

    @Override
    public int maxTokens() {
        return maxTokens;
    }

    /**
     * The provider is considered available whenever an endpoint is configured.
     * This is configuration-based readiness and never opens a socket, so health
     * checks stay cheap. Actual reachability is resolved lazily during
     * generation, where an unreachable endpoint degrades gracefully instead of
     * failing the request.
     */
    @Override
    public boolean isAvailable() {
        return !endpoint.isEmpty();
    }

    @Override
    public AiGenerationResult generate(AiGenerationRequest request, AiCancellation cancellation) {
        if (request == null || request.instruction() == null) {
            throw new AiProviderException("AI generation request or instruction must not be null");
        }
        if (cancellation != null && cancellation.isCancellationRequested()) {
            throw new AiProviderException("AI generation cancelled before it started");
        }

        try {
            return callEndpoint(request, cancellation);
        } catch (AiProviderException e) {
            // Genuine configuration/parse problems: fail loudly.
            throw e;
        } catch (IOException | InterruptedException e) {
            // Endpoint unavailable (connection refused, DNS failure, timeout,
            // transport error). Degrade gracefully to an offline placeholder so
            // the application keeps working while the model server is down.
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                throw new AiProviderException("AI generation interrupted", e);
            }
            log.warn("OpenAI-compatible endpoint '{}' is unavailable ({}); "
                            + "degrading to the offline local placeholder (model={}).",
                    endpoint, describe(e), model);
            return offlinePlaceholder(request);
        }
    }

    private AiGenerationResult callEndpoint(AiGenerationRequest request, AiCancellation cancellation)
            throws IOException, InterruptedException {
        String payload = buildRequestBody(request, cancellation);
        HttpResponse<String> response = sendRequest(payload);

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            // Server errors (5xx) degrade gracefully; client errors (4xx) are
            // treated as configuration problems and surfaced.
            if (status >= 500) {
                log.warn("OpenAI-compatible endpoint '{}' returned HTTP {} ({}); "
                                + "degrading to the offline local placeholder (model={}).",
                        endpoint, status, truncate(response.body()), model);
                return offlinePlaceholder(request);
            }
            throw new AiProviderException("OpenAI-compatible endpoint '" + endpoint
                    + "' returned HTTP " + status + ": " + truncate(response.body()));
        }

        return parseResponse(response.body());
    }

    private HttpResponse<String> sendRequest(String payload) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(payload));

        String apiKey = resolveApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String resolveApiKey() {
        if (apiKeyEnv == null || apiKeyEnv.isBlank()) {
            return null;
        }
        return System.getenv(apiKeyEnv);
    }

    private String buildRequestBody(AiGenerationRequest request, AiCancellation cancellation)
            throws InterruptedException {
        // Cooperative cancellation is polled while the body is assembled so a
        // slow caller can still abort before any bytes hit the wire.
        if (cancellation != null) {
            Thread.sleep(1);
            if (cancellation.isCancellationRequested()) {
                throw new AiProviderException("AI generation cancelled");
            }
        }

        String instruction = request.instruction();
        List<String> contextLines = request.contextLines();

        StringBuilder body = new StringBuilder();
        body.append("{\"model\":");
        appendJsonString(body, model);
        body.append(",\"stream\":false,\"max_tokens\":")
                .append(maxTokens).append(",\"messages\":[");

        body.append("{\"role\":\"system\",\"content\":");
        appendJsonString(body, contextLines != null && !contextLines.isEmpty()
                ? String.join("\n", contextLines) : SYSTEM_PROMPT);
        body.append(",{\"role\":\"user\",\"content\":");
        appendJsonString(body, instruction);
        body.append("]}");

        return body.toString();
    }

    /**
     * Minimal OpenAI-compatible response reader. Handles the standard
     * {@code choices[0].message.content} payload and an optional
     * {@code usage} block, without pulling in a JSON dependency. Malformed
     * input is reported as a configuration error so it surfaces rather than
     * silently degrading.
     */
    private AiGenerationResult parseResponse(String body) {
        try {
            String content = extractChoiceContent(body);
            int inputTokens = extractIntField(body, "prompt_tokens");
            int outputTokens = extractIntField(body, "completion_tokens");

            if (content == null) {
                throw new AiProviderException(
                        "OpenAI-compatible response missing choices[0].message.content: " + truncate(body));
            }
            if (inputTokens <= 0 && !content.isEmpty()) {
                inputTokens = Math.max(1, content.length() / 4);
            }
            if (outputTokens <= 0) {
                outputTokens = Math.min(maxTokens, Math.max(1, content.length() / 4));
            }
            return new AiGenerationResult(content, model, inputTokens, outputTokens);
        } catch (AiProviderException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AiProviderException("Failed to parse OpenAI-compatible response: " + e.getMessage()
                    + " (body=" + truncate(body) + ")", e);
        }
    }

    private String extractChoiceContent(String body) {
        int choices = body.indexOf("\"choices\"");
        if (choices < 0) {
            throw new IllegalArgumentException("no choices field");
        }
        int firstMessage = body.indexOf("\"content\"", choices);
        if (firstMessage < 0) {
            throw new IllegalArgumentException("no content in first choice");
        }
        int colon = body.indexOf(':', firstMessage);
        int quote = body.indexOf('"', colon);
        if (quote < 0) {
            throw new IllegalArgumentException("unterminated content string");
        }
        return readJsonString(body, quote);
    }

    private int extractIntField(String body, String field) {
        int idx = body.indexOf("\"" + field + "\"");
        if (idx < 0) {
            return 0;
        }
        int colon = body.indexOf(':', idx);
        int numStart = colon;
        while (++numStart < body.length() && Character.isWhitespace(body.charAt(numStart))) {
            // advance
        }
        int end = numStart;
        while (end < body.length() && (Character.isDigit(body.charAt(end)) || body.charAt(end) == '-')) {
            end++;
        }
        if (end <= numStart) {
            return 0;
        }
        try {
            return Integer.parseInt(body.substring(numStart, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private AiGenerationResult offlinePlaceholder(AiGenerationRequest request) {
        String text = "[unavailable openAI endpoint · " + model
                + "] offline placeholder for: " + request.instruction();
        int inputTokens = Math.max(1, request.instruction().length() / 4);
        return new AiGenerationResult(text, model, inputTokens, 0);
    }

    private String describe(Throwable e) {
        if (e instanceof HttpTimeoutException || e instanceof ConnectException
                || e instanceof UnknownHostException) {
            return e.getClass().getSimpleName();
        }
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    private static String truncate(String value) {
        if (value == null) {
            return "<null>";
        }
        int limit = 200;
        return value.length() <= limit ? value : value.substring(0, limit) + "...";
    }

    /**
     * Appends a JSON-escaped, double-quoted string starting at the current
     * cursor position.
     */
    private static void appendJsonString(StringBuilder sb, String value) {
        sb.append('"');
        if (value != null) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"':
                        sb.append("\\\"");
                        break;
                    case '\\':
                        sb.append("\\\\");
                        break;
                    case '\n':
                        sb.append("\\n");
                        break;
                    case '\r':
                        sb.append("\\r");
                        break;
                    case '\t':
                        sb.append("\\t");
                        break;
                    case '\b':
                        sb.append("\\b");
                        break;
                    case '\f':
                        sb.append("\\f");
                        break;
                    default:
                        if (c < 0x20) {
                            sb.append(String.format("\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                }
            }
        }
        sb.append('"');
    }

    /**
     * Reads a JSON string literal beginning at the opening quote index. Support
     * for the escapes emitted by {@link #appendJsonString(StringBuilder, String)}.
     */
    private static String readJsonString(String s, int quoteIndex) {
        StringBuilder out = new StringBuilder();
        int i = quoteIndex + 1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\') {
                char esc = s.charAt(i + 1);
                switch (esc) {
                    case 'n':
                        out.append('\n');
                        break;
                    case 'r':
                        out.append('\r');
                        break;
                    case 't':
                        out.append('\t');
                        break;
                    case 'b':
                        out.append('\b');
                        break;
                    case 'f':
                        out.append('\f');
                        break;
                    case '"':
                        out.append('"');
                        break;
                    case '\\':
                        out.append('\\');
                        break;
                    case 'u':
                        out.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                        i += 4;
                        break;
                    default:
                        out.append(esc);
                }
                i += 2;
            } else if (c == '"') {
                break;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
