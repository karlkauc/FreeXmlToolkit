package org.fxt.freexmltoolkit.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The bundles of the interface languages: German has every English key with the same placeholders,
 * every {@code %key} of an FXML layout exists, and the language choice resolves as documented.
 */
@DisplayName("I18n bundles")
class I18nBundlesTest {

    private static final Pattern FXML_KEY = Pattern.compile("=\"%([^\"]+)\"");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d+[^}]*}");

    @AfterEach
    void english() {
        I18n.setLocale(Locale.ENGLISH);
    }

    private static Properties load(String name) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = I18nBundlesTest.class.getResourceAsStream("/i18n/" + name)) {
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    @Test
    @DisplayName("German has exactly the English keys, with the same placeholders and no empty values")
    void germanIsComplete() throws IOException {
        Properties en = load("messages.properties");
        Properties de = load("messages_de.properties");
        Set<String> missing = new TreeSet<>(en.stringPropertyNames());
        missing.removeAll(de.stringPropertyNames());
        Set<String> extra = new TreeSet<>(de.stringPropertyNames());
        extra.removeAll(en.stringPropertyNames());
        assertEquals(Set.of(), missing, "keys without German text");
        assertEquals(Set.of(), extra, "German keys that English does not have");
        List<String> wrong = new ArrayList<>();
        for (String key : en.stringPropertyNames()) {
            String english = en.getProperty(key);
            String german = de.getProperty(key);
            if (!placeholders(english).equals(placeholders(german)) || german.isBlank() != english.isBlank()) {
                wrong.add(key + ": " + english + " → " + german);
            }
        }
        assertEquals(List.of(), wrong, "placeholders must match");
    }

    private static Set<String> placeholders(String text) {
        Set<String> found = new TreeSet<>();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            found.add(m.group());
        }
        return found;
    }

    @Test
    @DisplayName("every %key of the FXML layouts is in the English bundle")
    void fxmlKeysExist() throws IOException {
        Properties en = load("messages.properties");
        List<String> unknown = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/resources/pages"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".fxml")).toList()) {
                Matcher m = FXML_KEY.matcher(Files.readString(file));
                while (m.find()) {
                    if (!en.containsKey(m.group(1))) {
                        unknown.add(file.getFileName() + ": " + m.group(1));
                    }
                }
            }
        }
        assertEquals(List.of(), unknown);
    }

    @Test
    @DisplayName("the language: English by default, German on request, unknown languages fall back to English")
    void languages() {
        I18n.setLocale(Locale.GERMAN);
        assertEquals("Einstellungen", I18n.t("Activity.settings"));
        assertEquals("Z 3, Sp 7", I18n.t("Shell.position", "3", "7"));
        I18n.setLocale(Locale.FRENCH);
        assertEquals("Settings", I18n.t("Activity.settings"), "no French bundle: English");
        I18n.setLocale(Locale.ENGLISH);
        assertEquals("Settings", I18n.t("Activity.settings"));
        assertEquals("no.such.key", I18n.t("no.such.key"));
        assertEquals("fallback", I18n.or("no.such.key", "fallback"));
        assertEquals(I18n.Language.GERMAN, I18n.Language.fromCode("de"));
        assertEquals(I18n.Language.SYSTEM, I18n.Language.fromCode("xx"));
        assertTrue(I18n.bundle().containsKey("Activity.explorer"));
    }
}
