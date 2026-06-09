package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification that the Signature panel validates the active document and
 * reports an unsigned document as invalid. (A full sign+verify round-trip needs
 * a keystore and is a follow-up increment.)
 */
@ExtendWith(ApplicationExtension.class)
class SignaturePanelTest {

    private EditorHost host;
    private SignaturePanel panel;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        panel = new SignaturePanel(host);
        stage.setScene(new Scene(new HBox(host, panel), 1000, 600));
        stage.show();
    }

    @Test
    void reportsUnsignedDocumentAsInvalid(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root><child>value</child></root>");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.validateActive();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> !panel.getStatusText().equals("Validating…"));
        assertTrue(panel.getStatusText().contains("invalid"), panel.getStatusText());
    }

    @Test
    void exposesTheCreateCertificateSection() {
        WaitForAsyncUtils.waitForFxEvents();
        boolean hasSection = panel.lookupAll(".titled-pane").stream()
                .anyMatch(n -> n instanceof javafx.scene.control.TitledPane tp
                        && "Create Self-Signed Certificate".equals(tp.getText()));
        boolean hasButton = panel.lookupAll(".button").stream()
                .anyMatch(n -> n instanceof javafx.scene.control.Button b
                        && "Create Certificate".equals(b.getText()));
        assertTrue(hasSection, "panel must offer the certificate-creation section");
        assertTrue(hasButton, "panel must offer a 'Create Certificate' action");
    }

    @Test
    void exposesTheDetailedValidationAction() {
        WaitForAsyncUtils.waitForFxEvents();
        boolean present = panel.lookupAll(".button").stream()
                .anyMatch(n -> n instanceof javafx.scene.control.Button b && "Validate (Details)".equals(b.getText()));
        assertTrue(present, "panel must offer the detailed-validation action");
    }

    @Test
    void rejectsCertificateCreationWithoutCredentials() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setCredentials("", "", "");
            panel.createCertificate();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(panel.getStatusText().toLowerCase().contains("required"),
                "creation without alias/passwords must be rejected: " + panel.getStatusText());
    }
}
