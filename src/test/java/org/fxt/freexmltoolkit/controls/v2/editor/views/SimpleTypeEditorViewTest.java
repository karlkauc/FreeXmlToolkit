package org.fxt.freexmltoolkit.controls.v2.editor.views;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.schema.TypePickerField;
import org.fxt.freexmltoolkit.controls.shell.schema.TypePickerPopup.Item;
import org.fxt.freexmltoolkit.controls.shell.schema.TypePickerPopup.Row;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdList;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdRestriction;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The SimpleType editor's base type and list item type are chosen with the type picker, which
 * offers only what a simple type may derive from: the schema's other simple types and the
 * built-in datatypes.
 */
@ExtendWith(ApplicationExtension.class)
class SimpleTypeEditorViewTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:simpleType name="Code">
                <xs:restriction base="xs:string">
                  <xs:maxLength value="3"/>
                </xs:restriction>
              </xs:simpleType>
              <xs:simpleType name="ISINType">
                <xs:restriction base="xs:string"><xs:pattern value="[A-Z]{2}[A-Z0-9]{9}[0-9]"/></xs:restriction>
              </xs:simpleType>
              <xs:simpleType name="Codes"><xs:list itemType="Code"/></xs:simpleType>
              <xs:complexType name="PersonType"><xs:sequence/></xs:complexType>
            </xs:schema>
            """;

    private XsdSchema schema;
    private XsdSimpleType code;
    private XsdSimpleType codes;
    private SimpleTypeEditorView view;
    private Stage stage;
    private final AtomicInteger changes = new AtomicInteger();

    @Start
    void start(Stage stage) throws Exception {
        this.stage = stage;
        schema = new XsdNodeFactory().fromString(XSD);
        code = simpleType("Code");
        codes = simpleType("Codes");
        view = new SimpleTypeEditorView(code, new XsdEditorContext(schema));
        view.setOnChangeCallback(changes::incrementAndGet);
        stage.setScene(new Scene(view, 900, 600));
        stage.show();
    }

    private XsdSimpleType simpleType(String name) {
        return (XsdSimpleType) schema.getChildren().stream()
                .filter(n -> n instanceof XsdSimpleType s && name.equals(s.getName()))
                .findFirst().orElseThrow();
    }

    private void selectTab(FxRobot robot, String title) throws Exception {
        // the tab header label, not the panel title of the same text
        javafx.scene.Node header = robot.lookup(".tab-label").lookup((javafx.scene.Node n) ->
                n instanceof javafx.scene.control.Label l && title.equals(l.getText())).query();
        robot.clickOn(header);
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> title.equals(tabs().getSelectionModel().getSelectedItem().getText()));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void focus(FxRobot robot, TypePickerField field, String id) throws Exception {
        robot.clickOn("#" + id + " .text-field");
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> field.getEditor().isFocused());
    }

    private TabPane tabs() {
        return (TabPane) view.lookup(".tab-pane");
    }

    private boolean tabDisabled(String title) {
        return tabs().getTabs().stream().filter(t -> title.equals(t.getText())).findFirst().orElseThrow().isDisable();
    }

    private TypePickerField picker(String id) {
        return (TypePickerField) view.lookup("#" + id);
    }

    private static List<String> names(List<Row> rows) {
        return rows.stream().filter(r -> r instanceof Item).map(r -> ((Item) r).choice().qualifiedName()).toList();
    }

    @Test
    void baseTypePickerOffersSimpleTypesOnlyAndChangesTheRestriction(FxRobot robot) throws Exception {
        selectTab(robot, "Restriction");
        TypePickerField base = picker("simple-type-base-type");
        assertEquals("xs:string", base.getValue(), "pre-filled with the restriction's base");

        robot.clickOn("#simple-type-base-type .fxt-type-picker-button");
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, base::isPopupShowing);
        List<String> offered = names(WaitForAsyncUtils.waitForAsyncFx(2000, () -> base.popup().rows()));
        assertTrue(offered.contains("ISINType"), "other simple types of the schema");
        assertTrue(offered.contains("xs:decimal"), "built-in datatypes");
        assertFalse(offered.contains("PersonType"), "complex types cannot be a base of a simple type");
        assertFalse(offered.contains("Code"), "a simple type cannot restrict itself");
        assertFalse(offered.contains("xs:anyType"), "xs:anyType is not a simple type");
        robot.push(KeyCode.ESCAPE);
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> !base.isPopupShowing());

        focus(robot, base, "simple-type-base-type");
        robot.push(KeyCode.CONTROL, KeyCode.A);
        robot.write("xs:token");
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, base::isPopupShowing);
        WaitForAsyncUtils.waitFor(2, TimeUnit.SECONDS, () -> "xs:token".equals(base.popup().detailTitleText()));
        robot.push(KeyCode.ENTER);

        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> "xs:token".equals(restriction().getBase()));
        assertEquals(1, restriction().getFacets().size(), "existing facets are kept");
        assertTrue(changes.get() >= 1, "the change callback fired");
    }

    @Test
    void listAndUnionTabsFollowTheVariety() {
        assertTrue(tabDisabled("List"), "a restriction type cannot also be a list");
        assertTrue(tabDisabled("Union"), "a restriction type cannot also be a union");
    }

    @Test
    void itemTypePickerChangesTheListItemType(FxRobot robot) throws Exception {
        robot.interact(() -> {
            view = new SimpleTypeEditorView(codes, new XsdEditorContext(schema));
            view.setOnChangeCallback(changes::incrementAndGet);
            stage.getScene().setRoot(view);
        });
        assertFalse(tabDisabled("List"), "a list type edits its item type");
        assertTrue(tabDisabled("Union"), "a list type cannot also be a union");
        selectTab(robot, "List");
        TypePickerField item = picker("simple-type-item-type");
        assertEquals("Code", item.getValue(), "pre-filled with the list's item type");

        focus(robot, item, "simple-type-item-type");
        robot.push(KeyCode.CONTROL, KeyCode.A);
        robot.write("isin");
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, item::isPopupShowing);
        WaitForAsyncUtils.waitFor(2, TimeUnit.SECONDS, () -> "ISINType".equals(item.popup().detailTitleText()));
        List<String> offered = names(item.popup().rows());
        assertFalse(offered.contains("Codes"), "a list cannot contain itself");
        robot.push(KeyCode.ENTER);

        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> "ISINType".equals(list().getItemType()));
        assertEquals("ISINType", item.getValue());
        assertTrue(changes.get() >= 1, "the change callback fired");
    }

    private XsdRestriction restriction() {
        return code.getChildren().stream().filter(c -> c instanceof XsdRestriction).map(c -> (XsdRestriction) c)
                .findFirst().orElse(null);
    }

    private XsdList list() {
        return codes.getChildren().stream().filter(c -> c instanceof XsdList).map(c -> (XsdList) c)
                .findFirst().orElse(null);
    }
}
