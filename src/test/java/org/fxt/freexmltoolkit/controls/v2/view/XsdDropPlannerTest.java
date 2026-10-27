package org.fxt.freexmltoolkit.controls.v2.view;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.editor.commands.CopyNodeCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.MoveNodeCommand;
import org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSequence;
import org.fxt.freexmltoolkit.controls.v2.view.XsdDropPlanner.Plan;
import org.fxt.freexmltoolkit.controls.v2.view.XsdDropPlanner.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Where a node dragged in the graphic XSD view lands, and which drops the edit rules refuse.
 */
class XsdDropPlannerTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="a" type="xs:string"/>
                    <xs:element name="b" type="xs:string"/>
                    <xs:element name="c" type="xs:string"/>
                    <xs:element name="box">
                      <xs:complexType>
                        <xs:sequence>
                          <xs:element name="inner" type="xs:string"/>
                        </xs:sequence>
                        <xs:attribute name="id" type="xs:string"/>
                        <xs:attribute name="lang" type="xs:string"/>
                      </xs:complexType>
                    </xs:element>
                    <xs:element name="addr1" type="AddressType"/>
                    <xs:element name="addr2" type="AddressType"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
              <xs:complexType name="AddressType">
                <xs:sequence>
                  <xs:element name="street" type="xs:string"/>
                  <xs:element name="city" type="xs:string"/>
                </xs:sequence>
              </xs:complexType>
            </xs:schema>
            """;

    private XsdSchema schema;
    private XsdDropPlanner planner;

    @BeforeEach
    void setUp() throws Exception {
        schema = new XsdNodeFactory().fromString(XSD);
        planner = new XsdDropPlanner(schema);
    }

    @Test
    void movingDownWithinTheSameParentLandsRightAfterTheTarget() {
        XsdNode a = find("a");
        Plan plan = planner.plan(a, find("c"), Position.AFTER, false);

        assertFalse(plan.isBlocked(), plan.check().reason());
        assertSame(a.getParent(), plan.parent());
        assertEquals(2, plan.index(), "index after removing the dragged node");
        assertTrue(new MoveNodeCommand(a, plan.parent(), plan.index()).execute());
        assertEquals(List.of("b", "c", "a", "box"), names(plan.parent()).subList(0, 4));
    }

    @Test
    void movingUpBeforeATarget() {
        XsdNode c = find("c");
        Plan plan = planner.plan(c, find("a"), Position.BEFORE, false);

        assertEquals(0, plan.index());
        assertTrue(new MoveNodeCommand(c, plan.parent(), plan.index()).execute());
        assertEquals(List.of("c", "a", "b"), names(plan.parent()).subList(0, 3));
    }

    @Test
    void dropsThatChangeNothingHaveNoPlan() {
        assertNull(planner.plan(find("a"), find("b"), Position.BEFORE, false));
        assertNull(planner.plan(find("b"), find("a"), Position.AFTER, false));
        assertNull(planner.plan(find("a"), find("a"), Position.INTO, false));
    }

    @Test
    void droppingIntoAnElementWithAnInlineTypeGoesToItsCompositor() {
        Plan plan = planner.plan(find("a"), find("box"), Position.INTO, false);

        assertFalse(plan.isBlocked(), plan.check().reason());
        assertInstanceOf(XsdSequence.class, plan.parent());
        assertSame(find("inner").getParent(), plan.parent());
        assertEquals(1, plan.index(), "appended after 'inner'");
        assertTrue(planner.sharedComponents(plan).isEmpty(), "an inline type is not shared");
    }

    @Test
    void droppingIntoAnElementWithASharedNamedTypeAsksAboutTheType() {
        Plan plan = planner.plan(find("a"), find("addr1"), Position.INTO, false);

        assertFalse(plan.isBlocked(), plan.check().reason());
        assertSame(find("street").getParent(), plan.parent());
        assertEquals(List.of("type 'AddressType' (used 2 times)"), planner.sharedComponents(plan));
    }

    @Test
    void movingOutOfASharedTypeAlsoCountsAsChangingIt() {
        Plan plan = planner.plan(find("street"), find("a"), Position.BEFORE, false);

        assertFalse(plan.isBlocked(), plan.check().reason());
        assertEquals(List.of("type 'AddressType' (used 2 times)"), planner.sharedComponents(plan));
        Plan copy = planner.plan(find("street"), find("a"), Position.BEFORE, true);
        assertTrue(planner.sharedComponents(copy).isEmpty(), "a copy leaves the source type alone");
    }

    @Test
    void droppingIntoASimpleTypedElementIsBlocked() {
        Plan plan = planner.plan(find("a"), find("b"), Position.INTO, false);

        assertTrue(plan.isBlocked());
        assertTrue(plan.check().reason().contains("simple type"), plan.check().reason());
    }

    @Test
    void droppingIntoItsOwnDescendantIsBlocked() {
        assertTrue(planner.plan(find("box"), find("inner"), Position.AFTER, false).isBlocked());
    }

    @Test
    void attributesReorderAmongAttributesAndMoveIntoComplexTypes() {
        XsdNode lang = find("lang");
        Plan before = planner.plan(lang, find("id"), Position.BEFORE, false);
        assertFalse(before.isBlocked(), before.check().reason());
        assertInstanceOf(XsdComplexType.class, before.parent());
        assertEquals(1, before.index(), "after the sequence, before 'id'");

        Plan into = planner.plan(find("id"), find("addr1"), Position.INTO, false);
        assertFalse(into.isBlocked(), into.check().reason());
        assertEquals("AddressType", into.parent().getName());
    }

    @Test
    void attributesAndElementsDoNotMix() {
        assertTrue(planner.plan(find("id"), find("a"), Position.BEFORE, false).isBlocked());
        assertTrue(planner.plan(find("a"), find("id"), Position.AFTER, false).isBlocked());
    }

    @Test
    void globalDeclarationsStayGlobal() {
        assertTrue(planner.plan(find("root"), find("a"), Position.BEFORE, false).isBlocked());
        assertTrue(planner.plan(find("a"), find("root"), Position.AFTER, false).isBlocked());
    }

    @Test
    void nodesOfAnotherSchemaAreReadOnly() throws Exception {
        XsdSchema imported = new XsdNodeFactory().fromString(XSD);
        XsdNode foreign = findIn(imported, "b");

        assertTrue(planner.plan(find("a"), foreign, Position.AFTER, false).isBlocked());
        assertTrue(planner.plan(foreign, find("a"), Position.AFTER, false).isBlocked());
    }

    @Test
    void copyKeepsTheOriginalAndInsertsAtTheExactIndex() {
        XsdNode a = find("a");
        Plan plan = planner.plan(a, find("b"), Position.AFTER, true);

        assertFalse(plan.isBlocked(), plan.check().reason());
        assertEquals(2, plan.index(), "no removal correction for a copy");
        CopyNodeCommand command = new CopyNodeCommand(a, plan.parent(), plan.index());
        assertTrue(command.execute());
        assertEquals(List.of("a", "b", "a_copy", "c"), names(plan.parent()).subList(0, 4));
        assertTrue(command.undo());
        assertEquals(List.of("a", "b", "c"), names(plan.parent()).subList(0, 3));
    }

    @Test
    void copyIntoAnotherParentKeepsTheName() {
        Plan plan = planner.plan(find("a"), find("box"), Position.INTO, true);
        CopyNodeCommand command = new CopyNodeCommand(plan.node(), plan.parent(), plan.index());

        assertTrue(command.execute());
        assertEquals(List.of("inner", "a"), names(plan.parent()));
    }

    @Test
    void cursorZones() {
        assertEquals(Position.BEFORE, XsdDropPlanner.zone(10, 0, 100, true));
        assertEquals(Position.INTO, XsdDropPlanner.zone(50, 0, 100, true));
        assertEquals(Position.AFTER, XsdDropPlanner.zone(90, 0, 100, true));
        assertEquals(Position.BEFORE, XsdDropPlanner.zone(40, 0, 100, false));
        assertEquals(Position.AFTER, XsdDropPlanner.zone(60, 0, 100, false));
    }

    private XsdNode find(String name) {
        return findIn(schema, name);
    }

    private static XsdNode findIn(XsdNode node, String name) {
        if (name.equals(node.getName())) {
            return node;
        }
        for (XsdNode child : node.getChildren()) {
            XsdNode found = findIn(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static List<String> names(XsdNode parent) {
        return parent.getChildren().stream().map(XsdNode::getName).toList();
    }
}
