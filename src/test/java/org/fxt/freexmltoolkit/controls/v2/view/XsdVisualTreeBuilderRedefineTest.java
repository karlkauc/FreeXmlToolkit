package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdRedefine;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.framework.junit5.ApplicationExtension;

/** {@code xs:redefine} and {@code xs:override} change what the diagram shows of the target file. */
@ExtendWith(ApplicationExtension.class)
class XsdVisualTreeBuilderRedefineTest {

    private static final String BASE = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"%s>
              <xs:element name="fromBase" type="%sAddress"/>
              <xs:complexType name="Address">
                <xs:sequence><xs:element name="street" type="xs:string"/></xs:sequence>
                <xs:attribute name="id" type="xs:string"/>
              </xs:complexType>
              <xs:complexType name="Untouched">
                <xs:sequence><xs:element name="kept" type="xs:string"/></xs:sequence>
              </xs:complexType>
              <xs:group name="Parts"><xs:sequence><xs:element name="part" type="xs:string"/></xs:sequence></xs:group>
              <xs:attributeGroup name="Audit"><xs:attribute name="created" type="xs:string"/></xs:attributeGroup>
            </xs:schema>
            """;

    private static final String NAMESPACED = " targetNamespace=\"urn:t\" xmlns:t=\"urn:t\"";

    @TempDir
    Path dir;

    private XsdSchema load(String body) throws Exception {
        Path main = dir.resolve("main.xsd");
        String xsd = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:t" xmlns:t="urn:t">
                """ + body + "</xs:schema>";
        Files.writeString(main, xsd);
        return new XsdNodeFactory().fromStringWithSchemaFile(xsd, main, dir);
    }

    private void writeBase(boolean chameleon) throws Exception {
        Files.writeString(dir.resolve("base.xsd"),
                chameleon ? BASE.formatted("", "") : BASE.formatted(NAMESPACED, "t:"));
    }

    private static List<String> labels(XsdSchema schema, int budget) {
        VisualNode root = new XsdVisualTreeBuilder().withNodeBudget(budget)
                .buildFromSchema(schema, null, schema.getImportedSchemas());
        List<String> labels = new ArrayList<>();
        Deque<VisualNode> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            VisualNode node = pending.pop();
            node.setExpanded(true);
            labels.add(node.getLabel());
            node.getChildren().forEach(pending::push);
        }
        return labels;
    }

    /** Writes {@code name} as a part of namespace {@code urn:t} with {@code body} as its content. */
    private void writePart(String name, String body) throws Exception {
        Files.writeString(dir.resolve(name), """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:t" xmlns:t="urn:t">
                """ + body + "</xs:schema>");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2000})
    void aRedefineInsideAnIncludedFileTakesEffect(int budget) throws Exception {
        writeBase(false);
        writePart("part.xsd", EXTENDED);

        List<String> shown = labels(load("<xs:include schemaLocation=\"part.xsd\"/>"), budget);

        assertTrue(shown.contains("country"), shown.toString());
        assertTrue(shown.contains("street"), "the original is still the base: " + shown);
    }

    @Test
    void aRedefineInANestedIncludeResolvesAgainstItsOwnFolder() throws Exception {
        Files.createDirectory(dir.resolve("sub"));
        Files.writeString(dir.resolve("sub/base.xsd"), BASE.formatted(NAMESPACED, "t:"));
        writePart("sub/part.xsd", EXTENDED);
        writePart("outer.xsd", "<xs:include schemaLocation=\"sub/part.xsd\"/>");

        XsdSchema schema = load("<xs:include schemaLocation=\"outer.xsd\"/>");

        assertTrue(labels(schema, 2000).contains("country"));
        assertTrue(schema.getDependencyFiles().stream().anyMatch(file -> file.endsWith(Path.of("sub", "base.xsd"))),
                schema.getDependencyFiles().toString());
    }

    @Test
    void anOverrideInsideAnIncludedFileTakesEffect() throws Exception {
        writeBase(false);
        writePart("part.xsd", """
                <xs:override schemaLocation="base.xsd">
                  <xs:complexType name="Address">
                    <xs:sequence><xs:element name="replaced" type="xs:string"/></xs:sequence>
                  </xs:complexType>
                </xs:override>
                <xs:element name="home" type="t:Address"/>
                """);

        List<String> shown = labels(load("<xs:include schemaLocation=\"part.xsd\"/>"), 2000);

        assertTrue(shown.contains("replaced"), shown.toString());
        assertFalse(shown.contains("street"), shown.toString());
    }

    @Test
    void aMissingTargetInsideAnIncludedFileIsReported() throws Exception {
        writePart("part.xsd", "<xs:redefine schemaLocation=\"gone.xsd\"/>");

        XsdSchema schema = load("<xs:include schemaLocation=\"part.xsd\"/>");

        assertTrue(UnresolvedSchemaReferences.of(schema).stream()
                .anyMatch(entry -> entry.toString().contains("gone.xsd")),
                UnresolvedSchemaReferences.of(schema).toString());
    }

    @Test
    void aCycleThroughAnIncludedRedefineTerminates() throws Exception {
        writePart("part.xsd", "<xs:redefine schemaLocation=\"main.xsd\"/>");

        XsdSchema schema = load("<xs:include schemaLocation=\"part.xsd\"/><xs:element name=\"home\"/>");

        assertTrue(labels(schema, 2000).contains("home"));
    }

    @Test
    void savingLeavesARedefineFromAnIncludedFileWhereItIs() throws Exception {
        writeBase(false);
        writePart("part.xsd", EXTENDED);
        XsdSchema schema = load("<xs:include schemaLocation=\"part.xsd\"/><xs:element name=\"own\"/>");
        assertTrue(schema.getChildren().stream().anyMatch(XsdRedefine.class::isInstance));

        XsdSerializer serializer = new XsdSerializer();
        serializer.setExcludeIncludedNodes(true); // as the editor saves
        String saved = serializer.serialize(schema);

        assertFalse(saved.contains("redefine"), saved);
        assertFalse(saved.contains("country"), saved);
        assertTrue(saved.contains("part.xsd"), saved);
        assertTrue(saved.contains("own"), saved);
    }

    private static final String EXTENDED = """
            <xs:redefine schemaLocation="base.xsd">
              <xs:complexType name="Address"><xs:complexContent><xs:extension base="t:Address">
                <xs:sequence><xs:element name="country" type="xs:string"/></xs:sequence>
              </xs:extension></xs:complexContent></xs:complexType>
            </xs:redefine>
            <xs:element name="home" type="t:Address"/>
            <xs:element name="other" type="t:Untouched"/>
            """;

    @ParameterizedTest
    @ValueSource(ints = {1, 2000})
    void aRedefinedTypeBuildsOnItsOriginal(int budget) throws Exception {
        writeBase(false);

        List<String> labels = labels(load(EXTENDED), budget);

        assertTrue(labels.containsAll(List.of("street", "country", "id")), labels.toString());
        assertFalse(labels.stream().anyMatch(label -> label.startsWith("Unresolved")), labels.toString());
    }

    @Test
    void aTypeTheRedefineLeavesAloneResolvesFromTheTarget() throws Exception {
        writeBase(false);

        assertTrue(labels(load(EXTENDED), 2000).contains("kept"));
    }

    @Test
    void globalElementsOfTheTargetAreShownAndSeeTheRedefinition() throws Exception {
        writeBase(false);

        List<String> labels = labels(load(EXTENDED), 2000);

        assertTrue(labels.contains("fromBase"), labels.toString());
        assertEquals(2, labels.stream().filter("country"::equals).count(), "home and fromBase: " + labels);
    }

    @Test
    void aChameleonTargetTakesTheNamespaceOfTheRedefiningSchema() throws Exception {
        writeBase(true);

        List<String> labels = labels(load(EXTENDED), 2000);

        assertTrue(labels.containsAll(List.of("street", "country", "kept")), labels.toString());
        assertFalse(labels.stream().anyMatch(label -> label.startsWith("Unresolved")), labels.toString());
    }

    @Test
    void groupsBuildOnTheirOriginalsToo() throws Exception {
        writeBase(false);

        List<String> labels = labels(load("""
                <xs:redefine schemaLocation="base.xsd">
                  <xs:group name="Parts"><xs:sequence>
                    <xs:group ref="t:Parts"/>
                    <xs:element name="extra" type="xs:string"/>
                  </xs:sequence></xs:group>
                  <xs:attributeGroup name="Audit">
                    <xs:attributeGroup ref="t:Audit"/>
                    <xs:attribute name="changed" type="xs:string"/>
                  </xs:attributeGroup>
                </xs:redefine>
                <xs:element name="home"><xs:complexType>
                  <xs:group ref="t:Parts"/>
                  <xs:attributeGroup ref="t:Audit"/>
                </xs:complexType></xs:element>
                """), 2000);

        assertTrue(labels.containsAll(List.of("part", "extra", "created", "changed")), labels.toString());
    }

    @Test
    void overrideReplacesTheOriginal() throws Exception {
        writeBase(false);

        List<String> labels = labels(load("""
                <xs:override schemaLocation="base.xsd">
                  <xs:complexType name="Address">
                    <xs:sequence><xs:element name="replacement" type="xs:string"/></xs:sequence>
                  </xs:complexType>
                </xs:override>
                <xs:element name="home" type="t:Address"/>
                """), 2000);

        assertTrue(labels.contains("replacement"), labels.toString());
        assertFalse(labels.contains("street"), labels.toString());
    }

    @Test
    void aMissingTargetIsReported() throws Exception {
        XsdSchema schema = load("""
                <xs:redefine schemaLocation="gone.xsd"/>
                <xs:element name="home" type="t:Address"/>
                """);

        assertEquals(List.of("gone.xsd"),
                UnresolvedSchemaReferences.of(schema).stream().map(UnresolvedSchemaReferences.Entry::location).toList());
        assertTrue(labels(schema, 2000).contains("Unresolved type: t:Address"));
    }

    @Test
    void aCycleOverTwoFilesTerminates() throws Exception {
        Files.writeString(dir.resolve("base.xsd"), """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:t">
                  <xs:redefine schemaLocation="main.xsd"/>
                  <xs:complexType name="Address"/>
                </xs:schema>
                """);

        assertNotNull(labels(load(EXTENDED), 2000));
    }

    @Test
    void savingNeitherPullsInTheTargetNorLosesTheRedefine() throws Exception {
        writeBase(false);

        String saved = new XsdSerializer().serialize(load(EXTENDED));

        assertTrue(saved.contains("redefine"), saved);
        assertTrue(saved.contains("country"), saved);
        assertFalse(saved.contains("street"), saved);
    }

    @Test
    void aCopyOfTheSchemaKeepsTheTarget() throws Exception {
        writeBase(false);

        XsdNode copy = load(EXTENDED).deepCopy(null);
        XsdRedefine redefine = copy.getChildren().stream()
                .filter(XsdRedefine.class::isInstance).map(XsdRedefine.class::cast).findFirst().orElseThrow();

        assertNotNull(redefine.getReferencedSchema());
    }
}
