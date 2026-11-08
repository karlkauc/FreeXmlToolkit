package org.fxt.freexmltoolkit.i18n;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * The language of the user interface: English (the source texts in {@code i18n/messages.properties})
 * or German ({@code messages_de.properties}). The language is chosen once at start — from
 * {@code -Dfxt.locale}, else the setting {@value #SETTING} ({@code system}, {@code en}, {@code de}),
 * else the system language — and applies until the next start. Texts of the FXML layouts are
 * {@code %key} references into the same bundle.
 */
public final class I18n {

    /** Settings key of the user's choice. */
    public static final String SETTING = "ui.language";
    /** The base name of the bundle. */
    public static final String BUNDLE = "i18n.messages";

    /** The languages the interface is translated into. */
    public enum Language {
        SYSTEM("system", "System default"),
        ENGLISH("en", "English"),
        GERMAN("de", "Deutsch");

        private final String code;
        private final String label;

        Language(String code, String label) {
            this.code = code;
            this.label = label;
        }

        public String code() {
            return code;
        }

        /** @return the name, in its own language ("Deutsch") */
        public String label() {
            return label;
        }

        public static Language fromCode(String code) {
            for (Language language : values()) {
                if (language.code.equalsIgnoreCase(code == null ? "" : code.strip())) {
                    return language;
                }
            }
            return SYSTEM;
        }
    }

    private static final List<String> SUPPORTED = List.of("en", "de");
    private static volatile Locale locale = Locale.ENGLISH;
    private static volatile ResourceBundle bundle = load(Locale.ENGLISH);

    private I18n() {
    }

    /**
     * Chooses the interface language and makes it the JVM default (so JavaFX's own dialog buttons
     * and file choosers follow it). Called once at start.
     *
     * @param setting the user's setting ({@code system}, {@code en}, {@code de}) or {@code null}
     */
    public static synchronized void initialize(String setting) {
        String forced = System.getProperty("fxt.locale");
        String code;
        if (forced != null && !forced.isBlank()) {
            code = forced.strip();
        } else {
            Language chosen = Language.fromCode(setting);
            code = chosen == Language.SYSTEM ? Locale.getDefault().getLanguage() : chosen.code();
        }
        Locale resolved = SUPPORTED.contains(code.toLowerCase(Locale.ROOT)) ? Locale.forLanguageTag(code) : Locale.ENGLISH;
        setLocale(resolved);
        if (!Locale.getDefault().getLanguage().equals(resolved.getLanguage())) {
            // JavaFX's built-in texts (OK/Cancel, file choosers) follow the JVM default; a system
            // locale of the same language is kept with its region
            Locale.setDefault(resolved);
        }
    }

    /** Switches the bundle (tests; the application calls {@link #initialize(String)}). */
    public static synchronized void setLocale(Locale newLocale) {
        locale = newLocale;
        bundle = load(newLocale);
    }

    private static ResourceBundle load(Locale target) {
        // No fallback to the JVM default locale: English is the base bundle itself
        return ResourceBundle.getBundle(BUNDLE, target,
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
    }

    /** @return the interface language */
    public static Locale locale() {
        return locale;
    }

    /** @return the bundle, for {@code FXMLLoader.setResources} */
    public static ResourceBundle bundle() {
        return bundle;
    }

    /** @return the text of {@code key}; the key itself when it is missing (never throws) */
    public static String t(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    /** @return the text of {@code key}, or {@code fallback} when the bundle has none */
    public static String or(String key, String fallback) {
        return bundle.containsKey(key) ? bundle.getString(key) : fallback;
    }

    /**
     * @return the text of {@code key} with {@code {0}}, {@code {1}} … replaced by the arguments as text
     *         ({@code String.valueOf}); the pattern follows MessageFormat rules ({@code ''} for an apostrophe)
     */
    public static String t(String key, Object... args) {
        // Arguments as plain text: MessageFormat would group numbers ("1,234") and format dates
        Object[] texts = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            texts[i] = args[i] == null ? "null" : String.valueOf(args[i]);
        }
        return new MessageFormat(t(key), locale).format(texts);
    }
}
