package org.fxt.freexmltoolkit.controls.v2.editor.panels;

import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.CommandManager;
import org.fxt.freexmltoolkit.controls.v2.editor.selection.SelectionModel;
import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.util.Set;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.testfx.util.WaitForAsyncUtils.waitForFxEvents;

/**
 * Unit tests for FacetsPanel.
 * Tests facet display, inherited facets, datatype-specific filtering, and fixed facets.
 */
@ExtendWith(ApplicationExtension.class)
class FacetsPanelTest {

    private FacetsPanel facetsPanel;
    private XsdEditorContext editorContext;
    private XsdSchema schema;
    private GridPane facetsGridPane;

    @Start
    private void start(Stage stage) {
        // Create test schema
        schema = new XsdSchema();
        schema.setTargetNamespace("http://example.com/test");

        // Create editor context
        CommandManager commandManager = new CommandManager();
        SelectionModel selectionModel = new SelectionModel();
        editorContext = new XsdEditorContext(schema, commandManager, selectionModel);
        editorContext.setEditMode(true);

        // Create panel
        facetsPanel = new FacetsPanel(editorContext);

        // Show on stage
        Scene scene = new Scene(facetsPanel, 600, 400);
        stage.setScene(scene);
        stage.show();
    }

    @BeforeEach
    void setUp() throws TimeoutException {
        waitForFxEvents();

        // Get reference to internal GridPane using reflection
        try {
            java.lang.reflect.Field gridField = FacetsPanel.class.getDeclaredField("facetsGridPane");
            gridField.setAccessible(true);
            facetsGridPane = (GridPane) gridField.get(facetsPanel);
        } catch (Exception e) {
            fail("Could not access facetsGridPane field: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Should initialize with disabled state and no facets message")
    void testInitialState() {
        assertTrue(facetsPanel.isDisabled(), "Panel should be disabled initially");

        // Should show "no facets" label
        boolean hasNoFacetsLabel = facetsGridPane.getChildren().stream()
            .filter(node -> node instanceof Label)
            .map(node -> ((Label) node).getText())
            .anyMatch(text -> text.contains("No restriction defined"));

        assertTrue(hasNoFacetsLabel, "Should show 'no facets' message initially");
    }

    @Test
    @DisplayName("Should show applicable facets for xs:string restriction")
    void testStringRestrictionFacets() throws TimeoutException {
        // Create restriction with xs:string base
        XsdRestriction restriction = new XsdRestriction();
        restriction.setBase("xs:string");

        // Set restriction
        facetsPanel.setRestriction(restriction);
        waitForFxEvents();

        // Verify panel is enabled
        assertFalse(facetsPanel.isDisabled(), "Panel should be enabled with restriction");

        // Get applicable facets for xs:string (excluding pattern, enumeration, assertion)
        Set<XsdFacetType> applicableFacets = XsdDatatypeFacets.getApplicableFacets("xs:string");
        applicableFacets.removeIf(ft ->
            ft == XsdFacetType.PATTERN ||
            ft == XsdFacetType.ENUMERATION ||
            ft == XsdFacetType.ASSERTION
        );

        // Count TextFields/ComboBoxes in grid (exclude Labels and CheckBoxes)
        long inputControlCount = facetsGridPane.getChildren().stream()
            .filter(node -> node instanceof TextField || node instanceof ComboBox)
            .count();

        assertEquals(applicableFacets.size(), inputControlCount,
            "Should create input controls for all applicable facets (length, minLength, maxLength, whiteSpace)");
    }

    @Test
    @DisplayName("Should show applicable facets for xs:integer restriction")
    void testIntegerRestrictionFacets() throws TimeoutException {
        // Create restriction with xs:integer base
        XsdRestriction restriction = new XsdRestriction();
        restriction.setBase("xs:integer");

        facetsPanel.setRestriction(restriction);
        waitForFxEvents();

        // Get applicable facets for xs:integer
        Set<XsdFacetType> applicableFacets = XsdDatatypeFacets.getApplicableFacets("xs:integer");
        applicableFacets.removeIf(ft ->
            ft == XsdFacetType.PATTERN ||
            ft == XsdFacetType.ENUMERATION ||
            ft == XsdFacetType.ASSERTION
        );

        long inputControlCount = facetsGridPane.getChildren().stream()
            .filter(node -> node instanceof TextField || node instanceof ComboBox || node instanceof Spinner)
            .count();

        assertEquals(applicableFacets.size(), inputControlCount,
            "Should show numeric facets for xs:integer (totalDigits, minInclusive, maxInclusive, etc.)");
    }

    @Test
    @DisplayName("Should populate facet controls with existing values")
    void testPopulateExistingFacetValues() throws TimeoutException {
        // Create restriction with facets
        XsdRestriction restriction = new XsdRestriction();
        restriction.setBase("xs:string");

        XsdFacet minLength = new XsdFacet(XsdFacetType.MIN_LENGTH, "5", false);
        XsdFacet maxLength = new XsdFacet(XsdFacetType.MAX_LENGTH, "100", false);
        restriction.addChild(minLength);
        restriction.addChild(maxLength);

        facetsPanel.setRestriction(restriction);
        waitForFxEvents();

        // Find minLength TextField
        TextField minLengthField = (TextField) facetsGridPane.getChildren().stream()
            .filter(node -> node instanceof TextField)
            .filter(node -> {
                int index = facetsGridPane.getChildren().indexOf(node);
                // Check if the label before this field contains "minLength"
                if (index > 0) {
                    var prevNode = facetsGridPane.getChildren().get(index - 1);
                    if (prevNode instanceof Label label) {
                        return label.getText().toLowerCase().contains("minlength");
                    }
                }
                return false;
            })
            .findFirst()
            .map(node -> (TextField) node)
            .orElse(null);

        assertNotNull(minLengthField, "Should have TextField for minLength");
        assertEquals("5", minLengthField.getText(), "Should populate minLength with value '5'");
    }

    @Test
    @DisplayName("Should create ComboBox for whiteSpace facet")
    void testWhiteSpaceComboBox() throws TimeoutException {
        XsdRestriction restriction = new XsdRestriction();
        restriction.setBase("xs:string");

        facetsPanel.setRestriction(restriction);
        waitForFxEvents();

        // Find whiteSpace ComboBox
        ComboBox<?> whiteSpaceCombo = facetsGridPane.getChildren().stream()
            .filter(node -> node instanceof ComboBox)
            .map(node -> (ComboBox<?>) node)
            .findFirst()
            .orElse(null);

        assertNotNull(whiteSpaceCombo, "Should have ComboBox for whiteSpace facet");
        assertTrue(whiteSpaceCombo.getItems().contains("preserve"), "Should have 'preserve' option");
        assertTrue(whiteSpaceCombo.getItems().contains("replace"), "Should have 'replace' option");
        assertTrue(whiteSpaceCombo.getItems().contains("collapse"), "Should have 'collapse' option");
    }

    @Test
    @DisplayName("Should handle fixed facets as read-only")
    void testFixedFacets() throws TimeoutException {
        // For xs:string, whiteSpace can be fixed
        XsdRestriction restriction = new XsdRestriction();
        restriction.setBase("xs:token"); // token has fixed whiteSpace=collapse

        facetsPanel.setRestriction(restriction);
        waitForFxEvents();

        // Check if whiteSpace control is disabled (fixed facets are read-only)
        boolean hasDisabledControl = facetsGridPane.getChildren().stream()
            .filter(node -> node instanceof ComboBox && node.isDisabled())
            .findAny()
            .isPresent();

        // Note: xs:token has whiteSpace fixed to "collapse"
        if (XsdDatatypeFacets.isFacetFixed("xs:token", XsdFacetType.WHITE_SPACE)) {
            assertTrue(hasDisabledControl, "Fixed whiteSpace facet should be disabled for xs:token");
        }
    }

    @Test
    @DisplayName("Should show inherited facets from referenced type")
    void testInheritedFacets() throws TimeoutException {
        // Create a SimpleType with restriction
        XsdSimpleType simpleType = new XsdSimpleType();
        simpleType.setName("MyStringType");
        schema.addChild(simpleType);

        XsdRestriction restriction = new XsdRestriction();
        restriction.setBase("xs:string");
        simpleType.addChild(restriction);

        XsdFacet minLength = new XsdFacet(XsdFacetType.MIN_LENGTH, "10", false);
        restriction.addChild(minLength);

        // Create element referencing this type
        XsdElement element = new XsdElement();
        element.setName("MyElement");
        element.setType("MyStringType");

        // Set element (inherited facets mode)
        facetsPanel.setElement(element);
        waitForFxEvents();

        // Panel should be enabled (to show facets) but fields should be read-only
        assertFalse(facetsPanel.isDisabled(), "Panel should be enabled to show inherited facets");

        // Check that info label is visible
        boolean infoLabelVisible = false;
        try {
            java.lang.reflect.Field infoLabelField = FacetsPanel.class.getDeclaredField("infoLabel");
            infoLabelField.setAccessible(true);
            Label infoLabel = (Label) infoLabelField.get(facetsPanel);
            infoLabelVisible = infoLabel.isVisible();
            assertTrue(infoLabel.getText().contains("inherited"), "Info label should mention inherited facets");
            assertTrue(infoLabel.getText().contains("MyStringType"), "Info label should show type name");
        } catch (Exception e) {
            fail("Could not access infoLabel: " + e.getMessage());
        }

        assertTrue(infoLabelVisible, "Info label should be visible for inherited facets");

        // All input controls should be disabled (read-only)
        boolean allDisabled = facetsGridPane.getChildren().stream()
            .filter(node -> node instanceof TextField || node instanceof ComboBox)
            .allMatch(node -> node.isDisabled());

        assertTrue(allDisabled, "All facet controls should be disabled for inherited facets");
    }

    @Test
    @DisplayName("Should refresh facets when refresh() is called")
    void testRefresh() throws TimeoutException {
        XsdRestriction restriction = new XsdRestriction();
        restriction.setBase("xs:string");

        facetsPanel.setRestriction(restriction);
        waitForFxEvents();

        // Add a facet programmatically
        XsdFacet newFacet = new XsdFacet(XsdFacetType.MIN_LENGTH, "20", false);
        restriction.addChild(newFacet);

        // Refresh
        facetsPanel.refresh();
        waitForFxEvents();

        // Verify the new value appears
        boolean hasUpdatedValue = facetsGridPane.getChildren().stream()
            .filter(node -> node instanceof TextField)
            .map(node -> (TextField) node)
            .anyMatch(tf -> "20".equals(tf.getText()));

        assertTrue(hasUpdatedValue, "Refresh should update facet values");
    }

    @Test
    @DisplayName("Should clear facets when setRestriction(null) is called")
    void testClearRestriction() throws TimeoutException {
        XsdRestriction restriction = new XsdRestriction();
        restriction.setBase("xs:string");
        facetsPanel.setRestriction(restriction);
        waitForFxEvents();

        // Clear
        facetsPanel.setRestriction(null);
        waitForFxEvents();

        assertTrue(facetsPanel.isDisabled(), "Panel should be disabled when restriction is null");

        // Should show "no restriction" message
        boolean hasNoRestrictionMessage = facetsGridPane.getChildren().stream()
            .filter(node -> node instanceof Label)
            .map(node -> ((Label) node).getText())
            .anyMatch(text -> text.contains("No restriction"));

        assertTrue(hasNoRestrictionMessage, "Should show 'no restriction' message after clearing");
    }

    @Test
    @DisplayName("Should handle null base type gracefully")
    void testNullBaseType() throws TimeoutException {
        XsdRestriction restriction = new XsdRestriction();
        restriction.setBase(null);

        facetsPanel.setRestriction(restriction);
        waitForFxEvents();

        // Should show message about missing base type
        boolean hasMessage = facetsGridPane.getChildren().stream()
            .filter(node -> node instanceof Label)
            .map(node -> ((Label) node).getText())
            .anyMatch(text -> text.contains("No base type specified"));

        assertTrue(hasMessage, "Should show message when base type is null");
    }

    @Test
    @DisplayName("Should handle empty base type gracefully")
    void testEmptyBaseType() throws TimeoutException {
        XsdRestriction restriction = new XsdRestriction();
        restriction.setBase("");

        facetsPanel.setRestriction(restriction);
        waitForFxEvents();

        boolean hasMessage = facetsGridPane.getChildren().stream()
            .filter(node -> node instanceof Label)
            .map(node -> ((Label) node).getText())
            .anyMatch(text -> text.contains("No base type specified"));

        assertTrue(hasMessage, "Should show message when base type is empty");
    }

    @Test
    @DisplayName("Should show message when datatype has no applicable facets")
    void testNoApplicableFacets() throws TimeoutException {
        XsdRestriction restriction = new XsdRestriction();
        restriction.setBase("xs:boolean"); // boolean only has pattern, no other facets

        facetsPanel.setRestriction(restriction);
        waitForFxEvents();

        // After filtering out pattern/enumeration/assertion, should show message
        boolean hasMessage = facetsGridPane.getChildren().stream()
            .filter(node -> node instanceof Label)
            .map(node -> ((Label) node).getText())
            .anyMatch(text ->
                text.contains("No applicable facets") ||
                text.contains("separate tabs") ||
                text.contains("managed in separate tabs")
            );

        assertTrue(hasMessage, "Should show message when no facets are applicable");
    }

    @Test
    @DisplayName("Should create CheckBox for 'fixed' attribute")
    void testFixedCheckBox() throws TimeoutException {
        XsdRestriction restriction = new XsdRestriction();
        restriction.setBase("xs:string");

        XsdFacet minLength = new XsdFacet(XsdFacetType.MIN_LENGTH, "5", true); // fixed=true
        restriction.addChild(minLength);

        facetsPanel.setRestriction(restriction);
        waitForFxEvents();

        // Should have CheckBoxes for 'Fixed' attribute
        long checkBoxCount = facetsGridPane.getChildren().stream()
            .filter(node -> node instanceof CheckBox)
            .count();

        assertTrue(checkBoxCount > 0, "Should have 'Fixed' checkboxes for facets");

        // Find the checkbox that is selected (for the fixed facet)
        boolean hasSelectedCheckBox = facetsGridPane.getChildren().stream()
            .filter(node -> node instanceof CheckBox)
            .map(node -> (CheckBox) node)
            .anyMatch(CheckBox::isSelected);

        assertTrue(hasSelectedCheckBox, "Should have selected checkbox for fixed facet");
    }

    @Test
    @DisplayName("Should disable panel when edit mode is off")
    void testEditModeDisabled() throws TimeoutException {
        XsdRestriction restriction = new XsdRestriction();
        restriction.setBase("xs:string");

        facetsPanel.setRestriction(restriction);
        waitForFxEvents();
        assertFalse(facetsPanel.isDisabled(), "Panel should be enabled in edit mode");

        // Disable edit mode
        editorContext.setEditMode(false);
        facetsPanel.setRestriction(restriction); // Re-set to trigger update
        waitForFxEvents();

        assertTrue(facetsPanel.isDisabled(), "Panel should be disabled when edit mode is off");
    }

    @Test
    @DisplayName("Should handle element with null type")
    void testElementWithNullType() throws TimeoutException {
        XsdElement element = new XsdElement();
        element.setName("TestElement");
        element.setType(null);

        facetsPanel.setElement(element);
        waitForFxEvents();

        assertTrue(facetsPanel.isDisabled(), "Panel should be disabled when element has no type");
    }

    @Test
    @DisplayName("Should handle element with non-existent type reference")
    void testElementWithNonExistentType() throws TimeoutException {
        XsdElement element = new XsdElement();
        element.setName("TestElement");
        element.setType("NonExistentType");

        facetsPanel.setElement(element);
        waitForFxEvents();

        assertTrue(facetsPanel.isDisabled(), "Panel should be disabled when type is not found");
    }

    @Test
    @DisplayName("Should handle decimal facets (totalDigits, fractionDigits)")
    void testDecimalFacets() throws TimeoutException {
        XsdRestriction restriction = new XsdRestriction();
        restriction.setBase("xs:decimal");

        XsdFacet totalDigits = new XsdFacet(XsdFacetType.TOTAL_DIGITS, "10", false);
        XsdFacet fractionDigits = new XsdFacet(XsdFacetType.FRACTION_DIGITS, "2", false);
        restriction.addChild(totalDigits);
        restriction.addChild(fractionDigits);

        facetsPanel.setRestriction(restriction);
        waitForFxEvents();

        // Count input controls
        long inputCount = facetsGridPane.getChildren().stream()
            .filter(node -> node instanceof TextField || node instanceof ComboBox || node instanceof Spinner)
            .count();

        assertTrue(inputCount > 0, "Should create input controls for decimal facets");

        // Verify totalDigits and fractionDigits fields exist with correct values
        long fieldsWithValues = facetsGridPane.getChildren().stream()
            .filter(node -> node instanceof TextField)
            .map(node -> (TextField) node)
            .filter(tf -> "10".equals(tf.getText()) || "2".equals(tf.getText()))
            .count();

        assertTrue(fieldsWithValues >= 2, "Should populate totalDigits and fractionDigits values");
    }
}
