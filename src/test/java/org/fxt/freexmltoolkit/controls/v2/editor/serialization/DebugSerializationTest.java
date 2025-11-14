package org.fxt.freexmltoolkit.controls.v2.editor.serialization;

import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;

public class DebugSerializationTest {
    static void main(String[] args) {
        XsdSchema schema = new XsdSchema();
        XsdElement element = new XsdElement("Status");

        // Add enumerations to the element
        element.addEnumeration("active");
        element.addEnumeration("inactive");
        element.addEnumeration("pending");

        schema.addChild(element);

        XsdSerializer serializer = new XsdSerializer();
        String xsd = serializer.serialize(schema);

        System.out.println("Serialized XSD:");
        System.out.println(xsd);
        System.out.println("\n\nChecking assertions:");
        System.out.println("Contains '<xs:element name=\"Status\">': " + xsd.contains("<xs:element name=\"Status\">"));
        System.out.println("Contains '<xs:simpleType>': " + xsd.contains("<xs:simpleType>"));
        System.out.println("Contains '<xs:restriction': " + xsd.contains("<xs:restriction"));
        System.out.println("Contains '<xs:enumeration value=\"active\"/>': " + xsd.contains("<xs:enumeration value=\"active\"/>"));
    }
}
