package org.fxt.freexmltoolkit.service.ai.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;

class ToolRegistryTest {

    @TempDir
    Path temp;

    /** A tool for the tests: behaviour chosen by its name. */
    private record FakeTool(String name, Behaviour behaviour) implements AiTool {
        enum Behaviour { OK, MISUSE, CRASH, HANG }

        @Override public String description() { return "fake " + name; }

        @Override public JsonObject inputSchema() { return ToolSchemas.object(); }

        @Override
        public ToolResult call(JsonObject arguments, ToolContext context) throws ToolException {
            return switch (behaviour) {
                case OK -> ToolResult.ok("done", null);
                case MISUSE -> throw new ToolException("argument 'x' is required");
                case CRASH -> throw new IllegalStateException("boom");
                case HANG -> {
                    try {
                        Thread.sleep(60_000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    yield ToolResult.ok("late", null);
                }
            };
        }
    }

    private ToolRegistry registry() {
        return new ToolRegistry(List.of(
                new FakeTool("zeta", FakeTool.Behaviour.OK),
                new FakeTool("alpha", FakeTool.Behaviour.OK),
                new FakeTool("misuse", FakeTool.Behaviour.MISUSE),
                new FakeTool("crash", FakeTool.Behaviour.CRASH),
                new FakeTool("hang", FakeTool.Behaviour.HANG)));
    }

    private ToolContext context(Duration timeout) {
        return ToolContext.of(new WorkspaceAccess(List.of(temp)),
                new ToolLimits(timeout, 1000, 1000, 10));
    }

    @Test
    void toolsAreListedInADeterministicOrder() {
        assertEquals(List.of("alpha", "crash", "hang", "misuse", "zeta"),
                registry().tools().stream().map(AiTool::name).toList());
    }

    @Test
    void onlyWithAnUnknownNameThrowsNamingTheValidTools() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> registry().only(List.of("alpha", "nope")));
        assertTrue(e.getMessage().contains("nope") && e.getMessage().contains("alpha"), e.getMessage());
        assertEquals(List.of("alpha"), registry().only(List.of("alpha")).tools().stream().map(AiTool::name).toList());
    }

    @Test
    void duplicateNamesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ToolRegistry(List.of(
                new FakeTool("a", FakeTool.Behaviour.OK), new FakeTool("a", FakeTool.Behaviour.OK))));
    }

    @Test
    void unknownToolIsAnErrorResult() {
        ToolResult result = registry().call("nope", new JsonObject(), context(Duration.ofSeconds(5)));
        assertTrue(result.error());
        assertTrue(result.text().contains("nope"));
    }

    @Test
    void successIsPassedThrough() {
        ToolResult result = registry().call("alpha", new JsonObject(), context(Duration.ofSeconds(5)));
        assertFalse(result.error());
        assertEquals("done", result.text());
    }

    @Test
    void misuseIsAnErrorResultWithTheMessage() {
        ToolResult result = registry().call("misuse", new JsonObject(), context(Duration.ofSeconds(5)));
        assertTrue(result.error());
        assertTrue(result.text().contains("argument 'x' is required"), result.text());
    }

    @Test
    void aCrashingToolIsContained() {
        ToolResult result = registry().call("crash", new JsonObject(), context(Duration.ofSeconds(5)));
        assertTrue(result.error());
        assertTrue(result.text().contains("crash"), result.text());
    }

    @Test
    void aHangingToolTimesOut() {
        long start = System.nanoTime();
        ToolResult result = registry().call("hang", new JsonObject(), context(Duration.ofMillis(300)));
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertTrue(result.error());
        assertTrue(result.text().contains("time"), result.text());
        assertTrue(millis < 5_000, "returned after " + millis + " ms");
        assertTrue(result.structured().get("timed_out").getAsBoolean());
    }

    @Test
    void truncateMarksAndReportsTheLength() {
        ToolLimits limits = new ToolLimits(Duration.ofSeconds(1), 10, 100, 5);
        assertEquals("short", limits.truncate("short"));
        String cut = limits.truncate("x".repeat(25));
        assertTrue(cut.startsWith("x".repeat(10)));
        assertTrue(cut.contains("truncated") && cut.contains("25"), cut);
        assertTrue(limits.isTruncated("x".repeat(11)));
    }
}
