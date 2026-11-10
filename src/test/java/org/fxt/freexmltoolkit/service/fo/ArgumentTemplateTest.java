package org.fxt.freexmltoolkit.service.fo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ArgumentTemplateTest {

    private static final Map<String, String> VALUES = Map.of(
            "fo", "/tmp/my docs/a.fo", "pdf", "/out/a b.pdf", "title", "Report", "author", "", "pdfa", "");

    @Test
    void placeholdersKeepPathsWithSpacesInOneArgument() {
        assertEquals(List.of("-fo", "/tmp/my docs/a.fo", "-pdf", "/out/a b.pdf"),
                ArgumentTemplate.expand("-fo ${fo} -pdf ${pdf}", VALUES));
    }

    @Test
    void quotesGroupWordsAndArePartOfNoArgument() {
        assertEquals(List.of("-title", "My Report", "x=/out/a b.pdf"),
                ArgumentTemplate.expand("-title \"My Report\" x=${pdf}", VALUES));
    }

    @Test
    void optionalGroupsDropWhenAPlaceholderIsEmpty() {
        assertEquals(List.of("-fo", "/tmp/my docs/a.fo", "-title", "Report"),
                ArgumentTemplate.expand("-fo ${fo} [-title ${title}] [-author ${author}] [-pdfprofile PDF/A-1b ${pdfa}]",
                        VALUES));
    }

    @Test
    void pdfaFlagAddsItsGroupButNoText() {
        Map<String, String> values = Map.of("fo", "a.fo", "pdf", "a.pdf", "pdfa", ArgumentTemplate.FLAG_ON);
        assertEquals(List.of("a.fo", "-pdfprofile", "PDF/A-1b", "a.pdf"),
                ArgumentTemplate.expand("${fo} [-pdfprofile PDF/A-1b ${pdfa}] ${pdf}", values));
    }

    @Test
    void emptyArgumentsOutsideGroupsAreDropped() {
        assertEquals(List.of("a.fo"), ArgumentTemplate.expand("${fo} ${xml} \"\"", Map.of("fo", "a.fo")));
    }

    @Test
    void syntaxErrorsAndUnknownPlaceholdersAreReported() {
        assertThrows(IllegalArgumentException.class, () -> ArgumentTemplate.expand("${fo} ${nope}", VALUES));
        assertThrows(IllegalArgumentException.class, () -> ArgumentTemplate.expand("\"${fo}", VALUES));
        assertThrows(IllegalArgumentException.class, () -> ArgumentTemplate.expand("[${fo}", VALUES));
        assertThrows(IllegalArgumentException.class, () -> ArgumentTemplate.expand("${fo}]", VALUES));
        assertThrows(IllegalArgumentException.class, () -> ArgumentTemplate.expand("[[${fo}]]", VALUES));
    }

    @Test
    void validateNeedsFoAndPdf() {
        assertNull(ArgumentTemplate.validate("-fo ${fo} -pdf ${pdf}"));
        assertNull(ArgumentTemplate.validate("${fo} ${out}"));
        assertNotNull(ArgumentTemplate.validate("-pdf ${pdf}"));
        assertNotNull(ArgumentTemplate.validate("${fo}"));
        assertTrue(ArgumentTemplate.validate("${fo} ${pdf} ${bogus}").contains("bogus"));
    }

    @Test
    void presetTemplatesAreValid() {
        for (FoProcessorPreset preset : FoProcessorPreset.values()) {
            assertNull(ArgumentTemplate.validate(preset.arguments()), preset.name());
        }
        assertTrue(FoProcessorConfig.fromPreset(FoProcessorPreset.APACHE_FOP).supportsPdfA());
        assertTrue(!FoProcessorConfig.fromPreset(FoProcessorPreset.XEP).supportsMetadata());
    }
}
