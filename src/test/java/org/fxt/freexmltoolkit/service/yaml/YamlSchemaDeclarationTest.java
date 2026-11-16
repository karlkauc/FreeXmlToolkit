package org.fxt.freexmltoolkit.service.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests {@link YamlSchemaDeclaration}. */
@DisplayName("YamlSchemaDeclaration")
class YamlSchemaDeclarationTest {

    @Test
    @DisplayName("modeline comment, $schema key, modeline wins")
    void find() {
        assertEquals(Optional.of("https://json.schemastore.org/github-workflow.json"), YamlSchemaDeclaration.find(
                "# yaml-language-server: $schema=https://json.schemastore.org/github-workflow.json\non: push\n"));
        assertEquals(Optional.of("./schema.json"), YamlSchemaDeclaration.find("$schema: ./schema.json\na: 1\n"));
        assertEquals(Optional.of("s.json"), YamlSchemaDeclaration.find("\"$schema\": 's.json'  # comment\n"));
        assertEquals(Optional.of("m.json"),
                YamlSchemaDeclaration.find("$schema: k.json\n#yaml-language-server: $schema=m.json\n"));
        assertTrue(YamlSchemaDeclaration.find("a:\n  $schema: nested.json\n").isEmpty());
        assertTrue(YamlSchemaDeclaration.find("a: 1\n").isEmpty());
    }

    @Test
    @DisplayName("the JSON stub the JSON Schema binding reads")
    void stub() {
        assertEquals("{\"$schema\":\"a.json\"}", YamlSchemaDeclaration.asJsonStub("$schema: a.json\n"));
        assertEquals("{}", YamlSchemaDeclaration.asJsonStub("a: 1\n"));
    }
}
