package org.fxt.freexmltoolkit.controls.theme;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Inline font sizes do not follow Settings → Appearance → Text size: labels take a
 * {@code fxt-text-*} class (unified-shell.css), plain popups {@link Typography#fontSize}.
 */
class InlineFontSizeGuardTest {

    /** Sizes that follow other settings: the editor font, the grid's zoom, the generated sheets. */
    private static final Set<String> ALLOWED = Set.of("Typography.java", "EditorFont.java",
            "InlineValueLayer.java", "GridInlineEditor.java");
    private static final Pattern FIXED = Pattern.compile("-fx-font-size\\s*:\\s*[0-9]");

    @Test
    void noFixedInlineFontSizes() throws IOException {
        List<String> found = new ArrayList<>();
        scan(Path.of("src/main/java"), ".java", found);
        scan(Path.of("src/main/resources/pages"), ".fxml", found);
        assertTrue(found.isEmpty(), "use a fxt-text-* class (or Typography.fontSize in a popup): " + found);
    }

    private static void scan(Path root, String suffix, List<String> found) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(suffix)).toList()) {
                if (ALLOWED.contains(file.getFileName().toString())) {
                    continue;
                }
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    if (FIXED.matcher(lines.get(i)).find()) {
                        found.add(file.getFileName() + ":" + (i + 1));
                    }
                }
            }
        }
    }
}
