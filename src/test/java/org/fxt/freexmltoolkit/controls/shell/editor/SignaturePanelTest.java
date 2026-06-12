package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification of the Signature panel (Figma mockup node 50:2): an action
 * nav (Create Certificate / Sign XML File / Validate Signature / Expert Mode)
 * switching the visible form section, a shared KEYSTORE section, and the
 * existing sign/validate/create behaviors.
 */
@ExtendWith(ApplicationExtension.class)
class SignaturePanelTest {

    private EditorHost host;
    private SignaturePanel panel;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        panel = new SignaturePanel(host);
        stage.setScene(new Scene(new HBox(host, panel), 1000, 700));
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
    void titleFollowsTheSharedSidePanelConvention() {
        WaitForAsyncUtils.waitForFxEvents();
        Label title = (Label) panel.lookup(".fxt-side-panel-title");
        assertNotNull(title, "panel must keep the shared side-panel title class");
        assertEquals("SIGNATURE", title.getText());
    }

    @Test
    void signIsTheDefaultActionAndKeepsThePanelFormFree() {
        // Per the mockup the sign form lives in the editor area, not in the panel:
        // SIGN is selected by default and no panel form section is visible.
        WaitForAsyncUtils.waitForFxEvents();
        javafx.scene.control.ToggleButton signNav =
                (javafx.scene.control.ToggleButton) panel.lookup("#sig-nav-sign");
        assertTrue(signNav.isSelected(), "Sign XML File must be the default nav action");
        assertNull(panel.lookup("#sig-section-sign"), "the sign form must not live in the panel");
        assertFalse(panel.lookup("#sig-section-validate").isVisible());
        assertFalse(panel.lookup("#sig-section-create").isVisible());
        assertFalse(panel.lookup("#sig-section-expert").isVisible());
    }

    @Test
    void selectingANavActionSwitchesTheVisibleSection() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.selectAction(SignaturePanel.Action.VALIDATE);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(panel.lookup("#sig-section-validate").isVisible());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.selectAction(SignaturePanel.Action.EXPERT);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(panel.lookup("#sig-section-expert").isVisible());
        assertFalse(panel.lookup("#sig-section-validate").isVisible());
    }

    @Test
    void signNavOpensTheSignCardInTheEditorArea(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root/>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            ((javafx.scene.control.ToggleButton) panel.lookup("#sig-nav-sign")).fire();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        Label docName = (Label) host.lookup("#sign-doc-name");
        assertNotNull(docName, "the Sign card must open in the editor area");
        assertEquals("doc.xml", docName.getText(), "the card must show the active document");
        assertNotNull(host.lookup("#sign-card-run"), "the card must offer Sign Document");
    }

    @Test
    void exposesCertificateCreationViaTheNav() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.selectAction(SignaturePanel.Action.CREATE);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(panel.lookup("#sig-section-create").isVisible(), "create section must show");
        Button create = (Button) panel.lookup("#sig-create-run");
        assertNotNull(create, "panel must offer a 'Create Certificate' action");
        assertEquals("Create Certificate", create.getText());
    }

    @Test
    void exposesTheDetailedValidationAction() {
        WaitForAsyncUtils.waitForFxEvents();
        boolean present = panel.lookupAll(".button").stream()
                .anyMatch(n -> n instanceof Button b && "Validate (Details)".equals(b.getText()));
        assertTrue(present, "panel must offer the detailed-validation action");
    }

    @Test
    void exposesTheTrustValidationInTheExpertSection() {
        WaitForAsyncUtils.waitForFxEvents();
        Button trust = (Button) panel.lookup("#sig-trust-run");
        assertNotNull(trust, "expert section must offer the trust validation");
        assertEquals("Validate (Trust)", trust.getText());
        assertNotNull(panel.lookup("#sig-truststore-name"), "expert section must show the trust store");
    }

    @Test
    void keystoreRowShowsTheChosenFile(@TempDir Path tmp) throws Exception {
        Path ks = tmp.resolve("keystore.jks");
        Files.writeString(ks, "stub");
        Label name = (Label) panel.lookup("#sig-keystore-name");
        assertNotNull(name, "keystore section must show the keystore file");
        assertEquals("none", name.getText());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setKeystore(ks.toFile());
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("keystore.jks", name.getText());
    }

    @Test
    void signCardRendersCertificateDetails() {
        java.util.concurrent.atomic.AtomicReference<SignDocumentView> ref = new java.util.concurrent.atomic.AtomicReference<>();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            SignDocumentView view = new SignDocumentView(panel, host);
            view.showCertificate(new CertificateDetailsRunner.CertificateDetails(
                    "fxt-signer", "FreeXmlToolkit", "AT", true,
                    java.time.LocalDateTime.now().minusDays(1),
                    java.time.LocalDateTime.now().plusDays(612),
                    612, true, "0x4F2A9C1E", "SHA256withRSA", "digitalSignature",
                    "A1:B2:C3"));
            ref.set(view);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        // Lookup via the certificate box: an unskinned ScrollPane (not in a scene)
        // does not traverse into its content.
        var box = ref.get().certificateBoxForTests();
        assertEquals("fxt-signer", ((Label) box.lookup("#sign-cert-name")).getText());
        assertTrue(((Label) box.lookup("#sign-cert-validity")).getText().contains("612 days remaining"),
                "the validity banner must show the remaining days");
        assertTrue(box.isVisible());
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
