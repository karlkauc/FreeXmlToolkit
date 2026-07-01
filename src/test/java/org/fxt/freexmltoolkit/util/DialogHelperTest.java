package org.fxt.freexmltoolkit.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;

@DisplayName("DialogHelper Tests")
@ExtendWith(ApplicationExtension.class)
public class DialogHelperTest {

    // ApplicationExtension initializes the JavaFX (Monocle headless) toolkit before tests run.
    // A bare Platform.startup() does not register the Monocle glass platform when a test class
    // runs in its own forked JVM (forkEvery = 1), so we rely on the extension instead.

    private void runAndWait(Runnable action) {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } finally {
                latch.countDown();
            }
        });
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Action timed out");
        } catch (InterruptedException e) {
            fail("Interrupted while waiting for JavaFX action");
        }
    }

    @Test
    @DisplayName("Sollte Enums korrekt bereitstellen")
    void testEnums() {
        assertNotNull(DialogHelper.HeaderTheme.PRIMARY.getStyleClass());
        assertNotNull(DialogHelper.HeaderTheme.PRIMARY.getColor());
        assertNotNull(DialogHelper.InfoBoxType.INFO.getStyleClass());
        assertNotNull(DialogHelper.InfoBoxType.INFO.getIconLiteral());
    }

    @Test
    @DisplayName("Sollte Dialog-Header korrekt erstellen")
    void testCreateDialogHeader() {
        runAndWait(() -> {
            VBox header = DialogHelper.createDialogHeader("Title", "Subtitle", "bi-info", DialogHelper.HeaderTheme.PRIMARY);
            assertNotNull(header);
            assertTrue(header.getStyleClass().contains("dialog-header"));
            assertTrue(header.getStyleClass().contains("dialog-header-primary"));
            
            VBox textBox = (VBox) ((HBox) header.getChildren().get(0)).getChildren().get(1);
            Label titleLabel = (Label) textBox.getChildren().get(0);
            Label subtitleLabel = (Label) textBox.getChildren().get(1);
            
            assertEquals("Title", titleLabel.getText());
            assertEquals("Subtitle", subtitleLabel.getText());
        });
    }

    @Test
    @DisplayName("Sollte Sektionen korrekt erstellen")
    void testCreateSection() {
        runAndWait(() -> {
            VBox section = DialogHelper.createSection("Section Title", "Section Subtitle");
            assertNotNull(section);
            assertTrue(section.getStyleClass().contains("dialog-section"));
            
            Label titleLabel = (Label) section.getChildren().get(0);
            Label subtitleLabel = (Label) section.getChildren().get(1);
            
            assertEquals("Section Title", titleLabel.getText());
            assertEquals("Section Subtitle", subtitleLabel.getText());
        });
    }

    @Test
    @DisplayName("Sollte Info-Boxen korrekt erstellen")
    void testCreateInfoBox() {
        runAndWait(() -> {
            VBox infoBox = DialogHelper.createInfoBox(DialogHelper.InfoBoxType.SUCCESS, "Success Title", "Success Text");
            assertNotNull(infoBox);
            assertTrue(infoBox.getStyleClass().contains("dialog-info-box"));
            assertTrue(infoBox.getStyleClass().contains("dialog-info-box-success"));
            
            HBox header = (HBox) infoBox.getChildren().get(0);
            Label titleLabel = (Label) header.getChildren().get(1);
            Label textLabel = (Label) infoBox.getChildren().get(1);
            
            assertEquals("Success Title", titleLabel.getText());
            assertEquals("Success Text", textLabel.getText());
        });
    }

    @Test
    @DisplayName("Sollte Shortcut-Items korrekt erstellen")
    void testCreateShortcutItem() {
        runAndWait(() -> {
            HBox item = DialogHelper.createShortcutItem("Ctrl+S", "Save Document");
            assertNotNull(item);
            assertTrue(item.getStyleClass().contains("dialog-shortcut-item"));
            
            Label keyLabel = (Label) item.getChildren().get(0);
            Label descLabel = (Label) item.getChildren().get(1);
            
            assertEquals("Ctrl+S", keyLabel.getText());
            assertEquals("Save Document", descLabel.getText());
        });
    }

    @Test
    @DisplayName("Remedy-Texte sollten vorhanden und nicht leer sein")
    void testRemediesCatalog() {
        assertNotNull(DialogHelper.Remedies.FILE_UNREADABLE);
        assertNotNull(DialogHelper.Remedies.FILE_UNWRITABLE);
        assertNotNull(DialogHelper.Remedies.NETWORK);
        assertNotNull(DialogHelper.Remedies.URL_FETCH);
        assertNotNull(DialogHelper.Remedies.INVALID_SCHEMA);
        assertNotNull(DialogHelper.Remedies.TRANSFORM);
        assertNotNull(DialogHelper.Remedies.EXPORT);
        for (String remedy : List.of(
                DialogHelper.Remedies.FILE_UNREADABLE, DialogHelper.Remedies.FILE_UNWRITABLE,
                DialogHelper.Remedies.NETWORK, DialogHelper.Remedies.URL_FETCH,
                DialogHelper.Remedies.INVALID_SCHEMA, DialogHelper.Remedies.TRANSFORM,
                DialogHelper.Remedies.EXPORT)) {
            assertFalse(remedy.isBlank(), "Remedy text must not be blank");
        }
    }

    @Test
    @DisplayName("showActionError sollte im Suppress-Modus nie blockieren oder werfen")
    void testActionErrorSuppressedIsNonBlocking() {
        String previous = System.getProperty("fxt.suppressErrorDialogs");
        System.setProperty("fxt.suppressErrorDialogs", "true");
        try {
            // None of these must open a modal (showAndWait) or throw — they return early.
            assertDoesNotThrow(() -> {
                DialogHelper.showActionError("Title", "What happened", "Remedy");
                DialogHelper.showActionError("Title", "What happened", "Remedy",
                        new IllegalStateException("boom"));
                DialogHelper.showActionError("Title", "What happened", "Remedy", "ERROR: raw detail");
                DialogHelper.notifyActionFailure("Title", "message");
            });
        } finally {
            if (previous == null) {
                System.clearProperty("fxt.suppressErrorDialogs");
            } else {
                System.setProperty("fxt.suppressErrorDialogs", previous);
            }
        }
    }

    @Test
    @DisplayName("Sollte Hilfe-Dialoge korrekt erstellen")
    void testCreateHelpDialog() {
        runAndWait(() -> {
            List<String[]> features = new ArrayList<>();
            features.add(new String[]{"bi-star", "Star", "Feature Description"});
            
            List<String[]> shortcuts = new ArrayList<>();
            shortcuts.add(new String[]{"Ctrl+F", "Find"});
            
            Dialog<ButtonType> dialog = DialogHelper.createHelpDialog(
                    "Help", "Help Header", "Help Sub", "bi-question-circle",
                    DialogHelper.HeaderTheme.PURPLE, features, shortcuts
            );
            
            assertNotNull(dialog);
            assertEquals("Help", dialog.getTitle());
            assertNotNull(dialog.getDialogPane());
        });
    }
}
