package org.fxt.freexmltoolkit.controls.theme;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * JavaFX CSS uses only the first name of a {@code -fx-font-family} list. On Windows a family that is
 * neither bundled nor in JavaFX's short built-in list (e.g. Consolas) makes the first lookup enumerate
 * every installed font and read font files on the FX thread - 10 s on a slow machine (telemetry
 * {@code 208b73004b98f91d}, the IntelliSense popup). Stylesheets therefore name a bundled family
 * (Inter, JetBrains Mono) or a generic one.
 */
class StylesheetFontFamilyGuardTest {

    private static final Pattern FAMILY = Pattern.compile("-fx-font-family\\s*:\\s*['\"]?([^'\",;]+)");
    private static final Pattern ALLOWED = Pattern.compile(
            "Inter( .*)?|JetBrains Mono( .*)?|monospace|sans-serif|serif|System|inherit");

    @Test
    void stylesheetsNameOnlyBundledOrGenericFamilies() throws IOException {
        List<String> found = new ArrayList<>();
        scan(Path.of("src/main/resources/css"), found);
        scan(Path.of("src/main/resources/scss"), found);
        assertTrue(found.isEmpty(), "use \"Inter\" or \"JetBrains Mono\" (bundled): " + found);
    }

    private static void scan(Path root, List<String> found) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> !p.toString().contains("node_modules"))
                    .filter(p -> p.toString().endsWith(".css") || p.toString().endsWith(".scss")).toList()) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    Matcher m = FAMILY.matcher(lines.get(i));
                    if (m.find() && !ALLOWED.matcher(m.group(1).trim()).matches()) {
                        found.add(file.getFileName() + ":" + (i + 1) + " " + m.group(1).trim());
                    }
                }
            }
        }
    }
}
