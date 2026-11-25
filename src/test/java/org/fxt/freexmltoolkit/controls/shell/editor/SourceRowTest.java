package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * {@link SourceRow} as a two-line slot (UX plan 1.1/1.2): caption + controls on the head
 * line, the name on its own line, an empty text that names the next step instead of
 * "none", and a click on an empty slot that acts like "Change".
 */
@ExtendWith(ApplicationExtension.class)
class SourceRowTest {

    private final AtomicInteger changes = new AtomicInteger();
    private SourceRow row;
    private Label name;

    @Start
    void start(Stage stage) {
        name = new Label("none");
        row = new SourceRow();
        row.setIconLiteral("bi-diagram-3");
        row.setCaption("XSD");
        row.setEmptyText("No XSD bound — choose or drop a file");
        row.setName(name);
        row.setOnChangeAction(e -> changes.incrementAndGet());
        stage.setScene(new Scene(new VBox(row), 260, 120));
        stage.show();
    }

    @Test
    void anFxmlNonePlaceholderShowsTheEmptyText() {
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(row.isEmpty());
        assertEquals("No XSD bound — choose or drop a file", name.getText());
        assertTrue(name.getStyleClass().contains(SourceRow.NONE_STYLE_CLASS));
    }

    @Test
    void theNameHasItsOwnLineAndEllipsisesInTheMiddle() {
        WaitForAsyncUtils.waitForFxEvents();
        assertSame(row.head(), row.getChildren().get(0), "head line comes first");
        assertSame(name, row.getChildren().get(1), "the name is on the second line");
        assertFalse(row.head().getChildren().contains(name));
        assertEquals(OverrunStyle.CENTER_ELLIPSIS, name.getTextOverrun());
    }

    @Test
    void singleLineMovesTheNameOntoTheHeadLineAndBack() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            row.setSingleLine(true);
            return null;
        });
        assertEquals(1, row.getChildren().size(), "only the head line");
        assertTrue(row.head().getChildren().contains(name));
        assertTrue(row.head().getChildren().indexOf(name) < row.head().getChildren().indexOf(row.changeButton()),
                "the name comes before the controls");
        assertTrue(row.getStyleClass().contains(SourceRow.SINGLE_LINE_STYLE_CLASS));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            row.setSingleLine(false);
            return null;
        });
        assertSame(name, row.getChildren().get(1), "back on its own line");
        assertFalse(row.head().getChildren().contains(name));
    }

    @Test
    void showFileSetsNameAndPathTooltipAndNullRestoresTheEmptyText() {
        File file = new File(System.getProperty("java.io.tmpdir"), "a-rather-long-stylesheet-name.xsl");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            row.showFile(file);
            return null;
        });
        assertFalse(row.isEmpty());
        assertEquals(file.getName(), name.getText());
        assertNotNull(name.getTooltip());
        assertEquals(file.getAbsolutePath(), name.getTooltip().getText());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            row.showFile(null);
            return null;
        });
        assertTrue(row.isEmpty());
        assertEquals("No XSD bound — choose or drop a file", name.getText());
    }

    @Test
    void clickingAnEmptySlotActsLikeChangeButABoundNameDoesNot() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            name.fireEvent(click());
            return null;
        });
        assertEquals(1, changes.get(), "an empty slot opens the chooser");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            row.showName("schema.xsd", null);
            name.fireEvent(click());
            return null;
        });
        assertEquals(1, changes.get(), "a bound name keeps its own click behaviour");
    }

    @Test
    void anEmptyCaptionIsHidden() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            row.setCaption("");
            return null;
        });
        Label caption = (Label) row.head().lookup(".fxt-vp-source-caption");
        assertNotNull(caption);
        assertFalse(caption.isVisible());
        assertFalse(caption.isManaged());
    }

    /** Public for FXMLLoader: the controller of {@link #anOnChangeActionHandlerFromFxmlRuns}. */
    public static final class Controller {
        int calls;

        public void changed() {
            calls++;
        }
    }

    @Test
    void anOnChangeActionHandlerFromFxmlRuns() throws Exception {
        String fxml = "<?import org.fxt.freexmltoolkit.controls.shell.editor.SourceRow?>"
                + "<SourceRow xmlns:fx=\"http://javafx.com/fxml/1\" onChangeAction=\"#changed\"/>";
        Controller controller = new Controller();
        SourceRow loaded = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader();
            loader.setController(controller);
            return loader.<SourceRow>load(new java.io.ByteArrayInputStream(
                    fxml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        });
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> loaded.changeButton().fire());
        assertEquals(1, controller.calls, "the FXML handler runs on Change");
    }

    /** FXMLLoader reads "on…Change" as a property change listener: such an attribute never sets the handler. */
    @Test
    void noFxmlUsesAnOnChangeAttribute() throws Exception {
        java.nio.file.Path pages = java.nio.file.Path.of("src/main/resources/pages");
        try (var files = java.nio.file.Files.walk(pages)) {
            var offenders = files.filter(f -> f.toString().endsWith(".fxml")).filter(f -> {
                try {
                    return java.util.regex.Pattern.compile("\\sonChange=\"#").matcher(java.nio.file.Files.readString(f)).find();
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            }).toList();
            assertTrue(offenders.isEmpty(), "use onChangeAction on SourceRow: " + offenders);
        }
    }

    private static MouseEvent click() {
        return new MouseEvent(MouseEvent.MOUSE_CLICKED, 1, 1, 1, 1, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, true, false, true, null);
    }
}
