package org.fxt.freexmltoolkit.controls.shell.schema;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.Scene;
import javafx.scene.control.TreeItem;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification of the {@code XmlSearchTarget} implementation of
 * {@link XsdTreeView}: find/next/previous navigation, ancestor expansion,
 * match counting and invalidation when the schema is replaced.
 */
@ExtendWith(ApplicationExtension.class)
class XsdTreeViewSearchTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="order">
                <xs:complexType><xs:sequence>
                  <xs:element name="item" type="xs:string"/>
                  <xs:element name="itemCount" type="xs:int"/>
                  <xs:element name="note" type="xs:string"/>
                </xs:sequence></xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    private XsdTreeView tree;

    @Start
    void start(Stage stage) {
        tree = new XsdTreeView();
        stage.setScene(new Scene(tree, 400, 500));
        stage.show();
    }

    private void load(String xsd) {
        boolean ok = WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.setXsdFromText(xsd));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(ok, "XSD should parse");
    }

    private String selectedName() {
        TreeItem<XsdNode> item = tree.getSelectionModel().getSelectedItem();
        return item != null && item.getValue() != null ? item.getValue().getName() : null;
    }

    @Test
    void findSelectsAndCyclesForward() {
        load(XSD);

        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.find("item", true)));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("item", selectedName());

        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.find("item", true)));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("itemCount", selectedName());

        // Two matches only — a third find() wraps around to the first.
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.find("item", true)));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("item", selectedName());
    }

    @Test
    void findBackwardWrapsToLastMatch() {
        load(XSD);

        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.find("item", false)));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("itemCount", selectedName(), "backward from fresh search wraps to the last match");
    }

    @Test
    void findExpandsCollapsedAncestors() {
        load(XSD);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            collapseAll(tree.getRoot());
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.find("note", true)));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("note", selectedName());
        TreeItem<XsdNode> item = tree.getSelectionModel().getSelectedItem();
        for (TreeItem<XsdNode> p = item.getParent(); p != null; p = p.getParent()) {
            assertTrue(p.isExpanded(), "all ancestors of the match must be expanded");
        }
    }

    @Test
    void findAllCountsAndSelectsFirst() {
        load(XSD);

        int count = WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.findAll("item"));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(2, count);
        assertEquals("item", selectedName());
    }

    @Test
    void noMatchReturnsFalseAndZero() {
        load(XSD);
        assertFalse(WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.find("zzz-nothing", true)));
        assertEquals(0, (int) WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.findAll("zzz-nothing")));
    }

    @Test
    void emptyTreeYieldsNoMatches() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.setXsdFromText("<not-a-schema"));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(0, (int) WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.findAll("item")));
        assertFalse(WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.find("item", true)));
    }

    @Test
    void schemaReplacementInvalidatesMatchList() {
        load(XSD);
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.find("item", true)));
        WaitForAsyncUtils.waitForFxEvents();

        // New schema instance without "itemCount": stale matches must not survive.
        load("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="itemization" type="xs:string"/>
                </xs:schema>
                """);

        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> tree.find("item", true)));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("itemization", selectedName());
    }

    private void collapseAll(TreeItem<XsdNode> item) {
        if (item == null) {
            return;
        }
        item.setExpanded(false);
        for (TreeItem<XsdNode> child : item.getChildren()) {
            collapseAll(child);
        }
    }
}
