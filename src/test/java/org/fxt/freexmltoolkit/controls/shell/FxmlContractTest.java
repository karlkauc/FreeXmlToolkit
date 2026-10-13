package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javafx.fxml.FXML;

import org.junit.jupiter.api.Test;

/**
 * Guards the contract between every FXML layout under {@code pages/} and the class that loads
 * it (found by file name, see {@link FxmlViews}): each {@code @FXML} field has its
 * {@code fx:id} on an element of a matching type, each {@code #handler} is a public
 * {@code @FXML} method (the jpackage build cannot call non-public ones), no field uses a name
 * FXMLLoader reserves, and node ids are unique within a file.
 */
class FxmlContractTest {

    private static final Path PAGES = Path.of("src/main/resources/pages");
    private static final Path SOURCES = Path.of("src/main/java");

    /** Layouts whose file is not named after their class. */
    private static final Map<String, String> CLASS_BY_FILE = Map.of(
            "shell.fxml", "org.fxt.freexmltoolkit.controls.shell.UnifiedShellView",
            "tab_unified_shell.fxml", "org.fxt.freexmltoolkit.controller.UnifiedShellController");

    private static final Pattern FX_ID = Pattern.compile("\\bfx:id=\"([^\"]+)\"");
    private static final Pattern NODE_ID = Pattern.compile("\\sid=\"([^\"]+)\"");
    private static final Pattern IMPORT = Pattern.compile("<\\?import\\s+([\\w.]+?)(\\.\\*)?\\s*\\?>");
    private static final Pattern FX_ID_ELEMENT = Pattern.compile("<([\\w.]+)\\b[^<>]*?\\bfx:id=\"([^\"]+)\"");
    /** FXMLLoader injects the layout URL and the resource bundle into fields of these names. */
    private static final Set<String> RESERVED_FIELDS = Set.of("location", "resources");
    private static final Pattern HANDLER = Pattern.compile("\\bon\\w+=\"#([^\"]+)\"");

    @Test
    void everyLayoutMatchesItsClass() throws Exception {
        List<String> problems = new ArrayList<>();
        List<Path> layouts = layouts();
        assertFalse(layouts.isEmpty(), "no FXML layouts found under " + PAGES);
        for (Path layout : layouts) {
            String fxml = Files.readString(layout);
            Class<?> type = classFor(layout);
            Set<String> fxIds = matches(FX_ID, fxml, null, layout, problems);

            Map<String, Class<?>> elementTypes = elementTypes(fxml);
            for (Field field : type.getDeclaredFields()) {
                if (!field.isAnnotationPresent(FXML.class)) {
                    continue;
                }
                String name = field.getName();
                if (RESERVED_FIELDS.contains(name)) {
                    problems.add(layout + ": @FXML field '" + name + "' uses a name reserved by FXMLLoader");
                } else if (!fxIds.contains(name)) {
                    problems.add(layout + ": @FXML field '" + name + "' has no fx:id");
                } else if (elementTypes.containsKey(name)
                        && !field.getType().isAssignableFrom(elementTypes.get(name))) {
                    problems.add(layout + ": @FXML field '" + name + "' is a " + field.getType().getSimpleName()
                            + " but its element is a " + elementTypes.get(name).getSimpleName());
                }
            }
            for (String handler : matches(HANDLER, fxml, null, layout, problems)) {
                Method method = Stream.of(type.getDeclaredMethods())
                        .filter(m -> m.getName().equals(handler))
                        .findFirst().orElse(null);
                if (method == null) {
                    problems.add(layout + ": handler #" + handler + " is missing in " + type.getSimpleName());
                } else if (!method.isAnnotationPresent(FXML.class) || !Modifier.isPublic(method.getModifiers())) {
                    problems.add(layout + ": handler #" + handler + " must be a public @FXML method");
                }
            }
            matches(NODE_ID, fxml, "duplicate id", layout, problems);
        }
        assertTrue(problems.isEmpty(), () -> String.join("\n", problems));
    }

    /** @return the class of each element carrying an {@code fx:id}, as far as its tag resolves to a class */
    private static Map<String, Class<?>> elementTypes(String fxml) {
        List<String> classImports = new ArrayList<>();
        List<String> packageImports = new ArrayList<>();
        Matcher imports = IMPORT.matcher(fxml);
        while (imports.find()) {
            (imports.group(2) == null ? classImports : packageImports).add(imports.group(1));
        }
        Map<String, Class<?>> types = new HashMap<>();
        Matcher elements = FX_ID_ELEMENT.matcher(fxml);
        while (elements.find()) {
            Class<?> element = resolve(elements.group(1), classImports, packageImports);
            if (element != null) {
                types.put(elements.group(2), element);
            }
        }
        return types;
    }

    private static Class<?> resolve(String tag, List<String> classImports, List<String> packageImports) {
        List<String> candidates = new ArrayList<>();
        candidates.add(tag);
        classImports.stream().filter(i -> i.endsWith("." + tag)).forEach(candidates::add);
        packageImports.forEach(p -> candidates.add(p + "." + tag));
        for (String candidate : candidates) {
            try {
                return Class.forName(candidate, false, FxmlContractTest.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError e) {
                // not this candidate
            }
        }
        return null; // fx:root, fx:include, property elements
    }

    private static List<Path> layouts() throws IOException {
        try (Stream<Path> files = Files.walk(PAGES)) {
            return files.filter(p -> p.toString().endsWith(".fxml")).sorted().toList();
        }
    }

    /** @return the distinct matches; a repeated value is reported when {@code duplicateMessage} is set */
    private static Set<String> matches(Pattern pattern, String fxml, String duplicateMessage,
                                       Path layout, List<String> problems) {
        Set<String> values = new HashSet<>();
        Matcher m = pattern.matcher(fxml);
        while (m.find()) {
            if (!values.add(m.group(1)) && duplicateMessage != null) {
                problems.add(layout + ": " + duplicateMessage + " '" + m.group(1) + "'");
            }
        }
        return values;
    }

    private static Class<?> classFor(Path layout) throws Exception {
        String fileName = layout.getFileName().toString();
        String className = CLASS_BY_FILE.get(fileName);
        if (className == null) {
            String javaFile = fileName.replace(".fxml", ".java");
            try (Stream<Path> files = Files.walk(SOURCES)) {
                List<Path> found = files.filter(p -> p.getFileName().toString().equals(javaFile)).toList();
                assertEquals(1, found.size(), () -> layout + " must be named after exactly one class, found " + found);
                className = SOURCES.relativize(found.get(0)).toString()
                        .replace(".java", "").replace('/', '.').replace('\\', '.');
            }
        }
        // Not initialised: the check only reads declarations and must not start the toolkit.
        return Class.forName(className, false, FxmlContractTest.class.getClassLoader());
    }
}
