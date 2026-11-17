package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Hyperlink;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The confirmation after an export: Open · Show in Folder · OK, "don't show again" → a note with an Open link. */
@ExtendWith(ApplicationExtension.class)
class ExportResultTest {

    @TempDir
    Path dir;
    private Stage stage;
    private String suppress;
    private final List<String> desktopCalls = new CopyOnWriteArrayList<>();

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        this.stage = stage;
        stage.setScene(new Scene(new StackPane(), 600, 400));
        stage.show();
    }

    @BeforeEach
    void realDialogs() {
        suppress = System.getProperty(ExportResult.SUPPRESS_PROPERTY);
        System.clearProperty(ExportResult.SUPPRESS_PROPERTY);
        ExportResult.desktop = (action, file) -> desktopCalls.add(action + ":" + file.getName());
        ExportResult.setConfirmEnabled(true);
    }

    @AfterEach
    void restore() {
        if (suppress != null) {
            System.setProperty(ExportResult.SUPPRESS_PROPERTY, suppress);
        }
        ExportResult.desktop = null;
        ExportResult.handler = null;
        ExportResult.setConfirmEnabled(true);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            for (Window w : List.copyOf(Window.getWindows())) {
                if (w != stage && w.isShowing() && w instanceof Stage s) {
                    s.close();
                }
            }
            return null;
        });
    }

    private DialogPane openDialog() throws Exception {
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> dialogPane() != null);
        return dialogPane();
    }

    private static DialogPane dialogPane() {
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> Window.getWindows().stream()
                .filter(Window::isShowing)
                .map(w -> w.getScene() != null && w.getScene().getRoot() instanceof DialogPane p ? p : null)
                .filter(java.util.Objects::nonNull).findFirst().orElse(null));
    }

    private static void press(DialogPane pane, String text) {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            for (ButtonType type : pane.getButtonTypes()) {
                if (type.getText().equals(text)) {
                    ((Button) pane.lookupButton(type)).fire();
                    return null;
                }
            }
            throw new AssertionError("no button " + text + " in " + pane.getButtonTypes());
        });
    }

    @Test
    void theDialogOpensTheFileOrShowsItInItsFolder() throws Exception {
        File file = Files.writeString(dir.resolve("problems.xlsx"), "x").toFile();
        Platform.runLater(() -> ExportResult.saved(stage, "12 problems", file));
        DialogPane pane = openDialog();
        assertEquals("12 problems saved", pane.getHeaderText());
        assertEquals(List.of("Open", "Show in Folder", "OK"), pane.getButtonTypes().stream().map(ButtonType::getText).toList());
        assertEquals(file.getAbsolutePath(), ((javafx.scene.control.TextField) pane.lookup("#export-result-path")).getText());
        press(pane, "Open");
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> desktopCalls.contains("open:problems.xlsx"));

        Platform.runLater(() -> ExportResult.saved(stage, "12 problems", file));
        press(openDialog(), "Show in Folder");
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> desktopCalls.contains("reveal:problems.xlsx"));
    }

    @Test
    void aFolderExportOpensTheFolderAndCountsItsFiles() throws Exception {
        Platform.runLater(() -> ExportResult.savedFolder(stage, "Sample XML files", dir, 7));
        DialogPane pane = openDialog();
        assertTrue(pane.getButtonTypes().stream().anyMatch(b -> b.getText().equals("Open Folder")));
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> pane.lookupAll(".label").stream()
                .anyMatch(n -> n instanceof javafx.scene.control.Label l && "7 files".equals(l.getText()))));
        press(pane, "OK");
        assertTrue(desktopCalls.isEmpty(), "OK only closes");
    }

    @Test
    void dontShowAgainSwitchesToANoteWithAnOpenLink() throws Exception {
        File file = Files.writeString(dir.resolve("report.csv"), "x").toFile();
        Platform.runLater(() -> ExportResult.saved(stage, "Report", file));
        DialogPane pane = openDialog();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            ((CheckBox) pane.lookup("#export-result-dont-show")).setSelected(true);
            return null;
        });
        press(pane, "OK");
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> !ExportResult.confirmEnabled());

        Platform.runLater(() -> ExportResult.saved(stage, "Report", file));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> link() != null);
        assertEquals(null, dialogPane(), "no dialog any more");
        Hyperlink link = link();
        assertNotNull(link);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            link.fire();
            return null;
        });
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> desktopCalls.contains("open:report.csv"));
    }

    @Test
    void testsAndSuppressionShowNothing() throws Exception {
        List<ExportResult.Saved> seen = new CopyOnWriteArrayList<>();
        ExportResult.handler = seen::add;
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            ExportResult.saved(stage, "X", dir.resolve("x.txt"));
            return null;
        });
        assertEquals(1, seen.size());
        assertFalse(seen.getFirst().folder());
        ExportResult.handler = null;

        System.setProperty(ExportResult.SUPPRESS_PROPERTY, "true");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            ExportResult.saved(stage, "X", dir.resolve("x.txt"));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(null, dialogPane());
        System.clearProperty(ExportResult.SUPPRESS_PROPERTY);
    }

    private static Hyperlink link() {
        return WaitForAsyncUtils.waitForAsyncFx(2000, () -> Window.getWindows().stream()
                .filter(w -> w instanceof javafx.stage.Popup && w.isShowing())
                .map(w -> w.getScene().getRoot().lookup("#toast-action"))
                .filter(n -> n instanceof Hyperlink).map(n -> (Hyperlink) n).findFirst().orElse(null));
    }
}
