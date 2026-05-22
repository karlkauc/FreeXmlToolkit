/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.controls.icons;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Build-time guard against missing icons. Scans the source tree for icon literals
 * ({@code prefix-name}, e.g. {@code bi-save}) used in FXML ({@code iconLiteral="..."}) and Java
 * ({@code "bi-..."} string literals) and asserts every one resolves in the bundled Iconify sets.
 * <p>
 * This replaces the previous runtime failure mode (Ikonli throwing on an unresolved literal):
 * an invalid icon now fails the build here instead of crashing the app.
 */
class IconifyIconCoverageTest {

    private static final Path MAIN = Path.of("src", "main");

    // Quoted prefix-name literals (Java string literals / FXML attribute values), e.g. "bi-save".
    // Requiring surrounding quotes avoids matching the same token in prose/JavaDoc comments.
    private static final Pattern LITERAL = Pattern.compile("\"(bi-[a-z0-9]+(?:-[a-z0-9]+)*)\"");
    private static final Pattern FXML_ICON = Pattern.compile("iconLiteral\\s*=\\s*\"([^\"]+)\"");

    @Test
    void allUsedIconLiteralsResolve() throws IOException {
        Map<String, String> usages = collectLiterals();
        assertTrue(usages.size() > 50,
                "Expected to find many icon literals; found " + usages.size() + " (scan broken?)");

        IconifyIconService service = IconifyIconService.getInstance();
        Map<String, String> missing = new TreeMap<>();
        for (Map.Entry<String, String> e : usages.entrySet()) {
            if (!service.exists(e.getKey())) {
                missing.put(e.getKey(), e.getValue());
            }
        }

        if (!missing.isEmpty()) {
            StringBuilder sb = new StringBuilder("Unresolved icon literals (")
                    .append(missing.size()).append("):\n");
            missing.forEach((icon, where) -> sb.append("  ").append(icon)
                    .append("  (e.g. ").append(where).append(")\n"));
            fail(sb.toString());
        }
    }

    /** @return map of icon literal -> first file where it was seen */
    private Map<String, String> collectLiterals() throws IOException {
        Map<String, String> result = new TreeMap<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.endsWith(".fxml") || n.endsWith(".java");
                    })
                    .sorted(Comparator.naturalOrder())
                    .forEach(p -> scanFile(p, result));
        }
        return result;
    }

    private void scanFile(Path file, Map<String, String> result) {
        String content;
        try {
            content = Files.readString(file);
        } catch (IOException e) {
            return;
        }
        String name = file.getFileName().toString();
        Set<String> found = new TreeSet<>();
        if (name.endsWith(".fxml")) {
            Matcher m = FXML_ICON.matcher(content);
            while (m.find()) {
                found.add(m.group(1));
            }
        }
        // Also catch raw prefix-name literals (Java strings and any other FXML attributes).
        Matcher m = LITERAL.matcher(content);
        while (m.find()) {
            found.add(m.group(1));
        }
        for (String lit : found) {
            result.putIfAbsent(lit, name);
        }
    }
}
