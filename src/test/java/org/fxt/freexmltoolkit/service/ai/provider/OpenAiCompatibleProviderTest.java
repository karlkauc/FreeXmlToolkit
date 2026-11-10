package org.fxt.freexmltoolkit.service.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class OpenAiCompatibleProviderTest {

    private static final String KEY = "sk-test-SECRET-4711";

    private FakeChatServer server;

    @BeforeEach
    void start() throws Exception {
        server = new FakeChatServer();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private ProviderConfig config(TokenLimitField field, int readTimeoutSeconds) {
        return new ProviderConfig("p", "fake", ProviderKind.OPENAI_COMPATIBLE, ProviderPreset.CUSTOM,
                server.url("/v1"), "test-model", null, AuthHeader.BEARER, field, 1000, readTimeoutSeconds);
    }

    private OpenAiCompatibleProvider provider() {
        return new OpenAiCompatibleProvider(config(TokenLimitField.MAX_TOKENS, 30), KEY);
    }

    private static ToolSpec tool() {
        JsonObject schema = JsonParser.parseString("{\"type\":\"object\",\"properties\":{\"expression\":{\"type\":\"string\"}}}")
                .getAsJsonObject();
        return new ToolSpec("xpath", "Evaluate XPath", schema);
    }

    private JsonObject lastBody() {
        return JsonParser.parseString(server.last().body()).getAsJsonObject();
    }

    @Test
    void plainJsonAnswer() throws Exception {
        server.reply(FakeChatServer.Reply.json("""
                {"model":"test-model","choices":[{"message":{"role":"assistant","content":"Hello"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":5,"completion_tokens":2}}"""));
        ChatResponse r = provider().chat(ChatRequest.of("be brief", "hi"), null, CancelToken.none());
        assertEquals("Hello", r.text());
        assertEquals(StopReason.END, r.stop());
        assertEquals(new Usage(5, 2), r.usage());
        JsonObject body = lastBody();
        assertEquals("/v1/chat/completions", server.last().path());
        assertEquals("system", body.getAsJsonArray("messages").get(0).getAsJsonObject().get("role").getAsString());
        assertEquals(1000, body.get("max_tokens").getAsInt());
        assertFalse(body.has("tool_choice"));
        assertFalse(body.has("temperature"));
        assertEquals("Bearer " + KEY, server.last().header("Authorization"));
    }

    @Test
    void streamedTextIsAssembledAndDelivered() throws Exception {
        server.reply(FakeChatServer.Reply.sse(5,
                "{\"choices\":[{\"delta\":{\"content\":\"Hel\"}}]}",
                "{\"choices\":[{\"delta\":{\"content\":\"lo\"}}]}",
                "{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}",
                "{\"choices\":[],\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":2}}",
                "[DONE]"));
        List<String> deltas = new ArrayList<>();
        ChatResponse r = provider().chat(ChatRequest.of("", "hi"), deltas::add, CancelToken.none());
        assertEquals("Hello", r.text());
        assertEquals(List.of("Hel", "lo"), deltas);
        assertEquals(new Usage(3, 2), r.usage());
        assertTrue(lastBody().get("stream").getAsBoolean());
        assertTrue(lastBody().getAsJsonObject("stream_options").get("include_usage").getAsBoolean());
    }

    @Test
    void toolCallDeltasAreJoinedByIndexAndRoundTrip() throws Exception {
        server.reply(FakeChatServer.Reply.sse(0,
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"xpath\",\"arguments\":\"{\\\"expr\"}}]}}]}",
                "{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"ession\\\":\\\"//a\\\"}\"}}]}}]}",
                "{\"choices\":[{\"delta\":{},\"finish_reason\":\"tool_calls\"}]}",
                "[DONE]"));
        ChatRequest request = new ChatRequest("", List.of(ChatMessage.user("count a")), List.of(tool()), 0, 0.2);
        ChatResponse r = provider().chat(request, null, CancelToken.none());
        assertEquals(StopReason.TOOL_USE, r.stop());
        ToolCall call = r.toolCalls().getFirst();
        assertEquals("call_1", call.id());
        assertEquals("xpath", call.name());
        assertEquals("//a", call.arguments().get("expression").getAsString());
        JsonObject body = lastBody();
        assertEquals("function", body.getAsJsonArray("tools").get(0).getAsJsonObject().get("type").getAsString());
        assertEquals(0.2, body.get("temperature").getAsDouble());

        server.reply(FakeChatServer.Reply.json(
                "{\"choices\":[{\"message\":{\"content\":\"3\"},\"finish_reason\":\"stop\"}]}"));
        ChatRequest next = request.plus(List.of(r.message(), ChatMessage.toolResult("call_1", "3 items", false)));
        assertEquals("3", provider().chat(next, null, CancelToken.none()).text());
        var messages = lastBody().getAsJsonArray("messages");
        JsonObject assistant = messages.get(1).getAsJsonObject();
        assertEquals("call_1", assistant.getAsJsonArray("tool_calls").get(0).getAsJsonObject().get("id").getAsString());
        JsonObject toolMessage = messages.get(2).getAsJsonObject();
        assertEquals("tool", toolMessage.get("role").getAsString());
        assertEquals("call_1", toolMessage.get("tool_call_id").getAsString());
    }

    @Test
    void invalidArgumentJsonIsKeptRaw() throws Exception {
        server.reply(FakeChatServer.Reply.json("""
                {"choices":[{"message":{"content":null,"tool_calls":[{"id":"c","type":"function",
                 "function":{"name":"xpath","arguments":"{not json"}}]},"finish_reason":"tool_calls"}]}"""));
        ToolCall call = provider().chat(ChatRequest.of("", "x"), null, CancelToken.none()).toolCalls().getFirst();
        assertNull(call.arguments());
        assertEquals("{not json", call.rawArguments());
    }

    @Test
    void theTokenFieldFollowsTheConfiguration() throws Exception {
        server.reply(FakeChatServer.Reply.json("{\"choices\":[{\"message\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}"));
        new OpenAiCompatibleProvider(config(TokenLimitField.MAX_COMPLETION_TOKENS, 30), KEY)
                .chat(ChatRequest.of("", "x"), null, CancelToken.none());
        assertTrue(lastBody().has("max_completion_tokens"));
        assertFalse(lastBody().has("max_tokens"));
    }

    @Test
    void httpErrorsBecomeKinds() {
        assertKind(401, ProviderException.Kind.AUTH, Map.of());
        assertKind(403, ProviderException.Kind.PERMISSION, Map.of());
        assertKind(404, ProviderException.Kind.NOT_FOUND, Map.of());
        assertKind(503, ProviderException.Kind.OVERLOADED, Map.of());
        server.reply(FakeChatServer.Reply.status(429, "{\"error\":{\"message\":\"slow down\"}}", Map.of("Retry-After", "7")));
        ProviderException e = assertThrows(ProviderException.class,
                () -> provider().chat(ChatRequest.of("", "x"), null, CancelToken.none()));
        assertEquals(ProviderException.Kind.RATE_LIMIT, e.kind());
        assertEquals(7, e.retryAfterSeconds().getAsInt());
        assertTrue(e.getMessage().contains("slow down"), e.getMessage());
    }

    private void assertKind(int status, ProviderException.Kind kind, Map<String, String> headers) {
        server.reply(FakeChatServer.Reply.status(status, "{\"error\":{\"message\":\"m\"}}", headers));
        ProviderException e = assertThrows(ProviderException.class,
                () -> provider().chat(ChatRequest.of("", "x"), null, CancelToken.none()));
        assertEquals(kind, e.kind());
        assertFalse(e.getMessage().contains(KEY));
    }

    @Test
    void anUnreachableServer() {
        ProviderConfig nowhere = new ProviderConfig("p", "x", ProviderKind.OPENAI_COMPATIBLE, ProviderPreset.CUSTOM,
                "http://127.0.0.1:1/v1", "m", null, null, null, 0, 30);
        ProviderException e = assertThrows(ProviderException.class,
                () -> new OpenAiCompatibleProvider(nowhere, KEY).chat(ChatRequest.of("", "x"), null, CancelToken.none()));
        assertEquals(ProviderException.Kind.UNREACHABLE, e.kind());
    }

    @Test
    void aSlowFirstByteHitsTheReadTimeout() {
        server.reply(FakeChatServer.Reply.sse(12_000, "{\"choices\":[{\"delta\":{\"content\":\"late\"}}]}", "[DONE]"));
        ProviderException e = assertThrows(ProviderException.class,
                () -> new OpenAiCompatibleProvider(config(TokenLimitField.MAX_TOKENS, 10), KEY)
                        .chat(ChatRequest.of("", "x"), null, CancelToken.none()));
        assertEquals(ProviderException.Kind.TIMEOUT, e.kind());
    }

    @Test
    void cancelMidStream() throws Exception {
        server.reply(FakeChatServer.Reply.sse(400, "{\"choices\":[{\"delta\":{\"content\":\"a\"}}]}",
                "{\"choices\":[{\"delta\":{\"content\":\"b\"}}]}", "{\"choices\":[{\"delta\":{\"content\":\"c\"}}]}",
                "{\"choices\":[{\"delta\":{\"content\":\"d\"}}]}", "[DONE]"));
        CancelToken cancel = new CancelToken();
        List<String> deltas = new ArrayList<>();
        long start = System.nanoTime();
        ProviderException e = assertThrows(ProviderException.class, () -> provider().chat(ChatRequest.of("", "x"), d -> {
            deltas.add(d);
            cancel.cancel();
        }, cancel));
        assertEquals(ProviderException.Kind.CANCELLED, e.kind());
        assertTrue((System.nanoTime() - start) / 1_000_000 < 1500);
    }

    @Test
    void theKeyIsInNoLogLine() throws Exception {
        try (LogCapture log = LogCapture.capture()) {
            server.reply(FakeChatServer.Reply.status(401, "{\"error\":{\"message\":\"bad key\"}}", Map.of()));
            assertThrows(ProviderException.class, () -> provider().chat(ChatRequest.of("", "TOP-SECRET-PROMPT"), null,
                    CancelToken.none()));
            assertFalse(log.contains(KEY), log.lines().toString());
            assertFalse(log.lines().stream().filter(l -> !l.startsWith("DEBUG") && !l.startsWith("TRACE"))
                    .anyMatch(l -> l.contains("TOP-SECRET-PROMPT")), log.lines().toString());
        }
    }

    @Test
    void modelList() throws Exception {
        server.reply(FakeChatServer.Reply.json("{\"object\":\"list\",\"data\":[{\"id\":\"b-model\"},{\"id\":\"a-model\"}]}"));
        assertEquals(List.of("a-model", "b-model"), provider().listModels(CancelToken.none()));
        assertEquals("/v1/models", server.last().path());
        assertEquals("GET", server.last().method());
    }

    @Test
    void anAzureStyleKeyHeader() throws Exception {
        ProviderConfig azure = new ProviderConfig("p", "az", ProviderKind.OPENAI_COMPATIBLE, ProviderPreset.CUSTOM,
                server.url("/openai/v1"), "deployment", null, AuthHeader.API_KEY, null, 0, 30);
        server.reply(FakeChatServer.Reply.json("{\"choices\":[{\"message\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}"));
        new OpenAiCompatibleProvider(azure, KEY).chat(ChatRequest.of("", "x"), null, CancelToken.none());
        assertEquals(KEY, server.last().header("api-key"));
        assertNull(server.last().header("Authorization"));
    }
}
