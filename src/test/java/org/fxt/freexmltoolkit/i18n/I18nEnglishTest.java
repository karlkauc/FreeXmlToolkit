package org.fxt.freexmltoolkit.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Telemetry keys are built from dialog titles; {@link I18n#english} maps a translated title back to its
 * English text so one dialog is one key in every language (telemetry: {@code dialog.von_url_ffnen}).
 */
class I18nEnglishTest {

    @AfterEach
    void english() {
        I18n.setLocale(Locale.ENGLISH);
    }

    @Test
    void aGermanTitleMapsToItsEnglishText() {
        I18n.setLocale(Locale.GERMAN);
        assertEquals("Open from URL", I18n.english(I18n.t("EditorHost.open-from-url")));
        assertEquals("Copy View as Image", I18n.english(I18n.t("ViewExport.copy.title")));
    }

    @Test
    void textsThatAreNoBundleValueStayAsTheyAre() {
        I18n.setLocale(Locale.GERMAN);
        assertEquals("Built in code", I18n.english("Built in code"));
        assertNull(I18n.english(null));
        I18n.setLocale(Locale.ENGLISH);
        assertEquals("Open from URL", I18n.english("Open from URL"));
    }
}
