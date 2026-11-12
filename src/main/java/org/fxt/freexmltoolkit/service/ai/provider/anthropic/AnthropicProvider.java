package org.fxt.freexmltoolkit.service.ai.provider.anthropic;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.ConnectionServiceImpl;
import org.fxt.freexmltoolkit.service.ai.provider.CancelToken;
import org.fxt.freexmltoolkit.service.ai.provider.ChatMessage;
import org.fxt.freexmltoolkit.service.ai.provider.ChatProvider;
import org.fxt.freexmltoolkit.service.ai.provider.ChatRequest;
import org.fxt.freexmltoolkit.service.ai.provider.ChatResponse;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderConfig;
import org.fxt.freexmltoolkit.service.ai.provider.ProviderException;
import org.fxt.freexmltoolkit.service.ai.provider.StopReason;
import org.fxt.freexmltoolkit.service.ai.provider.ToolCall;
import org.fxt.freexmltoolkit.service.ai.provider.ToolSpec;
import org.fxt.freexmltoolkit.service.ai.provider.Usage;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.AnthropicClientImpl;
import com.anthropic.core.ClientOptions;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlockParam;
import com.anthropic.models.models.ModelInfo;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The Anthropic Messages API through the official Java SDK (decision D13) on the {@code ConnectionService} transport.
 *
 * <p>Rules from the documentation (platform.claude.com, 2026-11-10), most handled by the SDK:
 * <ul>
 *   <li>API version {@code 2026-10-21} rejects {@code temperature}, {@code top_p}, {@code top_k} — never sent
 *       (/docs/en/api/versioning.md);</li>
 *   <li>{@code tool_choice} stays {@code auto} — forced {@code any}/{@code tool} is a 400 on current models
 *       (/docs/en/api/errors.md);</li>
 *   <li>assistant turns are replayed exactly as received, {@code thinking} blocks included (same page) — kept in
 *       {@link ChatMessage#providerState()} as the SDK's own block parameters;</li>
 *   <li>{@code stop_reason: "refusal"} → {@link StopReason#REFUSED}.</li>
 * </ul>
 * Retries are the caller's ({@code maxRetries(0)}); nothing of a request or answer is logged above DEBUG.
 */
public final class AnthropicProvider implements ChatProvider {

    private static final Logger logger = LogManager.getLogger(AnthropicProvider.class);
    private static final Gson GSON = new Gson();

    private final ProviderConfig config;
    private final ConnectionServiceHttpClient transport;
    private final AnthropicClient client;

    public AnthropicProvider(ProviderConfig config, String apiKey) {
        this(config, apiKey, uri -> ConnectionServiceImpl.getInstance().openConnection(uri));
    }

    AnthropicProvider(ProviderConfig config, String apiKey, ConnectionServiceHttpClient.Opener opener) {
        this.config = config;
        this.transport = new ConnectionServiceHttpClient(opener, config.readTimeoutSeconds());
        ClientOptions.Builder builder = ClientOptions.builder()
                .httpClient(transport)
                .baseUrl(config.baseUrl())
                .maxRetries(0)
                .openTelemetry(false)
                .timeout(Duration.ofSeconds(Math.max(config.readTimeoutSeconds() * 4L, 600)));
        // ClientOptions.Builder.backend(…) is Kotlin-synthetic, and generic credentials go out as "Authorization:
        // Bearer"; the Messages API takes the key as X-Api-Key, which the SDK leaves alone when it is already set
        if (apiKey != null && !apiKey.isEmpty()) {
            builder.putHeader("X-Api-Key", apiKey);
        }
        ClientOptions options = builder.build();
        this.client = new AnthropicClientImpl(options);
    }

    @Override
    public synchronized ChatResponse chat(ChatRequest request, Consumer<String> onTextDelta, CancelToken cancel)
            throws ProviderException {
        CancelToken token = cancel == null ? CancelToken.none() : cancel;
        token.throwIfCancelled();
        transport.cancelWith(token);
        MessageCreateParams params = params(request);
        logger.debug("Messages request to {} ({} messages)", config.host(), request.messages().size());
        MessageAccumulator accumulator = MessageAccumulator.create();
        try (StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(params)) {
            stream.stream().forEach(event -> {
                if (token.isCancelled()) {
                    throw new CancelledSignal();
                }
                accumulator.accumulate(event);
                if (onTextDelta != null) {
                    event.contentBlockDelta().flatMap(d -> d.delta().text()).ifPresent(t -> onTextDelta.accept(t.text()));
                }
            });
        } catch (CancelledSignal e) {
            throw cancelled(e);
        } catch (AnthropicServiceException e) {
            throw translate(e);
        } catch (AnthropicIoException | java.io.UncheckedIOException e) {
            if (token.isCancelled()) {
                throw cancelled(e);
            }
            throw ioFailure(e);
        } catch (RuntimeException e) {
            if (token.isCancelled()) {
                throw cancelled(e);
            }
            logger.debug("Anthropic request failed", e);
            throw new ProviderException(ProviderException.Kind.PROTOCOL,
                    ProviderException.describe(ProviderException.Kind.PROTOCOL) + " (" + e.getClass().getSimpleName()
                            + ")", -1, -1, null, e);
        } finally {
            transport.cancelWith(CancelToken.none());
        }
        return response(accumulator.message());
    }

    @Override
    public List<String> listModels(CancelToken cancel) throws ProviderException {
        transport.cancelWith(cancel);
        try {
            List<String> ids = new ArrayList<>();
            for (ModelInfo model : client.models().list().autoPager()) {
                ids.add(model.id());
            }
            return ids;
        } catch (AnthropicServiceException e) {
            throw translate(e);
        } catch (RuntimeException e) {
            throw ioFailure(e);
        } finally {
            transport.cancelWith(CancelToken.none());
        }
    }

    // ---------------------------------------------------------------- request

    MessageCreateParams params(ChatRequest request) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(config.model())
                .maxTokens(request.maxOutputTokens() > 0 ? request.maxOutputTokens() : config.maxOutputTokens());
        if (!request.system().isBlank()) {
            builder.system(request.system());
        }
        // temperature is never sent: API version 2026-10-21 rejects it
        List<MessageParam> messages = new ArrayList<>();
        List<ContentBlockParam> pendingResults = new ArrayList<>();
        for (ChatMessage m : request.messages()) {
            if (m.role() == ChatMessage.Role.TOOL) {
                // all results of one assistant turn go back in one user message
                pendingResults.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                        .toolUseId(m.toolCallId()).content(m.text()).isError(m.toolError()).build()));
                continue;
            }
            if (!pendingResults.isEmpty()) {
                messages.add(MessageParam.ofUserOfContentBlockParams(List.copyOf(pendingResults)));
                pendingResults.clear();
            }
            messages.add(switch (m.role()) {
                case USER -> MessageParam.ofUser(m.text());
                case ASSISTANT -> assistantMessage(m);
                case TOOL -> throw new IllegalStateException();
            });
        }
        if (!pendingResults.isEmpty()) {
            messages.add(MessageParam.ofUserOfContentBlockParams(List.copyOf(pendingResults)));
        }
        builder.messages(messages);
        for (ToolSpec spec : request.tools()) {
            builder.addTool(tool(spec));
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private static MessageParam assistantMessage(ChatMessage m) {
        if (m.providerState() instanceof List<?> blocks && !blocks.isEmpty() && blocks.getFirst() instanceof ContentBlockParam) {
            // exactly as received — thinking blocks must not change
            return MessageParam.ofAssistantOfContentBlockParams((List<ContentBlockParam>) blocks);
        }
        List<ContentBlockParam> blocks = new ArrayList<>();
        if (!m.text().isEmpty()) {
            blocks.add(ContentBlockParam.ofText(TextBlockParam.builder().text(m.text()).build()));
        }
        for (ToolCall call : m.toolCalls()) {
            blocks.add(ContentBlockParam.ofToolUse(ToolUseBlockParam.builder().id(call.id()).name(call.name())
                    .input(JsonValue.from(toJava(call.arguments() == null ? new JsonObject() : call.arguments())))
                    .build()));
        }
        return MessageParam.ofAssistantOfContentBlockParams(blocks);
    }

    private static Tool tool(ToolSpec spec) {
        JsonObject schema = spec.inputSchema();
        Tool.InputSchema.Builder input = Tool.InputSchema.builder();
        if (schema.has("properties")) {
            Tool.InputSchema.Properties.Builder properties = Tool.InputSchema.Properties.builder();
            schema.getAsJsonObject("properties").entrySet().forEach(e ->
                    properties.putAdditionalProperty(e.getKey(), JsonValue.from(toJava(e.getValue()))));
            input.properties(properties.build());
        }
        if (schema.has("required") && schema.get("required").isJsonArray()) {
            List<String> required = new ArrayList<>();
            schema.getAsJsonArray("required").forEach(e -> required.add(e.getAsString()));
            input.required(required);
        }
        if (schema.has("additionalProperties")) {
            input.putAdditionalProperty("additionalProperties",
                    JsonValue.from(toJava(schema.get("additionalProperties"))));
        }
        return Tool.builder().name(spec.name()).description(spec.description()).inputSchema(input.build()).build();
    }

    private static Object toJava(JsonElement json) {
        return GSON.fromJson(json, Object.class);
    }

    // ---------------------------------------------------------------- response

    private static ChatResponse response(Message message) {
        StringBuilder text = new StringBuilder();
        List<ToolCall> calls = new ArrayList<>();
        List<ContentBlockParam> replay = new ArrayList<>();
        for (ContentBlock block : message.content()) {
            replay.add(block.toParam());
            block.text().ifPresent(t -> text.append(t.text()));
            block.toolUse().ifPresent(use -> {
                String raw = GSON.toJson(use._input().convert(Object.class));
                JsonObject args = null;
                try {
                    JsonElement parsed = JsonParser.parseString(raw);
                    args = parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
                } catch (RuntimeException ignored) {
                    // keep raw
                }
                calls.add(new ToolCall(use.id(), use.name(), args, raw));
            });
        }
        String reason = message.stopReason().map(Object::toString).orElse("");
        StopReason stop = switch (reason.toLowerCase(java.util.Locale.ROOT)) {
            case "end_turn", "stop_sequence", "pause_turn" -> StopReason.END;
            case "max_tokens", "model_context_window_exceeded" -> StopReason.MAX_TOKENS;
            case "tool_use" -> StopReason.TOOL_USE;
            case "refusal" -> StopReason.REFUSED;
            default -> calls.isEmpty() ? StopReason.OTHER : StopReason.TOOL_USE;
        };
        Usage usage = new Usage(message.usage().inputTokens(), message.usage().outputTokens());
        ChatMessage asMessage = new ChatMessage(ChatMessage.Role.ASSISTANT, text.toString(), calls, null, false,
                List.copyOf(replay));
        return new ChatResponse(text.toString(), calls, stop, usage, message.model().toString(), asMessage);
    }

    private ProviderException translate(AnthropicServiceException e) {
        int status = e.statusCode();
        ProviderException.Kind kind = switch (errorType(e)) {
            // an error event inside a 200 stream carries only its type
            case "overloaded_error" -> ProviderException.Kind.OVERLOADED;
            case "api_error" -> ProviderException.Kind.SERVER;
            case "rate_limit_error" -> ProviderException.Kind.RATE_LIMIT;
            default -> ProviderException.kindOf(status);
        };
        int retryAfter = -1;
        List<String> values = e.headers().values("retry-after");
        if (!values.isEmpty()) {
            try {
                retryAfter = (int) Math.ceil(Double.parseDouble(values.getFirst().strip()));
            } catch (NumberFormatException ignored) {
                // keep -1
            }
        }
        List<String> ids = e.headers().values("request-id");
        String requestId = ids.isEmpty() ? null : ids.getFirst();
        logger.debug("Anthropic answered HTTP {} (request {})", status, requestId);
        String detail = errorMessage(e);
        return new ProviderException(kind, ProviderException.describe(kind) + " (HTTP " + status + ")"
                + (detail.isEmpty() ? "" : ": " + detail), status, retryAfter, requestId, null);
    }

    /** {@code error.type} of the error body, or "". */
    private static String errorType(AnthropicServiceException e) {
        try {
            Object body = e.body().convert(Object.class);
            if (body instanceof Map<?, ?> map && map.get("error") instanceof Map<?, ?> error
                    && error.get("type") != null) {
                return String.valueOf(error.get("type"));
            }
        } catch (RuntimeException ignored) {
            // no readable body
        }
        return "";
    }

    /** {@code error.message} of the error body, shortened. */
    private static String errorMessage(AnthropicServiceException e) {
        try {
            Object body = e.body().convert(Object.class);
            if (body instanceof Map<?, ?> map && map.get("error") instanceof Map<?, ?> error
                    && error.get("message") != null) {
                String message = String.valueOf(error.get("message")).replaceAll("\\s+", " ").strip();
                return message.length() > 300 ? message.substring(0, 300) + "…" : message;
            }
        } catch (RuntimeException ignored) {
            // no readable body
        }
        return "";
    }

    private static ProviderException ioFailure(RuntimeException e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        ProviderException.Kind kind = cause instanceof java.net.SocketTimeoutException ? ProviderException.Kind.TIMEOUT
                : cause instanceof java.net.ConnectException || cause instanceof java.net.UnknownHostException
                ? ProviderException.Kind.UNREACHABLE : ProviderException.Kind.PROTOCOL;
        return new ProviderException(kind, ProviderException.describe(kind) + " (" + cause.getClass().getSimpleName()
                + ")", -1, -1, null, e);
    }

    private static ProviderException cancelled(Throwable e) {
        return new ProviderException(ProviderException.Kind.CANCELLED,
                ProviderException.describe(ProviderException.Kind.CANCELLED), -1, -1, null, e);
    }

    /** Leaves the stream loop when the user cancels. */
    private static final class CancelledSignal extends RuntimeException {
        CancelledSignal() {
            super(null, null, false, false);
        }
    }
}
