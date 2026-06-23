package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement.DocumentationInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the Data Dictionary documentation lookups in {@link XsdDocumentationHtmlService}
 * honour the user-selected language filter ({@code setIncludedLanguages}), instead of always
 * emitting every language found in the schema.
 */
@DisplayName("XsdDocumentationHtmlService - Data Dictionary language filtering")
class XsdDocumentationHtmlServiceLanguageFilterTest {

    private XsdDocumentationHtmlService service;
    private XsdDocumentationData data;

    @BeforeEach
    void setUp() {
        data = new XsdDocumentationData();

        XsdExtendedElement element = new XsdExtendedElement();
        element.setElementName("LocalizedElement");
        element.setCurrentXpath("/root/LocalizedElement");
        element.setDocumentations(Arrays.asList(
                new DocumentationInfo("de", "Deutscher Text"),
                new DocumentationInfo("en", "English text"),
                new DocumentationInfo("fr", "Texte francais"),
                new DocumentationInfo("default", "Fallback text")
        ));

        Map<String, XsdExtendedElement> map = new LinkedHashMap<>();
        map.put(element.getCurrentXpath(), element);
        data.setExtendedXsdElementMap(map);

        service = new XsdDocumentationHtmlService();
        service.setDocumentationData(data);
    }

    @Test
    @DisplayName("Only the selected language (plus 'default' fallback) is returned")
    void returnsOnlySelectedLanguage() {
        service.setIncludedLanguages(Set.of("de"));

        Map<String, String> docs = service.getChildDocumentations("/root/LocalizedElement");

        assertTrue(docs.containsKey("de"), "selected language 'de' must be present");
        assertTrue(docs.containsKey("default"), "'default' fallback must always be kept");
        assertFalse(docs.containsKey("en"), "non-selected 'en' must be filtered out");
        assertFalse(docs.containsKey("fr"), "non-selected 'fr' must be filtered out");
    }

    @Test
    @DisplayName("Filtering is case-insensitive")
    void filteringIsCaseInsensitive() {
        service.setIncludedLanguages(Set.of("EN"));

        Map<String, String> docs = service.getChildDocumentations("/root/LocalizedElement");

        assertTrue(docs.containsKey("en"), "language match must ignore case");
        assertFalse(docs.containsKey("de"));
        assertFalse(docs.containsKey("fr"));
    }

    @Test
    @DisplayName("No filter set -> all languages are returned")
    void returnsAllLanguagesWhenNoFilter() {
        // setIncludedLanguages never called -> includedLanguages is null

        Map<String, String> docs = service.getChildDocumentations("/root/LocalizedElement");

        assertEquals(Set.of("de", "en", "fr", "default"), docs.keySet());
    }

    @Test
    @DisplayName("Empty filter -> all languages are returned")
    void returnsAllLanguagesWhenEmptyFilter() {
        service.setIncludedLanguages(Set.of());

        Map<String, String> docs = service.getChildDocumentations("/root/LocalizedElement");

        assertEquals(Set.of("de", "en", "fr", "default"), docs.keySet());
    }
}
