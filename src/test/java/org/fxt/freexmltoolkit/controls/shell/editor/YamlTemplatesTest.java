package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;

import org.fxt.freexmltoolkit.domain.XmlTemplate;
import org.fxt.freexmltoolkit.service.TemplateRepository;
import org.fxt.freexmltoolkit.service.yaml.YamlDocument;
import org.fxt.freexmltoolkit.service.yaml.YamlSchemaDeclaration;
import org.junit.jupiter.api.Test;

/** The YAML templates of File → New: offered for YAML only, rendered they are valid YAML. */
class YamlTemplatesTest {

    private static XmlTemplate template(String name) {
        return TemplateRepository.getInstance().getAllTemplates().stream()
                .filter(t -> name.equals(t.getName())).findFirst()
                .orElseThrow(() -> new AssertionError(name + " is not a built-in template"));
    }

    @Test
    void offeredForYamlAndRenderedAsValidYaml() {
        for (String name : new String[] {"YAML document with a JSON Schema", "Docker Compose file",
                "GitHub Actions workflow"}) {
            XmlTemplate t = template(name);
            assertTrue(NewFileDialog.isDocumentTemplate(t), name);
            assertTrue(NewFileDialog.matchesType(t, EditorFileType.YAML), name);
            assertFalse(NewFileDialog.matchesType(t, EditorFileType.XML), name);
            assertFalse(NewFileDialog.matchesType(t, EditorFileType.JSON), name);
            String text = TemplateRunner.render(t, Map.of("schema", "config.schema.json"));
            assertTrue(YamlDocument.parse(text).isValid(), name + ":\n" + text);
        }
    }

    @Test
    void theSchemaTemplateDeclaresItsSchema() {
        String text = TemplateRunner.render(template("YAML document with a JSON Schema"),
                Map.of("schema", "config.schema.json"));
        assertEquals(Optional.of("config.schema.json"), YamlSchemaDeclaration.find(text));
        assertTrue(TemplateRunner.render(template("YAML document with a JSON Schema"), Map.of()).startsWith("ERROR"),
                "the schema is required");
    }

    @Test
    void anEmptyYamlFileStartsADocument() {
        assertEquals("---\n", EditorFileType.YAML.defaultContent());
    }
}
