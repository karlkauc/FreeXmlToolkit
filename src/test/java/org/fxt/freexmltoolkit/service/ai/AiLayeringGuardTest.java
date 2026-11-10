package org.fxt.freexmltoolkit.service.ai;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The AI tool layer serves the command line ({@code fxt-cli mcp}) and the UI, so {@code service/ai} imports no
 * JavaFX; the MCP SDK and Reactor stay inside {@code service/ai/mcp}, behind the adapter, so the choice of the
 * MCP implementation stays reversible (decision D4 in {@code docs/superpowers/specs/2026-11-10-ai-integration-design.md}).
 */
class AiLayeringGuardTest {

    private static final Path AI = Path.of("src/main/java/org/fxt/freexmltoolkit/service/ai");
    private static final Path MCP = AI.resolve("mcp");
    private static final Pattern JAVAFX = Pattern.compile("^import\\s+(static\\s+)?javafx\\.");
    private static final Pattern MCP_SDK = Pattern.compile(
            "^import\\s+(static\\s+)?(io\\.modelcontextprotocol|reactor\\.|org\\.reactivestreams)\\.?");

    @Test
    void theAiLayerHasNoUiAndNoSdkOutsideTheAdapter() throws IOException {
        if (!Files.isDirectory(AI)) {
            return;
        }
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(AI)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).strip();
                    if (JAVAFX.matcher(line).find()) {
                        offenders.add(file + ":" + (i + 1) + "  JavaFX: " + line);
                    }
                    if (!file.startsWith(MCP) && MCP_SDK.matcher(line).find()) {
                        offenders.add(file + ":" + (i + 1) + "  MCP SDK outside service/ai/mcp: " + line);
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(), "AI layering violated:\n" + String.join("\n", offenders));
    }
}
