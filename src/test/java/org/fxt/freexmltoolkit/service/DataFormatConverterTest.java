package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fxt.freexmltoolkit.service.DataFormatConverter.ConversionException;
import org.fxt.freexmltoolkit.service.DataFormatConverter.Format;
import org.fxt.freexmltoolkit.service.DataFormatConverter.Mapping;
import org.fxt.freexmltoolkit.service.DataFormatConverter.Options;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

/** Tests {@link DataFormatConverter}: structured and W3C mappings, YAML, errors. */
@DisplayName("DataFormatConverter")
class DataFormatConverterTest {

    private static final Options STRUCTURED = Options.DEFAULTS;
    private static final Options W3C = Options.DEFAULTS.withMapping(Mapping.W3C);

    private static void assertJson(String expected, String actual) {
        assertEquals(JsonParser.parseString(expected), JsonParser.parseString(actual), actual);
    }

    @Test
    @DisplayName("XML → JSON: attributes, text, repeated elements, namespaces")
    void xmlToJsonStructured() throws Exception {
        String xml = """
                <?xml version="1.0"?>
                <!-- dropped -->
                <order id="7" xmlns:x="urn:x">
                  <item sku="A">Apple</item>
                  <item sku="B">Pear</item>
                  <note>  keep  spaces </note>
                  <x:empty/>
                  <mixed a="1">text <b>bold</b></mixed>
                  <amount>007</amount>
                </order>
                """;
        String json = DataFormatConverter.xmlToJson(xml, STRUCTURED);
        assertJson("""
                {"order": {"@xmlns:x": "urn:x", "@id": "7",
                  "item": [{"@sku": "A", "#text": "Apple"}, {"@sku": "B", "#text": "Pear"}],
                  "note": "  keep  spaces ",
                  "x:empty": "",
                  "mixed": {"@a": "1", "#text": "text", "b": "bold"},
                  "amount": "007"}}
                """, json);
        assertTrue(json.startsWith("{\n  \"order\""), "indented with 2 spaces: " + json);
    }

    @Test
    @DisplayName("typed values turn JSON numbers and booleans into numbers and booleans")
    void typedValues() throws Exception {
        String json = DataFormatConverter.xmlToJson(
                "<r n=\"1.5\"><a>42</a><b>true</b><c>007</c><d>1e3</d><e>-0</e></r>",
                STRUCTURED.withTypedValues(true).withIndent(0));
        assertEquals("{\"r\":{\"@n\":1.5,\"a\":42,\"b\":true,\"c\":\"007\",\"d\":1e3,\"e\":-0}}", json);
    }

    @Test
    @DisplayName("JSON → XML: single root property, attributes, arrays, invalid names")
    void jsonToXmlStructured() throws Exception {
        String xml = DataFormatConverter.jsonToXml("""
                {"order": {"@id": 7, "item": [{"@sku": "A", "#text": "Apple"}, "Pear"],
                  "first name": "Ada & <Bob>", "1st": true, "empty": null, "matrix": [[1, 2], []]}}
                """, STRUCTURED);
        assertEquals("""
                <?xml version="1.0" encoding="UTF-8"?>
                <order id="7">
                  <item sku="A">Apple</item>
                  <item>Pear</item>
                  <first_name>Ada &amp; &lt;Bob&gt;</first_name>
                  <_1st>true</_1st>
                  <empty/>
                  <matrix>
                    <item>1</item>
                    <item>2</item>
                  </matrix>
                  <matrix/>
                </order>
                """, xml);
    }

    @Test
    @DisplayName("JSON → XML: other top levels get the root element; JSON5 input")
    void jsonToXmlRoot() throws Exception {
        assertEquals("""
                <?xml version="1.0" encoding="UTF-8"?>
                <data>
                  <a>1</a>
                  <b>x</b>
                </data>
                """, DataFormatConverter.jsonToXml("{a: 1, b: 'x', /* JSON5 */}", STRUCTURED.withRootName("data")));
        assertEquals("""
                <?xml version="1.0" encoding="UTF-8"?>
                <json>
                  <item>1</item>
                  <item>2</item>
                </json>
                """, DataFormatConverter.jsonToXml("[1, 2]", STRUCTURED));
        assertThrows(ConversionException.class,
                () -> DataFormatConverter.jsonToXml("[1]", STRUCTURED.withRootName("1bad")));
    }

    @Test
    @DisplayName("namespaces survive XML → JSON → XML; undeclared prefixes become plain names")
    void namespaceRoundTrip() throws Exception {
        String xml = "<p:a xmlns:p=\"urn:p\" xmlns=\"urn:d\"><p:b x=\"1\">t</p:b><c/></p:a>";
        String back = DataFormatConverter.jsonToXml(DataFormatConverter.xmlToJson(xml, STRUCTURED), STRUCTURED);
        assertEquals("""
                <?xml version="1.0" encoding="UTF-8"?>
                <p:a xmlns:p="urn:p" xmlns="urn:d">
                  <p:b x="1">t</p:b>
                  <c/>
                </p:a>
                """, back);
        assertTrue(DataFormatConverter.jsonToXml("{\"q:x\": 1}", STRUCTURED).contains("<q_x>1</q_x>"));
    }

    @Test
    @DisplayName("W3C mapping: json-to-xml and xml-to-json round trip")
    void w3c() throws Exception {
        String xml = DataFormatConverter.jsonToXml("{\"a\": [1, \"two\", null, true], \"b c\": {}}", W3C);
        assertTrue(xml.contains("<map xmlns=\"" + DataFormatConverter.W3C_NAMESPACE + "\">"), xml);
        assertTrue(xml.contains("<array key=\"a\">"), xml);
        assertTrue(xml.contains("<map key=\"b c\"/>"), xml);
        assertJson("{\"a\": [1, \"two\", null, true], \"b c\": {}}", DataFormatConverter.xmlToJson(xml, W3C));
        ConversionException e = assertThrows(ConversionException.class,
                () -> DataFormatConverter.xmlToJson("<order/>", W3C));
        assertTrue(e.getMessage().contains("W3C"), e.getMessage());
    }

    @Test
    @DisplayName("YAML → JSON and XML; several documents become an array")
    void yaml() throws Exception {
        String yaml = """
                # comment
                name: Ada
                age: 36
                ratio: 0.5
                active: yes
                tags: [a, b]
                base: &base {x: 1}
                copy: *base
                none: ~
                """;
        assertJson("""
                {"name": "Ada", "age": 36, "ratio": 0.5, "active": "yes", "tags": ["a", "b"],
                 "base": {"x": 1}, "copy": {"x": 1}, "none": null}
                """, DataFormatConverter.convert(yaml, Format.YAML, Format.JSON, STRUCTURED));
        assertTrue(DataFormatConverter.convert(yaml, Format.YAML, Format.XML, STRUCTURED)
                .contains("<tags>a</tags>"));
        assertJson("[{\"a\": 1}, {\"b\": 2}]",
                DataFormatConverter.convert("a: 1\n---\nb: 2\n", Format.YAML, Format.JSON, STRUCTURED));
    }

    @Test
    @DisplayName("YAML output: block style, types kept, ambiguous strings quoted, round trip")
    void writeYaml() throws Exception {
        String json = """
                {"name": "Ada", "zip": "007", "flag": "true", "none": null, "n": 12345678901234567890,
                 "pi": 3.14, "big": 1.000000000000000000001, "ok": true, "tags": ["a", "b: c"],
                 "nested": {"empty": {}, "list": []}, "text": "two\nlines"}
                """;
        String yaml = DataFormatConverter.convert(json, Format.JSON, Format.YAML, STRUCTURED);
        assertTrue(yaml.startsWith("name: Ada\n"), yaml);
        assertTrue(yaml.contains("zip: '007'"), yaml);
        assertTrue(yaml.contains("flag: 'true'"), yaml);
        assertTrue(yaml.contains("none: null"), yaml);
        assertTrue(yaml.contains("tags:\n- a\n- 'b: c'"), yaml);
        assertJson(json, DataFormatConverter.convert(yaml, Format.YAML, Format.JSON, STRUCTURED));

        String fromXml = DataFormatConverter.convert("<r a=\"1\"><i>x</i><i>y</i></r>", Format.XML, Format.YAML,
                STRUCTURED.withTypedValues(true));
        assertEquals("r:\n  '@a': 1\n  i:\n  - x\n  - y\n", fromXml);
        assertThrows(ConversionException.class, () -> DataFormatConverter.convert("<a/>", Format.XML, Format.XML, STRUCTURED));
    }

    @Test
    @DisplayName("YAML errors: duplicate keys, alias bombs, recursive aliases, bad syntax")
    void yamlErrors() {
        assertThrows(ConversionException.class, () -> DataFormatConverter.parseYaml("a: 1\na: 2\n"));
        assertThrows(ConversionException.class, () -> DataFormatConverter.parseYaml("a: [1\n"));
        assertThrows(ConversionException.class, () -> DataFormatConverter.parseYaml("&a [*a]\n"));
        StringBuilder bomb = new StringBuilder("a: &a [x, x, x, x, x, x, x, x, x]\n");
        for (char c = 'b'; c <= 'j'; c++) {
            char p = (char) (c - 1);
            bomb.append(c).append(": &").append(c).append(" [*").append(p).append(", *").append(p)
                    .append(", *").append(p).append(", *").append(p).append(", *").append(p)
                    .append(", *").append(p).append(", *").append(p).append(", *").append(p).append("]\n");
        }
        assertThrows(ConversionException.class, () -> DataFormatConverter.parseYaml(bomb.toString()));

        // Two aliases per collection stay under the alias limit, but 26 levels expand to 2^27 nodes
        StringBuilder doubling = new StringBuilder("l0: &l0 [x, x]\n");
        for (int i = 1; i <= 25; i++) {
            doubling.append("l").append(i).append(": &l").append(i).append(" [*l").append(i - 1).append(", *l")
                    .append(i - 1).append("]\n");
        }
        ConversionException e = assertThrows(ConversionException.class,
                () -> DataFormatConverter.parseYaml(doubling.toString()));
        assertTrue(e.getMessage().contains("too many values"), e.getMessage());
    }

    @Test
    @DisplayName("XML errors: not well-formed, DTD entities are not expanded from outside")
    void xmlErrors() {
        ConversionException e = assertThrows(ConversionException.class,
                () -> DataFormatConverter.xmlToJson("<a><b></a>", STRUCTURED));
        assertTrue(e.getMessage().contains("line 1"), e.getMessage());
        String xxe = "<!DOCTYPE a [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><a>&e;</a>";
        try {
            String json = DataFormatConverter.xmlToJson(xxe, STRUCTURED);
            assertTrue(!json.contains("root:"), json);
        } catch (ConversionException refused) {
            // refusing the document is fine as well
        }
    }

    @Test
    @DisplayName("JSON → XML refuses characters XML cannot hold; formats by file name")
    void refusals() {
        assertThrows(ConversionException.class, () -> DataFormatConverter.jsonToXml("{\"a\": \"\\u0001\"}", STRUCTURED));
        assertEquals(Format.YAML, Format.ofFileName("x.YML"));
        assertEquals(Format.JSON, Format.ofFileName("x.json5"));
        assertEquals(Format.XML, Format.ofFileName("x.xsd"));
    }
}
