package org.fxt.freexmltoolkit.util;

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
 * Dialogs are built through {@link DialogHelper} (or a {@code Dialog} subclass that calls
 * {@link DialogHelper#prepare}), so every popup gets an owner, a type icon and the shell look.
 * Styling comes from {@code DialogTheming}; no dialog attaches stylesheets of its own.
 */
class DialogCreationGuardTest {

    private static final Pattern RAW_DIALOG = Pattern.compile(
            "new (?:javafx\\.scene\\.control\\.)?(?:(?:Alert|TextInputDialog|ChoiceDialog)\\s*[<(]|Dialog<)");
    private static final Pattern OWN_STYLESHEETS = Pattern.compile(
            "getDialogPane\\(\\)\\.getStylesheets\\(\\)|dialog-theme\\.css");
    private static final Pattern SUBCLASS = Pattern.compile("class (\\w+) extends (Dialog|Alert)\\b");

    @Test
    void dialogsAreBuiltThroughDialogHelper() throws IOException {
        List<String> offenders = scan(RAW_DIALOG);
        assertTrue(offenders.isEmpty(), "Build dialogs with DialogHelper (createAlert, createTextInputDialog, "
                + "createChoiceDialog, createDialog, createStyledAlert):\n" + String.join("\n", offenders));
    }

    @Test
    void dialogsDoNotAttachTheirOwnStylesheets() throws IOException {
        List<String> offenders = scan(OWN_STYLESHEETS);
        assertTrue(offenders.isEmpty(), "DialogTheming styles every dialog:\n" + String.join("\n", offenders));
    }

    @Test
    void dialogSubclassesArePrepared() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            String text = Files.readString(file);
            var m = SUBCLASS.matcher(text);
            while (m.find()) {
                if (!text.contains("DialogHelper.prepare(this)")) {
                    offenders.add(file + "  " + m.group(1));
                }
            }
        }
        assertTrue(offenders.isEmpty(), "Call DialogHelper.prepare(this) in the constructor:\n"
                + String.join("\n", offenders));
    }

    private static List<String> scan(Pattern pattern) throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            String name = file.getFileName().toString();
            if (name.equals("DialogHelper.java") || name.equals("DialogTheming.java")) {
                continue;
            }
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).strip();
                if (line.startsWith("*") || line.startsWith("//") || line.startsWith("/*")) {
                    continue;
                }
                if (pattern.matcher(line).find()) {
                    offenders.add(file + ":" + (i + 1) + "  " + line);
                }
            }
        }
        return offenders;
    }

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            return files.filter(f -> f.toString().endsWith(".java")).toList();
        }
    }
}
