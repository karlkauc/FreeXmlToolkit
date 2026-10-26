package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import static org.junit.jupiter.api.Assertions.*;

import org.fxt.freexmltoolkit.controls.v2.editor.commands.EditCheck.Status;
import org.fxt.freexmltoolkit.controls.v2.model.XsdAttribute;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdExtension;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSequence;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The structural rules decide which edit keeps the schema valid. */
class XsdEditRulesTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="order">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="id"/>
                    <xs:element name="amount" type="xs:int"/>
                    <xs:element name="code" type="CodeType"/>
                    <xs:element name="person" type="PersonType"/>
                    <xs:element name="money" type="MoneyType"/>
                    <xs:element ref="note"/>
                    <xs:element name="anything" type="xs:anyType"/>
                    <xs:element name="unknown" type="foo:Bar"/>
                    <xs:element name="color">
                      <xs:simpleType><xs:restriction base="xs:string"/></xs:simpleType>
                    </xs:element>
                    <xs:element name="empty"/>
                  </xs:sequence>
                  <xs:attribute name="status">
                    <xs:simpleType><xs:restriction base="xs:string"/></xs:simpleType>
                  </xs:attribute>
                </xs:complexType>
              </xs:element>
              <xs:element name="note" type="xs:string"/>
              <xs:simpleType name="CodeType"><xs:restriction base="xs:string"/></xs:simpleType>
              <xs:complexType name="PersonType"><xs:sequence><xs:element name="name" type="xs:string"/></xs:sequence></xs:complexType>
              <xs:complexType name="MoneyType">
                <xs:simpleContent><xs:extension base="xs:decimal"/></xs:simpleContent>
              </xs:complexType>
            </xs:schema>
            """;

    private XsdSchema schema;

    @BeforeEach
    void setUp() throws Exception {
        schema = new XsdNodeFactory().fromString(XSD);
    }

    private XsdElement order() {
        return (XsdElement) schema.getChildren().stream()
                .filter(n -> n instanceof XsdElement e && "order".equals(e.getName())).findFirst().orElseThrow();
    }

    private XsdNode orderSequence() {
        return XsdEditRules.elementContainer(order());
    }

    private XsdElement child(String name) {
        return (XsdElement) orderSequence().getChildren().stream()
                .filter(n -> n instanceof XsdElement e && (name.equals(e.getName()) || name.equals(e.getRef())))
                .findFirst().orElseThrow();
    }

    private XsdAttribute status() {
        XsdComplexType ct = (XsdComplexType) XsdEditRules.inlineType(order());
        return (XsdAttribute) ct.getChildren().stream().filter(n -> n instanceof XsdAttribute).findFirst().orElseThrow();
    }

    @Test
    void reportedCase_typeOnElementWithInlineSequenceIsReplaceable() {
        EditCheck check = XsdEditRules.changeType(order(), "xs:int");
        assertEquals(Status.REPLACEABLE, check.status());
        assertTrue(check.reason().contains("inline complex type with a sequence of 10 elements"), check.reason());
        assertEquals("Replace with xs:int", check.replaceLabel());
        assertTrue(XsdEditRules.changeType(order(), "").isAllowed(), "removing the type is always fine");
        assertTrue(XsdEditRules.changeType(child("id"), "xs:int").isAllowed());
    }

    @Test
    void typeChangesOnReferencesAndInlineSimpleTypes() {
        assertTrue(XsdEditRules.changeType(child("note"), "xs:int").isBlocked(), "a ref takes the referenced type");
        assertEquals(Status.REPLACEABLE, XsdEditRules.changeType(child("color"), "xs:token").status());
        assertEquals(Status.REPLACEABLE, XsdEditRules.changeType(status(), "xs:token").status(),
                "an attribute with an inline simple type");
    }

    @Test
    void compositorOnTypedElementIsReplaceable() {
        assertEquals(Status.REPLACEABLE, XsdEditRules.addCompositor(child("amount"), "sequence").status());
        assertEquals("Remove type and add sequence",
                XsdEditRules.addCompositor(child("person"), "sequence").replaceLabel());
        assertTrue(XsdEditRules.addCompositor(child("note"), "sequence").isBlocked());
        assertTrue(XsdEditRules.addCompositor(child("color"), "sequence").isBlocked());
        assertTrue(XsdEditRules.addCompositor(child("empty"), "sequence").isAllowed());
        assertTrue(XsdEditRules.addCompositor(order(), "choice").isAllowed(), "replaces the compositor as before");
        assertTrue(XsdEditRules.addCompositor(XsdEditRules.findNamedType(order(), "MoneyType"), "sequence")
                .isBlocked(), "simple content cannot take a compositor");
    }

    @Test
    void childElementsNeedACompositorAndAComplexType() {
        assertTrue(XsdEditRules.addElement(order()).isAllowed());
        assertTrue(XsdEditRules.addElement(orderSequence()).isAllowed());
        assertTrue(XsdEditRules.addElement(schema).isAllowed());
        assertTrue(XsdEditRules.addElement(child("person")).isAllowed(), "goes into PersonType's sequence");
        assertInstanceOf(XsdSequence.class, XsdEditRules.elementContainer(child("person")));
        assertTrue(XsdEditRules.addElement(child("amount")).isBlocked());
        assertTrue(XsdEditRules.addElement(child("code")).isBlocked(), "named simple type");
        assertTrue(XsdEditRules.addElement(child("money")).isBlocked(), "simple content");
        assertTrue(XsdEditRules.addElement(child("note")).isBlocked());
        assertTrue(XsdEditRules.addElement(child("anything")).isBlocked());
        assertTrue(XsdEditRules.addElement(child("unknown")).isBlocked());
        assertTrue(XsdEditRules.addElement(child("color")).isBlocked());
        EditCheck empty = XsdEditRules.addElement(child("empty"));
        assertTrue(empty.isBlocked());
        assertTrue(empty.reason().contains("add one first"), empty.reason());
        assertTrue(XsdEditRules.addElement(status()).isBlocked(), "attributes have no children");
    }

    @Test
    void attributesGoIntoComplexTypes() {
        assertTrue(XsdEditRules.addAttribute(order()).isAllowed(), "attributes and a sequence coexist");
        assertTrue(XsdEditRules.addAttribute(child("empty")).isAllowed(), "an inline complex type is created");
        assertTrue(XsdEditRules.addAttribute(child("person")).isAllowed());
        assertInstanceOf(XsdComplexType.class, XsdEditRules.attributeContainer(child("person")));
        assertInstanceOf(XsdExtension.class, XsdEditRules.attributeContainer(child("money")),
                "simple content: attributes go into the extension");
        assertTrue(XsdEditRules.addAttribute(child("amount")).isBlocked());
        assertTrue(XsdEditRules.addAttribute(child("color")).isBlocked());
        assertTrue(XsdEditRules.addAttribute(child("note")).isBlocked());
        assertTrue(XsdEditRules.addAttribute(orderSequence()).isBlocked());
    }

    @Test
    void pasteAndMoveFollowThePlacedKind() {
        XsdElement element = new XsdElement("x");
        XsdAttribute attribute = new XsdAttribute("a");
        assertTrue(XsdEditRules.paste(orderSequence(), element).isAllowed());
        assertTrue(XsdEditRules.paste(child("amount"), element).isBlocked());
        assertTrue(XsdEditRules.paste(child("amount"), attribute).isBlocked());
        assertTrue(XsdEditRules.paste(order(), attribute).isAllowed());
        XsdSimpleType named = (XsdSimpleType) XsdEditRules.findNamedType(order(), "CodeType");
        assertTrue(XsdEditRules.paste(orderSequence(), named).isBlocked());
        assertTrue(XsdEditRules.paste(schema, named).isAllowed());

        assertTrue(XsdEditRules.move(child("id"), orderSequence()).isAllowed(), "reorder");
        assertTrue(XsdEditRules.move(child("id"), child("amount")).isBlocked());
        assertTrue(XsdEditRules.move(order(), orderSequence()).isBlocked(), "into itself");
    }

    @Test
    void refNeedsAnUntypedDeclaration() {
        assertTrue(XsdEditRules.changeRef(child("amount"), "note").isBlocked());
        assertTrue(XsdEditRules.changeRef(order(), "note").isBlocked());
        assertTrue(XsdEditRules.changeRef(child("id"), "note").isAllowed());
        assertTrue(XsdEditRules.changeRef(child("amount"), "").isAllowed());
    }

    @Test
    void builtinsAreRecognisedByTheirNamespacePrefix() throws Exception {
        XsdSchema xsd = new XsdNodeFactory().fromString("""
                <xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema">
                  <xsd:element name="a" type="xsd:int"/>
                  <xsd:element name="b" type="string"/>
                  <xsd:complexType name="string"><xsd:sequence/></xsd:complexType>
                </xsd:schema>
                """);
        XsdElement a = (XsdElement) xsd.getChildren().get(0);
        XsdElement b = (XsdElement) xsd.getChildren().get(1);
        assertTrue(XsdEditRules.isBuiltinRef(a, "xsd:int"));
        assertTrue(XsdEditRules.addElement(a).isBlocked());
        assertFalse(XsdEditRules.isBuiltinRef(b, "string"), "shadowed by the schema's own type");
        assertTrue(XsdEditRules.addElement(b).isAllowed(), "the schema's complex type 'string' has a sequence");
        assertNull(XsdEditRules.findNamedType(a, "xsd:string"), "the XSD namespace never resolves to a schema type");
    }

    @Test
    void newElementAndAttributeNeedConsistentProperties() {
        NewElementSpec ok = new NewElementSpec("line", "xs:int", 0, -1, null, false, false, null, null, null, null);
        assertTrue(XsdEditRules.newElement(order(), ok).isAllowed());
        assertTrue(XsdEditRules.newElement(order(),
                new NewElementSpec("line", null, 3, 2, null, false, false, null, null, null, null)).isBlocked(), "min > max");
        assertTrue(XsdEditRules.newElement(order(),
                new NewElementSpec("1line", null, 1, 1, null, false, false, null, null, null, null)).isBlocked(), "NCName");
        assertTrue(XsdEditRules.newElement(order(),
                new NewElementSpec("line", null, 1, 1, null, false, false, "a", "b", null, null)).isBlocked(),
                "default and fixed");
        assertTrue(XsdEditRules.newElement(order(),
                new NewElementSpec("line", null, 1, 1, null, false, true, null, null, null, null)).isBlocked(),
                "a local element cannot be abstract");
        assertTrue(XsdEditRules.newElement(schema,
                new NewElementSpec("top", null, 0, 1, null, false, false, null, null, null, null)).isBlocked(),
                "no cardinality on a top-level element");
        assertTrue(XsdEditRules.newElement(schema, NewElementSpec.named("note")).isBlocked(), "name taken");
        assertTrue(XsdEditRules.newElement(schema, NewElementSpec.named("top")).isAllowed());
        assertTrue(XsdEditRules.newElement(child("amount"), NewElementSpec.named("x")).isBlocked(),
                "the parent rule still applies");

        assertTrue(XsdEditRules.newAttribute(order(), NewAttributeSpec.named("code")).isAllowed());
        assertTrue(XsdEditRules.newAttribute(order(),
                new NewAttributeSpec("code", null, "required", null, "x", null, null)).isBlocked(),
                "a default needs use=optional");
        assertTrue(XsdEditRules.newAttribute(order(),
                new NewAttributeSpec("code", null, "prohibited", null, null, "x", null)).isBlocked());
        assertTrue(XsdEditRules.newAttribute(order(),
                new NewAttributeSpec("code", null, "required", null, null, "x", null)).isAllowed());
    }
}
