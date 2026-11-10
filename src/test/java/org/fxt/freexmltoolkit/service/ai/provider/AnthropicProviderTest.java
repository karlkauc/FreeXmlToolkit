package org.fxt.freexmltoolkit.service.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.service.ai.provider.anthropic.AnthropicProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** The Anthropic provider (official SDK on the ConnectionService transport) against Anthropic-shaped scripts. */
class AnthropicProviderTest {

    static final String KEY = "sk-ant-test-SECRET-0815";

    private FakeChatServer server;

    @BeforeEach
    void start() throws Exception {
        server = new FakeChatServer();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private AnthropicProvider provider() {
        return new AnthropicProvider(config(30), KEY);
    }

    private ProviderConfig config(int readTimeout) {
        return new ProviderConfig("a", "anthropic", ProviderKind.ANTHROPIC, ProviderPreset.ANTHROPIC, server.url(""),
                "test-model", null, null, null, 1000, readTimeout);
    }

    static String start(String id) {
        return "event: message_start\ndata: {\"type\":\"message_start\",\"message\":{\"id\":\"" + id + "\",\"type\":\"message\","
                + "\"role\":\"assistant\",\"model\":\"test-model\",\"content\":[],\"stop_reason\":null,"
                + "\"stop_sequence\":null,\"usage\":{\"input_tokens\":7,\"output_tokens\":1}}}";
    }

    static String textBlock(int index, String... pieces) {
        StringBuilder events = new StringBuilder("event: content_block_start\ndata: {\"type\":\"content_block_start\",\"index\":"
                + index + ",\"content_block\":{\"type\":\"text\",\"text\":\"\"}}\n\n");
        for (String piece : pieces) {
            events.append("event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":").append(index)
                    .append(",\"delta\":{\"type\":\"text_delta\",\"text\":\"").append(piece).append("\"}}\n\n");
        }
        events.append("event: content_block_stop\ndata: {\"type\":\"content_block_stop\",\"index\":").append(index).append('}');
        return events.toString();
    }

    static String stopEvents(String reason, int outputTokens) {
        return "event: message_delta\ndata: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"" + reason
                + "\",\"stop_sequence\":null},\"usage\":{\"output_tokens\":" + outputTokens + "}}\n\n"
                + "event: message_stop\ndata: {\"type\":\"message_stop\"}";
    }

    private JsonObject lastBody() {
        return JsonParser.parseString(server.last().body()).getAsJsonObject();
    }

    @Test
    void streamedTextWithHeadersAndNoTemperature() throws Exception {
        server.reply(FakeChatServer.Reply.sse(0, start("msg_1"), textBlock(0, "Hel", "lo"), stopEvents("end_turn", 2)));
        List<String> deltas = new ArrayList<>();
        ChatRequest request = new ChatRequest("be brief", List.of(ChatMessage.user("hi")), List.of(), 0, 0.7);
        ChatResponse r = provider().chat(request, deltas::add, CancelToken.none());
        assertEquals("Hello", r.text());
        assertEquals(List.of("Hel", "lo"), deltas);
        assertEquals(StopReason.END, r.stop());
        assertEquals(7, r.usage().inputTokens());
        assertEquals("/v1/messages", server.last().path());
        assertEquals(KEY, server.last().header("x-api-key"));
        assertTrue(server.last().header("anthropic-version") != null);
        JsonObject body = lastBody();
        assertFalse(body.has("temperature"), "API version 2026-10-21 rejects temperature");
        assertFalse(body.has("tool_choice"));
        assertEquals(1000, body.get("max_tokens").getAsInt());
        assertTrue(body.toString().contains("be brief"));
    }

    @Test
    void toolUseRoundTripReplaysTheAssistantTurnUnchanged() throws Exception {
        String thinking = "event: content_block_start\ndata: {\"type\":\"content_block_start\",\"index\":0,"
                + "\"content_block\":{\"type\":\"thinking\",\"thinking\":\"\",\"signature\":\"\"}}\n\n"
                + "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":0,"
                + "\"delta\":{\"type\":\"signature_delta\",\"signature\":\"SIG-123\"}}\n\n"
                + "event: content_block_stop\ndata: {\"type\":\"content_block_stop\",\"index\":0}";
        String toolUse = "event: content_block_start\ndata: {\"type\":\"content_block_start\",\"index\":1,"
                + "\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"xpath\",\"input\":{}}}\n\n"
                + "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":1,"
                + "\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"expression\\\":\"}}\n\n"
                + "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":1,"
                + "\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"\\\"//a\\\"}\"}}\n\n"
                + "event: content_block_stop\ndata: {\"type\":\"content_block_stop\",\"index\":1}";
        server.reply(FakeChatServer.Reply.sse(0, start("msg_2"), thinking, toolUse, stopEvents("tool_use", 20)));
        JsonObject schema = JsonParser.parseString("{\"type\":\"object\",\"properties\":{\"expression\":{\"type\":\"string\"}},"
                + "\"required\":[\"expression\"],\"additionalProperties\":false}").getAsJsonObject();
        ChatRequest request = new ChatRequest("", List.of(ChatMessage.user("count")),
                List.of(new ToolSpec("xpath", "XPath", schema)), 0, null);
        ChatResponse r = provider().chat(request, null, CancelToken.none());
        assertEquals(StopReason.TOOL_USE, r.stop());
        assertEquals("//a", r.toolCalls().getFirst().arguments().get("expression").getAsString());
        JsonObject tool = lastBody().getAsJsonArray("tools").get(0).getAsJsonObject();
        assertEquals("xpath", tool.get("name").getAsString());
        assertEquals("expression", tool.getAsJsonObject("input_schema").getAsJsonArray("required").get(0).getAsString());

        server.reply(FakeChatServer.Reply.sse(0, start("msg_3"), textBlock(0, "3"), stopEvents("end_turn", 1)));
        ChatRequest next = request.plus(List.of(r.message(), ChatMessage.toolResult("toolu_1", "3 items", false)));
        assertEquals("3", provider().chat(next, null, CancelToken.none()).text());
        JsonArray messages = lastBody().getAsJsonArray("messages");
        JsonArray assistant = messages.get(1).getAsJsonObject().getAsJsonArray("content");
        assertEquals("thinking", assistant.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("SIG-123", assistant.get(0).getAsJsonObject().get("signature").getAsString());
        assertEquals("tool_use", assistant.get(1).getAsJsonObject().get("type").getAsString());
        JsonObject result = messages.get(2).getAsJsonObject().getAsJsonArray("content").get(0).getAsJsonObject();
        assertEquals("tool_result", result.get("type").getAsString());
        assertEquals("toolu_1", result.get("tool_use_id").getAsString());
    }

    @Test
    void refusalIsReported() throws Exception {
        server.reply(FakeChatServer.Reply.sse(0, start("msg_4"), stopEvents("refusal", 0)));
        assertEquals(StopReason.REFUSED, provider().chat(ChatRequest.of("", "x"), null, CancelToken.none()).stop());
    }

    @Test
    void errorsBecomeKinds() {
        assertKind(401, ProviderException.Kind.AUTH, Map.of());
        assertKind(404, ProviderException.Kind.NOT_FOUND, Map.of());
        assertKind(529, ProviderException.Kind.OVERLOADED, Map.of());
        server.reply(FakeChatServer.Reply.status(429,
                "{\"type\":\"error\",\"error\":{\"type\":\"rate_limit_error\",\"message\":\"slow down\"}}",
                Map.of("retry-after", "11", "request-id", "req_42")));
        ProviderException e = assertThrows(ProviderException.class,
                () -> provider().chat(ChatRequest.of("", "x"), null, CancelToken.none()));
        assertEquals(ProviderException.Kind.RATE_LIMIT, e.kind());
        assertEquals(11, e.retryAfterSeconds().getAsInt());
        assertEquals("req_42", e.requestId());
        assertTrue(e.getMessage().contains("slow down"), e.getMessage());
    }

    private void assertKind(int status, ProviderException.Kind kind, Map<String, String> headers) {
        int before = server.requests().size();
        server.reply(FakeChatServer.Reply.status(status, "{\"type\":\"error\",\"error\":{\"type\":\"x\",\"message\":\"m\"}}",
                headers));
        ProviderException e = assertThrows(ProviderException.class,
                () -> provider().chat(ChatRequest.of("", "x"), null, CancelToken.none()));
        assertEquals(kind, e.kind());
        assertFalse(e.getMessage().contains(KEY));
        assertEquals(before + 1, server.requests().size(), "no retries inside the SDK");
    }

    @Test
    void midStreamErrorEvent() {
        server.reply(FakeChatServer.Reply.sse(0, start("msg_5"),
                "event: error\ndata: {\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}"));
        ProviderException e = assertThrows(ProviderException.class,
                () -> provider().chat(ChatRequest.of("", "x"), null, CancelToken.none()));
        assertTrue(e.kind() == ProviderException.Kind.OVERLOADED || e.kind() == ProviderException.Kind.SERVER
                || e.kind() == ProviderException.Kind.PROTOCOL, e.kind().toString());
    }

    @Test
    void cancelMidStream() {
        server.reply(FakeChatServer.Reply.sse(400, start("msg_6"), textBlock(0, "a", "b", "c", "d"), stopEvents("end_turn", 4)));
        CancelToken cancel = new CancelToken();
        long begin = System.nanoTime();
        ProviderException e = assertThrows(ProviderException.class, () -> provider().chat(ChatRequest.of("", "x"), d -> cancel.cancel(),
                cancel));
        assertEquals(ProviderException.Kind.CANCELLED, e.kind());
        assertTrue((System.nanoTime() - begin) / 1_000_000 < 3000);
    }

    @Test
    void theKeyIsInNoLogLine() throws Exception {
        try (LogCapture log = LogCapture.capture()) {
            server.reply(FakeChatServer.Reply.status(401,
                    "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}", Map.of()));
            assertThrows(ProviderException.class, () -> provider().chat(ChatRequest.of("", "TOP-SECRET-PROMPT"), null,
                    CancelToken.none()));
            assertFalse(log.contains(KEY), log.lines().toString());
            assertFalse(log.lines().stream().filter(l -> !l.startsWith("DEBUG") && !l.startsWith("TRACE"))
                    .anyMatch(l -> l.contains("TOP-SECRET-PROMPT")), log.lines().toString());
        }
    }
}
