package org.fxt.freexmltoolkit.controls.shell.schema.overview;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.controls.shell.schema.EditRuleDialogs;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.AddSchemaReferenceCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.GlobalComponentKind;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.XsdCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSerializer;
import org.fxt.freexmltoolkit.controls.v2.editor.serialization.XsdSortOrder;
import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The Schema Overview view: component rows, insert/move/sort/rename, the attribute,
 * identity-constraint, assertion and namespace tabs, undo through the shared context, and the
 * saved text.
 */
@ExtendWith(ApplicationExtension.class)
class SchemaOverviewViewTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:t="urn:t" targetNamespace="urn:t"
                       elementFormDefault="qualified">
              <xs:import namespace="urn:o" schemaLocation="o.xsd"/>
              <xs:simpleType name="Meal">
                <xs:restriction base="xs:string">
                  <xs:enumeration value="dinner"/>
                  <xs:enumeration value="lunch"/>
                </xs:restriction>
              </xs:simpleType>
              <xs:complexType name="ExpenseType">
                <xs:sequence>
                  <xs:element name="item" type="xs:string" maxOccurs="unbounded"/>
                </xs:sequence>
                <xs:attribute name="mealtype" type="t:Meal"/>
              </xs:complexType>
              <xs:element name="expense" type="t:ExpenseType">
                <xs:key name="itemKey">
                  <xs:selector xpath="t:item"/>
                  <xs:field xpath="."/>
                </xs:key>
              </xs:element>
            </xs:schema>
            """;

    private SchemaOverviewView view;
    private XsdEditorContext context;
    private final List<XsdNode> selections = new ArrayList<>();
    private final List<String> blocked = new ArrayList<>();

    @Start
    void start(Stage stage) throws Exception {
        context = new XsdEditorContext(new XsdNodeFactory().fromString(XSD));
        view = new SchemaOverviewView(new SchemaOverviewHost() {
            @Override
            public boolean execute(XsdCommand command) {
                return context.getCommandManager().executeCommand(command);
            }

            @Override
            public void selectionChanged(XsdNode node) {
                selections.add(node);
            }

            @Override
            public void openTypeEditor(String typeName) {
            }

            @Override
            public void showInGraphic(XsdNode node) {
            }

            @Override
            public void openReferencedFile(XsdSchemaReference reference) {
            }

            @Override
            public Path documentPath() {
                return null;
            }

            @Override
            public Window window() {
                return null;
            }
        });
        EditRuleDialogs.setBlockedHandler((title, check) -> blocked.add(check.reason()));
        EditRuleDialogs.setConfirmer((title, check) -> true);
        view.setContext(context);
        stage.setScene(new Scene(view, 1100, 700));
        stage.show();
    }

    @AfterEach
    void reset() {
        EditRuleDialogs.setBlockedHandler(null);
        EditRuleDialogs.setConfirmer(null);
        SchemaOverviewView.setReferenceUpdateResponder(null);
        SchemaReferenceDialog.setResponder(null);
    }

    private <T> T fx(Callable<T> action) throws Exception {
        return WaitForAsyncUtils.waitForAsyncFx(5000, action);
    }

    private XsdSchema schema() {
        return context.getSchema();
    }

    private XsdNode child(String name) {
        return schema().getChildren().stream().filter(c -> name.equals(c.getName())).findFirst().orElseThrow();
    }

    private List<String> rowKinds() throws Exception {
        return fx(() -> view.lookupAll("#overview-components").stream().findFirst()
                .map(n -> ((javafx.scene.control.TableView<?>) n).getItems().stream()
                        .map(r -> OverviewRows.kind((XsdNode) r) + ":" + OverviewRows.name((XsdNode) r)).toList())
                .orElseThrow());
    }

    private String saved() {
        return new XsdSerializer().serialize(schema(), XsdSortOrder.PRESERVE);
    }

    @Test
    void listsEveryTopLevelComponentInDocumentOrder() throws Exception {
        assertEquals(List.of("import:o.xsd", "simpleType:Meal", "complexType:ExpenseType", "element:expense"),
                rowKinds());
    }

    @Test
    void insertMoveSortAndUndo() throws Exception {
        fx(() -> {
            view.select(child("Meal"));
            return null;
        });
        XsdNode group = fx(() -> view.addComponent(GlobalComponentKind.GROUP, true));
        assertNotNull(group);
        assertEquals(List.of("import:o.xsd", "group:NewGroup", "simpleType:Meal", "complexType:ExpenseType",
                "element:expense"), rowKinds());

        fx(() -> {
            view.select(child("expense"));
            view.onMoveUp();
            return null;
        });
        assertEquals("element:expense", rowKinds().get(3));
        assertTrue(saved().indexOf("name=\"expense\"") < saved().indexOf("name=\"ExpenseType\""));

        fx(() -> {
            view.onSortTypeThenName();
            return null;
        });
        assertEquals(List.of("import:o.xsd", "complexType:ExpenseType", "element:expense", "group:NewGroup",
                "simpleType:Meal"), rowKinds());

        fx(() -> {
            context.getCommandManager().undo();
            context.getCommandManager().undo();
            context.getCommandManager().undo();
            view.refresh();
            return null;
        });
        assertEquals(List.of("import:o.xsd", "simpleType:Meal", "complexType:ExpenseType", "element:expense"),
                rowKinds());
    }

    @Test
    void renameUpdatesReferencesWhenConfirmed() throws Exception {
        SchemaOverviewView.setReferenceUpdateResponder((node, count) -> {
            assertEquals(1, count);
            return Optional.of(true);
        });
        fx(() -> {
            view.rename(child("Meal"), "MealKind");
            return null;
        });
        assertTrue(saved().contains("type=\"t:MealKind\""), saved());
        assertEquals("simpleType:MealKind", rowKinds().get(1));
    }

    @Test
    void attributeDefaultOffersTheEnumerationAndIsSaved() throws Exception {
        fx(() -> {
            view.select(child("expense"));
            return null;
        });
        AttributesPane pane = fx(() -> (AttributesPane) tab("overview-tab-attributes").getContent());
        assertFalse(fx(() -> tab("overview-tab-attributes").isDisable()));
        XsdNode mealtype = fx(() -> pane.table().getItems().getFirst());
        assertEquals("mealtype", mealtype.getName());

        assertEquals(List.of("", "dinner", "lunch"), fx(() -> pane.enumerationValues(mealtype)));
        fx(() -> {
            pane.changeValue(mealtype, "dinner", false);
            return null;
        });
        assertTrue(saved().contains("default=\"dinner\""), saved());
    }

    @Test
    void keyrefNeedsAKeyAndIsAddedToTheElement() throws Exception {
        fx(() -> {
            view.select(child("expense"));
            return null;
        });
        IdentityConstraintsPane pane = fx(() -> (IdentityConstraintsPane) tab("overview-tab-constraints").getContent());
        fx(() -> {
            pane.onAddKeyRef();
            return null;
        });
        assertTrue(blocked.isEmpty(), blocked.toString());
        assertTrue(saved().contains("<xs:keyref name=\"expenseRef\" refer=\"t:itemKey\">"), saved());
        assertEquals(2, fx(() -> pane.table().getItems().size()));
    }

    @Test
    void assertionsBelongToComplexTypes() throws Exception {
        fx(() -> {
            view.select(child("Meal"));
            return null;
        });
        assertTrue(fx(() -> tab("overview-tab-assertions").isDisable()));
        fx(() -> {
            view.select(child("ExpenseType"));
            return null;
        });
        AssertionsPane pane = fx(() -> (AssertionsPane) tab("overview-tab-assertions").getContent());
        assertFalse(fx(() -> tab("overview-tab-assertions").isDisable()));
        fx(() -> {
            pane.onAdd();
            return null;
        });
        assertTrue(saved().contains("<xs:assert test=\"true()\"/>"), saved());
    }

    @Test
    void namespacesAndDirectives() throws Exception {
        NamespacesPane pane = fx(() -> (NamespacesPane) tab("overview-tab-namespaces").getContent());
        fx(() -> {
            pane.onAdd();
            return null;
        });
        assertTrue(schema().getNamespaces().containsKey("ns1"));

        SchemaReferenceDialog.setResponder(kind -> {
            assertEquals(AddSchemaReferenceCommand.Kind.INCLUDE, kind);
            return Optional.of(new SchemaReferenceDialog.Answer(null, "common.xsd"));
        });
        fx(() -> {
            view.lookupAll(".menu-button").stream()
                    .filter(n -> "overview-directive".equals(n.getId())).findFirst()
                    .map(n -> (javafx.scene.control.MenuButton) n).orElseThrow()
                    .getItems().get(1).fire();
            return null;
        });
        assertEquals("include:common.xsd", rowKinds().get(1));
    }

    @Test
    void annotationRowsAreEditable() throws Exception {
        XsdNode annotation = fx(() -> view.addComponent(GlobalComponentKind.ANNOTATION, false));
        assertInstanceOf(XsdAnnotation.class, annotation);
        fx(() -> {
            view.changeAnnotation(annotation, "About this schema");
            return null;
        });
        assertTrue(saved().contains("<xs:documentation>About this schema</xs:documentation>"), saved());
        assertTrue(saved().lastIndexOf("About this schema") > saved().indexOf("name=\"expense\""));
    }

    private Tab tab(String id) {
        return view.detailTabs().getTabs().stream().filter(t -> id.equals(t.getId())).findFirst().orElseThrow();
    }
}
