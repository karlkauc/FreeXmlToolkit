package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification of the shared side-panel action rows ({@link PanelActionList},
 * {@link PanelAction}, {@link SourceRow}): rows carry a visible label and icon, fire their
 * action, honour disabled/visible bindings, and - the reason the component exists - keep
 * their geometry across hover / pressed / focused, with the legacy theme sheets loaded in
 * the same order as the application.
 */
@ExtendWith(ApplicationExtension.class)
class PanelActionListTest {

    private static final PseudoClass HOVER = PseudoClass.getPseudoClass("hover");
    private static final PseudoClass PRESSED = PseudoClass.getPseudoClass("pressed");
    private static final PseudoClass ARMED = PseudoClass.getPseudoClass("armed");
    private static final PseudoClass FOCUSED = PseudoClass.getPseudoClass("focused");
    private static final PseudoClass FOCUS_VISIBLE = PseudoClass.getPseudoClass("focus-visible");

    private final AtomicInteger fired = new AtomicInteger();
    private final SimpleBooleanProperty busy = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty reportAvailable = new SimpleBooleanProperty(true);
    private PanelActionList list;
    private SourceRow sourceRow;
    private Button legacyPrimary;
    private Button legacyTool;
    private VBox root;

    @Start
    void start(Stage stage) {
        list = new PanelActionList(
                PanelAction.of("act-one", "bi-magic", "Generate XSD from XML", fired::incrementAndGet),
                PanelAction.of("act-two", "bi-layers", "Flatten Schema…", fired::incrementAndGet)
                        .disabledWhen(busy),
                PanelAction.of("act-three", "bi-file-earmark-text", "Validation Report", fired::incrementAndGet)
                        .visibleWhen(reportAvailable)
                        .tooltip("Needs a validation run first"),
                PanelAction.of("act-primary", "bi-play-fill", "Run Validation", fired::incrementAndGet)
                        .asPrimary());
        sourceRow = new SourceRow("bi-diagram-3", new Label("schema.xsd"), fired::incrementAndGet,
                new Label("extra"));
        legacyPrimary = new Button("Run Validation");
        legacyPrimary.getStyleClass().add("fxt-primary-button");
        legacyTool = new Button("Open PDF");
        legacyTool.getStyleClass().add("fxt-tool-button");

        root = new VBox(PanelActionList.section("TOOLS", false, list), sourceRow, legacyPrimary, legacyTool);
        root.setPrefWidth(260);
        Scene scene = new Scene(root, 260, 400);
        // Same sheets and order as shell.fxml + ThemeManager, so the legacy .button rules are present.
        scene.getStylesheets().addAll(
                css("/css/design-tokens.css"), css("/css/app-theme.css"), css("/css/fxt-theme.css"),
                css("/css/light-theme.css"), css("/css/unified-shell.css"));
        stage.setScene(scene);
        stage.show();
    }

    private String css(String resource) {
        return getClass().getResource(resource).toExternalForm();
    }

    @Test
    void rowsShowLabelAndIconInOrder() {
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(List.of("Generate XSD from XML", "Flatten Schema…", "Validation Report", "Run Validation"),
                list.labels());
        Button first = list.button("act-one");
        assertNotNull(first);
        assertTrue(first.getGraphic() instanceof IconifyIcon, "row graphic must be the icon");
        assertTrue(first.getStyleClass().contains("fxt-action-row"));
        assertNull(first.getTooltip(), "no tooltip unless one was given");
        assertNotNull(list.button("act-three").getTooltip(), "explicit tooltip is kept");
        assertTrue(list.button("act-primary").getStyleClass().contains("fxt-action-row-primary"));
        assertFalse(first.getStyleClass().contains("fxt-action-row-primary"));
    }

    @Test
    void clickingARowFiresItsAction() {
        WaitForAsyncUtils.waitForFxEvents();
        int before = fired.get();
        WaitForAsyncUtils.asyncFx(() -> list.button("act-one").fire());
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(before + 1, fired.get());

        WaitForAsyncUtils.asyncFx(() -> sourceRow.changeButton().fire());
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(before + 2, fired.get(), "the source row's Change button fires the change action");
    }

    @Test
    void bindingsDisableAndHideRows() {
        WaitForAsyncUtils.waitForFxEvents();
        Button flatten = list.button("act-two");
        Button report = list.button("act-three");
        assertFalse(flatten.isDisabled());
        assertTrue(report.isVisible() && report.isManaged());

        WaitForAsyncUtils.asyncFx(() -> {
            busy.set(true);
            reportAvailable.set(false);
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(flatten.isDisabled());
        assertFalse(report.isVisible());
        assertFalse(report.isManaged(), "hidden rows must not reserve space");
    }

    @Test
    void sourceRowKeepsSharedStyleAndOrder() {
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(sourceRow.getStyleClass().contains("fxt-vp-source-row"));
        var children = sourceRow.getChildren();
        assertSame(sourceRow.changeButton(), children.get(children.size() - 1), "Change is the last node");
        assertTrue(children.get(children.size() - 2) instanceof Label extra && "extra".equals(extra.getText()),
                "extras sit directly before Change");
        assertEquals("Change", sourceRow.changeButton().getText());
    }

    @Test
    void geometryIsStableAcrossHoverPressedAndFocused() {
        WaitForAsyncUtils.waitForFxEvents();
        assertStable(list.button("act-one"), "fxt-action-row");
        assertStable(list.button("act-primary"), "fxt-action-row-primary");
        assertStable(sourceRow.changeButton(), "fxt-action-row-inline");
        assertStable(legacyPrimary, "fxt-primary-button");
        assertStable(legacyTool, "fxt-tool-button");
    }

    private void assertStable(Button button, String what) {
        Snapshot base = snapshot(button, null);
        for (PseudoClass state : List.of(HOVER, PRESSED, ARMED, FOCUSED, FOCUS_VISIBLE)) {
            Snapshot stateSnapshot = snapshot(button, state);
            assertEquals(base, stateSnapshot, what + " changes geometry on :" + state.getPseudoClassName());
        }
    }

    /** Applies CSS + layout with the pseudo-class set (or cleared) and captures the geometry. */
    private Snapshot snapshot(Button button, PseudoClass state) {
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            for (PseudoClass p : List.of(HOVER, PRESSED, ARMED, FOCUSED, FOCUS_VISIBLE)) {
                button.pseudoClassStateChanged(p, p == state);
            }
            root.applyCss();
            root.layout();
            Bounds bounds = button.getLayoutBounds();
            return new Snapshot(bounds.getWidth(), bounds.getHeight(), button.getPadding(), button.getFont(),
                    button.getBorder() == null ? Insets.EMPTY : button.getBorder().getInsets());
        });
    }

    private record Snapshot(double width, double height, Insets padding, Font font, Insets borderInsets) {
    }
}
