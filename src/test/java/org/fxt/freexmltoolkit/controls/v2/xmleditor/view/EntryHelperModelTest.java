package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.schema.XsdSchemaAdapter;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.EntryHelperModel.Entry;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.EntryHelperModel.Focus;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.EntryHelperModel.Kind;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.TableEditOps.Placement;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The entry helpers' lists (what the schema allows where) and their insert commands. */
class EntryHelperModelTest {

    static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="Company">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="Name" type="xs:string"/>
                    <xs:choice minOccurs="0">
                      <xs:element name="Phone" type="xs:string"/>
                      <xs:element name="Email" type="xs:string"/>
                    </xs:choice>
                    <xs:element name="Person" maxOccurs="unbounded">
                      <xs:complexType>
                        <xs:sequence>
                          <xs:element name="Role">
                            <xs:simpleType>
                              <xs:restriction base="xs:string">
                                <xs:enumeration value="Manager"/>
                                <xs:enumeration value="Developer"/>
                              </xs:restriction>
                            </xs:simpleType>
                          </xs:element>
                        </xs:sequence>
                        <xs:attribute name="id" type="xs:string" use="required"/>
                        <xs:attribute name="level" default="junior">
                          <xs:simpleType>
                            <xs:restriction base="xs:string">
                              <xs:enumeration value="junior"/>
                              <xs:enumeration value="senior"/>
                            </xs:restriction>
                          </xs:simpleType>
                        </xs:attribute>
                      </xs:complexType>
                    </xs:element>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    @TempDir
    Path dir;

    private XmlEditorContext context;
    private XsdSchemaAdapter schema;

    /** @return a schema adapter over {@link #XSD} */
    static XsdSchemaAdapter schema(Path dir) throws Exception {
        Path xsd = dir.resolve("company.xsd");
        Files.writeString(xsd, XSD);
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsd.toString());
        service.processXsd(Boolean.TRUE);
        XsdSchemaAdapter adapter = new XsdSchemaAdapter();
        adapter.setXsdDocumentationData(service.xsdDocumentationData);
        return adapter;
    }

    @BeforeEach
    void setUp() throws Exception {
        schema = schema(dir);
        context = new XmlEditorContext();
        context.loadDocumentFromString("<Company><Name>ACME</Name><Person id=\"p1\"><Role>Manager</Role></Person></Company>");
        context.setSchemaProvider(schema);
    }

    private XmlElement company() {
        return context.getDocument().getRootElement();
    }

    private static Entry entry(List<Entry> entries, String name) {
        return entries.stream().filter(e -> e.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void theAdapterListsChildElementsThroughCompositorsInSchemaOrder() {
        assertEquals(List.of("Name", "Phone", "Email", "Person"), schema.getValidChildElements("/Company[1]"));
        assertEquals(List.of("Role"), schema.getValidChildElements("/Company/Person[1]"));
        assertTrue(schema.getValidChildElements("/Company/Missing").isEmpty());
    }

    @Test
    void childrenMarkPresentRequiredAndRepeatableEntries() {
        List<Entry> children = EntryHelperModel.children(schema, Focus.of(company(), null));
        assertEquals(List.of("Name", "Phone", "Email", "Person"), children.stream().map(Entry::name).toList());
        Entry name = entry(children, "Name");
        assertTrue(name.mandatory());
        assertFalse(name.available(), "Name occurs once and is there already");
        assertTrue(entry(children, "Person").available(), "unbounded");
        assertFalse(entry(children, "Phone").mandatory(), "inside an optional choice");
        assertTrue(entry(children, "Phone").available());
    }

    @Test
    void siblingsOfAPersonAreTheCompanyContentAndInsertAtThePlacement() {
        XmlElement person = company().getChildElement("Person");
        Focus focus = Focus.of(person, null);
        List<Entry> siblings = EntryHelperModel.siblings(schema, focus);
        assertTrue(siblings.stream().allMatch(e -> e.kind() == Kind.ELEMENT));

        assertTrue(context.executeCommand(
                EntryHelperModel.insertSibling(schema, focus, entry(siblings, "Phone"), Placement.BEFORE)));
        assertEquals("Phone", company().getChildElements().get(1).getName());
        assertTrue(context.executeCommand(
                EntryHelperModel.insertSibling(schema, focus, entry(siblings, "Person"), Placement.AFTER)));
        assertEquals(2, company().getChildElements("Person").size());
        assertNull(EntryHelperModel.insertSibling(schema, focus, entry(siblings, "Name"), Placement.AFTER),
                "an unavailable entry inserts nothing");
    }

    @Test
    void attributesAreListedFirstAndAddedWithTheirDefault() {
        XmlElement person = company().getChildElement("Person");
        List<Entry> children = EntryHelperModel.children(schema, Focus.of(person, null));
        assertEquals(List.of("id", "level", "Role"), children.stream().map(Entry::name).toList());
        Entry id = entry(children, "id");
        assertEquals(Kind.ATTRIBUTE, id.kind());
        assertTrue(id.mandatory());
        assertFalse(id.available(), "already set");

        assertTrue(context.executeCommand(
                EntryHelperModel.addChild(schema, Focus.of(person, null), entry(children, "level"))));
        assertEquals("junior", person.getAttribute("level"));
    }

    @Test
    void valuesComeFromTheEnumerationOfTheElementOrAttribute() {
        XmlElement person = company().getChildElement("Person");
        XmlElement role = person.getChildElement("Role");
        List<Entry> roleValues = EntryHelperModel.values(schema, Focus.of(role, null));
        assertEquals(List.of("Manager", "Developer"), roleValues.stream().map(Entry::name).toList());
        assertFalse(entry(roleValues, "Manager").available(), "the current value");
        assertTrue(context.executeCommand(EntryHelperModel.setValue(Focus.of(role, null), entry(roleValues, "Developer"))));
        assertEquals("Developer", role.getTextContent());

        person.setAttribute("level", "junior");
        Focus attr = Focus.of(person, "level");
        List<Entry> levels = EntryHelperModel.values(schema, attr);
        assertEquals(List.of("junior", "senior"), levels.stream().map(Entry::name).toList());
        assertTrue(context.executeCommand(EntryHelperModel.setValue(attr, entry(levels, "senior"))));
        assertEquals("senior", person.getAttribute("level"));
        assertTrue(EntryHelperModel.children(schema, attr).isEmpty(), "attributes have no children");
    }

    @Test
    void withoutSchemaEverythingIsEmpty() {
        Focus focus = Focus.of(company(), null);
        assertTrue(EntryHelperModel.children(null, focus).isEmpty());
        assertTrue(EntryHelperModel.siblings(null, focus).isEmpty());
        assertTrue(EntryHelperModel.values(null, focus).isEmpty());
        assertEquals(Integer.MAX_VALUE, EntryHelperModel.maxOccurs("unbounded"));
        assertEquals(3, EntryHelperModel.maxOccurs("3"));
    }
}
