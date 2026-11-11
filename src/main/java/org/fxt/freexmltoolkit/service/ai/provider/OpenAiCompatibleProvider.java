package org.fxt.freexmltoolkit.service.ai.provider;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * OpenAI-compatible Chat Completions (decision D14): OpenAI, Ollama, LM Studio, vLLM, Azure OpenAI v1, gateways.
 *
 * <p>Wire format from the providers' documentation (2026-11-10):
 * <ul>
 *   <li>{@code POST {base}/chat/completions}, {@code Authorization: Bearer} (Azure v1: {@code api-key}); Ollama needs a
 *       key value but ignores it — developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create,
 *       docs.ollama.com/api/openai-compatibility;</li>
 *   <li>the length limit as {@code max_completion_tokens} (OpenAI) or {@code max_tokens} (Ollama, most servers);</li>
 *   <li>no {@code tool_choice} (Ollama does not support it);</li>
 *   <li>streamed as {@code data: {chunk}} lines ending with {@code data: [DONE]}; tool-call deltas accumulate by
 *       {@code index}, arguments may be invalid JSON; usage only on the last chunk —
 *       developers.openai.com/api/reference/resources/chat/subresources/completions/streaming-events;</li>
 *   <li>errors {@code {"error":{"message","type","code"}}}, {@code Retry-After} on 429/503 —
 *       developers.openai.com/api/docs/guides/error-codes, …/rate-limits.</li>
 * </ul>
 * Nothing of the request or the answer is logged above DEBUG.
 */
public final class OpenAiCompatibleProvider implements ChatProvider {

    private static final Logger logger = LogManager.getLogger(OpenAiCompatibleProvider.class);

    private final ProviderConfig config;
    private final String apiKey;
    private final ProviderHttp.Opener opener;

    public OpenAiCompatibleProvider(ProviderConfig config, String apiKey) {
        this(config, apiKey, ProviderHttp.defaultOpener());
    }

    OpenAiCompatibleProvider(ProviderConfig config, String apiKey, ProviderHttp.Opener opener) {
        this.config = config;
        this.apiKey = apiKey;
        this.opener = opener;
    }

    @Override
    public ChatResponse chat(ChatRequest request, Consumer<String> onTextDelta, CancelToken cancel)
            throws ProviderException {
        cancel = cancel == null ? CancelToken.none() : cancel;
        JsonObject body = body(request, true);
        HttpURLConnection connection = post(body, cancel);
        try {
            int status = connection.getResponseCode();
            if (status == 400 && body.has("stream_options")) {
                // an older server that does not know stream_options: once more without
                String error = ProviderHttp.errorBody(connection);
                connection.disconnect();
                if (error.contains("stream_options")) {
                    body.remove("stream_options");
                    connection = post(body, cancel);
                    status = connection.getResponseCode();
                } else {
                    throw failure(400, error, connection);
                }
            }
            if (status / 100 != 2) {
                throw failure(status, ProviderHttp.errorBody(connection), connection);
            }
            String contentType = String.valueOf(connection.getContentType());
            try (InputStream in = connection.getInputStream()) {
                return contentType.contains("event-stream") ? readStream(in, onTextDelta, cancel)
                        : readWhole(in, onTextDelta);
            }
        } catch (IOException e) {
            throw ProviderHttp.translate(e, cancel);
        } finally {
            connection.disconnect();
        }
    }

    @Override
    public List<String> listModels(CancelToken cancel) throws ProviderException {
        cancel = cancel == null ? CancelToken.none() : cancel;
        URI uri = ProviderHttp.endpoint(config.baseUrl(), "/models");
        HttpURLConnection connection = ProviderHttp.send(opener, uri, null, headers(), 30, cancel);
        try {
            int status = connection.getResponseCode();
            if (status == 404) {
                return List.of();
            }
            if (status / 100 != 2) {
                throw failure(status, ProviderHttp.errorBody(connection), connection);
            }
            try (InputStream in = connection.getInputStream()) {
                JsonObject json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                        .getAsJsonObject();
                List<String> ids = new ArrayList<>();
                if (json.has("data") && json.get("data").isJsonArray()) {
                    for (JsonElement model : json.getAsJsonArray("data")) {
                        if (model.isJsonObject() && model.getAsJsonObject().has("id")) {
                            ids.add(model.getAsJsonObject().get("id").getAsString());
                        }
                    }
                }
                ids.sort(String::compareToIgnoreCase);
                return ids;
            }
        } catch (IOException e) {
            throw ProviderHttp.translate(e, cancel);
        } catch (JsonParseException | IllegalStateException e) {
            throw new ProviderException(ProviderException.Kind.PROTOCOL, "The model list could not be read."); // NOPMD - the cause may quote response text or the host; never in logs (AI rules)
        } finally {
            connection.disconnect();
        }
    }

    // ---------------------------------------------------------------- request

    JsonObject body(ChatRequest request, boolean stream) {
        JsonObject body = new JsonObject();
        body.addProperty("model", config.model());
        JsonArray messages = new JsonArray();
        if (!request.system().isBlank()) {
            messages.add(message("system", request.system()));
        }
        for (ChatMessage m : request.messages()) {
            switch (m.role()) {
                case USER -> messages.add(message("user", m.text()));
                case ASSISTANT -> {
                    JsonObject a = new JsonObject();
                    a.addProperty("role", "assistant");
                    if (m.text().isEmpty() && !m.toolCalls().isEmpty()) {
                        a.add("content", com.google.gson.JsonNull.INSTANCE);
                    } else {
                        a.addProperty("content", m.text());
                    }
                    if (!m.toolCalls().isEmpty()) {
                        JsonArray calls = new JsonArray();
                        for (ToolCall call : m.toolCalls()) {
                            JsonObject c = new JsonObject();
                            c.addProperty("id", call.id());
                            c.addProperty("type", "function");
                            JsonObject function = new JsonObject();
                            function.addProperty("name", call.name());
                            function.addProperty("arguments", call.rawArguments() != null ? call.rawArguments()
                                    : call.arguments() == null ? "{}" : call.arguments().toString());
                            c.add("function", function);
                            calls.add(c);
                        }
                        a.add("tool_calls", calls);
                    }
                    messages.add(a);
                }
                case TOOL -> {
                    JsonObject t = message("tool", m.text());
                    t.addProperty("tool_call_id", m.toolCallId());
                    messages.add(t);
                }
            }
        }
        body.add("messages", messages);
        int limit = request.maxOutputTokens() > 0 ? request.maxOutputTokens() : config.maxOutputTokens();
        body.addProperty(config.tokenField().json(), limit);
        if (request.temperature() != null) {
            body.addProperty("temperature", request.temperature());
        }
        if (!request.tools().isEmpty()) {
            JsonArray tools = new JsonArray();
            for (ToolSpec spec : request.tools()) {
                JsonObject function = new JsonObject();
                function.addProperty("name", spec.name());
                function.addProperty("description", spec.description());
                function.add("parameters", spec.inputSchema());
                JsonObject tool = new JsonObject();
                tool.addProperty("type", "function");
                tool.add("function", function);
                tools.add(tool);
            }
            body.add("tools", tools);
        }
        if (stream) {
            body.addProperty("stream", true);
            JsonObject options = new JsonObject();
            options.addProperty("include_usage", true);
            body.add("stream_options", options);
        }
        return body;
    }

    private static JsonObject message(String role, String content) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", content);
        return m;
    }

    private Map<String, String> headers() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json, text/event-stream");
        String key = apiKey == null || apiKey.isBlank() ? (config.isLocal(List.of()) ? "local" : null) : apiKey;
        if (key != null) {
            if (config.authHeader() == AuthHeader.API_KEY) {
                headers.put("api-key", key);
            } else {
                headers.put("Authorization", "Bearer " + key);
            }
        }
        return headers;
    }

    private HttpURLConnection post(JsonObject body, CancelToken cancel) throws ProviderException {
        logger.debug("Chat request to {} ({} characters)", config.host(), body.toString().length());
        return ProviderHttp.send(opener, ProviderHttp.endpoint(config.baseUrl(), "/chat/completions"),
                body.toString(), headers(), config.readTimeoutSeconds(), cancel);
    }

    // ---------------------------------------------------------------- response

    private ChatResponse readStream(InputStream in, Consumer<String> onTextDelta, CancelToken cancel)
            throws IOException, ProviderException {
        StringBuilder text = new StringBuilder();
        Map<Integer, CallBuilder> calls = new TreeMap<>();
        String finish = null;
        String model = null;
        Usage usage = Usage.UNKNOWN;
        SseReader reader = new SseReader(in);
        SseReader.Event event;
        boolean done = false;
        while ((event = reader.next()) != null) {
            cancel.throwIfCancelled();
            String data = event.data().strip();
            if (data.equals("[DONE]")) {
                done = true;
                break;
            }
            JsonObject chunk;
            try {
                chunk = JsonParser.parseString(data).getAsJsonObject();
            } catch (JsonParseException | IllegalStateException e) {
                throw new ProviderException(ProviderException.Kind.PROTOCOL, // NOPMD - the cause may quote response text; never in logs (AI rules)
                        ProviderException.describe(ProviderException.Kind.PROTOCOL));
            }
            if (chunk.has("error")) {
                throw streamError(chunk.get("error"));
            }
            if (chunk.has("model") && !chunk.get("model").isJsonNull()) {
                model = chunk.get("model").getAsString();
            }
            if (chunk.has("usage") && chunk.get("usage").isJsonObject()) {
                usage = usage(chunk.getAsJsonObject("usage"));
            }
            if (!chunk.has("choices") || !chunk.get("choices").isJsonArray() || chunk.getAsJsonArray("choices").isEmpty()) {
                continue;
            }
            JsonObject choice = chunk.getAsJsonArray("choices").get(0).getAsJsonObject();
            if (choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()) {
                finish = choice.get("finish_reason").getAsString();
            }
            JsonObject delta = choice.has("delta") && choice.get("delta").isJsonObject()
                    ? choice.getAsJsonObject("delta") : new JsonObject();
            if (delta.has("content") && !delta.get("content").isJsonNull()) {
                String piece = delta.get("content").getAsString();
                text.append(piece);
                if (onTextDelta != null && !piece.isEmpty()) {
                    onTextDelta.accept(piece);
                }
            }
            if (delta.has("tool_calls") && delta.get("tool_calls").isJsonArray()) {
                for (JsonElement element : delta.getAsJsonArray("tool_calls")) {
                    JsonObject part = element.getAsJsonObject();
                    int index = part.has("index") ? part.get("index").getAsInt() : calls.size();
                    CallBuilder call = calls.computeIfAbsent(index, i -> new CallBuilder());
                    if (part.has("id") && !part.get("id").isJsonNull()) {
                        call.id = part.get("id").getAsString();
                    }
                    if (part.has("function") && part.get("function").isJsonObject()) {
                        JsonObject f = part.getAsJsonObject("function");
                        if (f.has("name") && !f.get("name").isJsonNull()) {
                            call.name.append(f.get("name").getAsString());
                        }
                        if (f.has("arguments") && !f.get("arguments").isJsonNull()) {
                            call.arguments.append(f.get("arguments").getAsString());
                        }
                    }
                }
            }
        }
        if (!done && finish == null) {
            throw new ProviderException(ProviderException.Kind.PROTOCOL, "The provider's answer ended early.");
        }
        return response(text.toString(), calls.values().stream().map(CallBuilder::build).toList(), finish, usage, model);
    }

    private ChatResponse readWhole(InputStream in, Consumer<String> onTextDelta) throws IOException, ProviderException {
        JsonObject json;
        try {
            json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new ProviderException(ProviderException.Kind.PROTOCOL, // NOPMD - the cause may quote response text; never in logs (AI rules)
                    ProviderException.describe(ProviderException.Kind.PROTOCOL));
        }
        if (json.has("error")) {
            throw streamError(json.get("error"));
        }
        JsonObject choice = json.getAsJsonArray("choices").get(0).getAsJsonObject();
        JsonObject message = choice.getAsJsonObject("message");
        String text = message.has("content") && !message.get("content").isJsonNull()
                ? message.get("content").getAsString() : "";
        if (onTextDelta != null && !text.isEmpty()) {
            onTextDelta.accept(text);
        }
        List<ToolCall> calls = new ArrayList<>();
        if (message.has("tool_calls") && message.get("tool_calls").isJsonArray()) {
            for (JsonElement element : message.getAsJsonArray("tool_calls")) {
                JsonObject c = element.getAsJsonObject();
                JsonObject f = c.getAsJsonObject("function");
                CallBuilder b = new CallBuilder();
                b.id = c.has("id") ? c.get("id").getAsString() : null;
                b.name.append(f.get("name").getAsString());
                b.arguments.append(f.has("arguments") && !f.get("arguments").isJsonNull()
                        ? f.get("arguments").getAsString() : "");
                calls.add(b.build());
            }
        }
        String finish = choice.has("finish_reason") && !choice.get("finish_reason").isJsonNull()
                ? choice.get("finish_reason").getAsString() : null;
        Usage usage = json.has("usage") && json.get("usage").isJsonObject() ? usage(json.getAsJsonObject("usage"))
                : Usage.UNKNOWN;
        String model = json.has("model") && !json.get("model").isJsonNull() ? json.get("model").getAsString() : null;
        return response(text, calls, finish, usage, model);
    }

    private static ChatResponse response(String text, List<ToolCall> calls, String finish, Usage usage, String model) {
        StopReason stop = switch (finish == null ? "" : finish) {
            case "stop" -> StopReason.END;
            case "length" -> StopReason.MAX_TOKENS;
            case "tool_calls", "function_call" -> StopReason.TOOL_USE;
            case "content_filter" -> StopReason.CONTENT_FILTER;
            default -> calls.isEmpty() ? StopReason.END : StopReason.TOOL_USE;
        };
        ChatMessage message = new ChatMessage(ChatMessage.Role.ASSISTANT, text, calls, null, false, null);
        return new ChatResponse(text, calls, stop, usage, model, message);
    }

    private static Usage usage(JsonObject usage) {
        long in = usage.has("prompt_tokens") ? usage.get("prompt_tokens").getAsLong() : -1;
        long out = usage.has("completion_tokens") ? usage.get("completion_tokens").getAsLong() : -1;
        return new Usage(in, out);
    }

    private ProviderException failure(int status, String errorBody, HttpURLConnection connection) {
        ProviderException.Kind kind = ProviderException.kindOf(status);
        String detail = errorMessage(errorBody);
        logger.debug("Provider {} answered HTTP {}", config.host(), status);
        return new ProviderException(kind, ProviderException.describe(kind) + " (HTTP " + status + ")"
                + (detail.isEmpty() ? "" : ": " + detail), status, ProviderHttp.retryAfter(connection),
                connection.getHeaderField("x-request-id"), null);
    }

    private static ProviderException streamError(JsonElement error) {
        String message = error.isJsonObject() && error.getAsJsonObject().has("message")
                ? error.getAsJsonObject().get("message").getAsString() : String.valueOf(error);
        return new ProviderException(ProviderException.Kind.SERVER,
                ProviderException.describe(ProviderException.Kind.SERVER) + ": " + shorten(message));
    }

    /** {@code error.message} of an error body, shortened; the raw text when it is not JSON. */
    static String errorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonElement json = JsonParser.parseString(body);
            if (json.isJsonObject() && json.getAsJsonObject().has("error")) {
                JsonElement error = json.getAsJsonObject().get("error");
                if (error.isJsonObject() && error.getAsJsonObject().has("message")) {
                    return shorten(error.getAsJsonObject().get("message").getAsString());
                }
                if (error.isJsonPrimitive()) {
                    return shorten(error.getAsString());
                }
            }
        } catch (JsonParseException | IllegalStateException e) {
            // not JSON
        }
        return shorten(body.strip());
    }

    private static String shorten(String text) {
        String one = text.replaceAll("\\s+", " ").strip();
        return one.length() > 300 ? one.substring(0, 300) + "…" : one;
    }

    private static final class CallBuilder {
        String id;
        final StringBuilder name = new StringBuilder();
        final StringBuilder arguments = new StringBuilder();

        ToolCall build() {
            String raw = arguments.toString();
            JsonObject parsed = null;
            try {
                JsonElement json = JsonParser.parseString(raw.isBlank() ? "{}" : raw);
                parsed = json.isJsonObject() ? json.getAsJsonObject() : null;
            } catch (JsonParseException e) {
                parsed = null;
            }
            return new ToolCall(id == null ? "call_" + Integer.toHexString(System.identityHashCode(this)) : id,
                    name.toString(), parsed, raw);
        }
    }
}
