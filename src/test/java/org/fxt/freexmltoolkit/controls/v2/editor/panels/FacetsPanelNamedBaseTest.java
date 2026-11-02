package org.fxt.freexmltoolkit.controls.v2.editor.panels;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdFacetType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** A restriction of a named type gets the facet fields of what that type comes down to. */
@ExtendWith(ApplicationExtension.class)
class FacetsPanelNamedBaseTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:simpleType name="Text"><xs:restriction base="xs:string"/></xs:simpleType>
              <xs:simpleType name="Short"><xs:restriction base="Text"/></xs:simpleType>
              <xs:simpleType name="Codes"><xs:list itemType="xs:string"/></xs:simpleType>
              <xs:simpleType name="FewCodes"><xs:restriction base="Codes"/></xs:simpleType>
              <xs:simpleType name="AnyId"><xs:union memberTypes="xs:string xs:int"/></xs:simpleType>
              <xs:simpleType name="SomeId"><xs:restriction base="AnyId"/></xs:simpleType>
            </xs:schema>
            """;

    @Start
    void start(Stage stage) {
    }

    private static Set<XsdFacetType> offered(String type) throws Exception {
        XsdSchema schema = new XsdNodeFactory().fromString(XSD);
        XsdNode simpleType = schema.getChildren().stream().filter(child -> type.equals(child.getName()))
                .findFirst().orElseThrow();
        XsdRestriction restriction = (XsdRestriction) simpleType.getChildren().getFirst();
        return WaitForAsyncUtils.waitForAsyncFx(10_000, () -> {
            XsdEditorContext context = new XsdEditorContext(schema);
            FacetsPanel panel = new FacetsPanel(context);
            panel.setRestriction(restriction);
            return panel.offeredFacets();
        });
    }

    @Test
    void aNamedStringTypeOffersLengthFacets() throws Exception {
        assertTrue(offered("Short").containsAll(Set.of(XsdFacetType.LENGTH, XsdFacetType.MAX_LENGTH)));
    }

    @Test
    void aListOffersItsLengthFacetsAndNoRange() throws Exception {
        Set<XsdFacetType> offered = offered("FewCodes");
        assertTrue(offered.contains(XsdFacetType.MAX_LENGTH), offered.toString());
        assertFalse(offered.contains(XsdFacetType.MAX_INCLUSIVE), offered.toString());
    }

    @Test
    void aUnionKeepsItsFacetsInTheirOwnTabs() throws Exception {
        assertEquals(Set.of(), offered("SomeId"));
    }
}
