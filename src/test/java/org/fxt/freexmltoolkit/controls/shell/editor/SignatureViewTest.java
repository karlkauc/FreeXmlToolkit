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
 * TestFX verification of the Signature page (UX plan 4.3): one page whose form follows the
 * action picked in the side panel, the KEYSTORE inputs shown once above the forms that need
 * them, and the sign/validate/create/trust behaviours.
 */
@ExtendWith(ApplicationExtension.class)
class SignatureViewTest {

    private EditorHost host;
    private SignatureView panel;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        panel = new SignatureView(host);
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
            panel.useActiveDocument();
            panel.validateActive();
            return null;
        });
        WaitForAsyncUtils.waitFor(4, TimeUnit.SECONDS, () -> !panel.getStatusText().equals("Validating…"));
        assertTrue(panel.getStatusText().contains("no signature"),
                "an unsigned document must be reported as having no signature: " + panel.getStatusText());
    }

    @Test
    void signIsTheDefaultActionAndTheKeystoreStandsOnceAboveItsForm() {
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(SignatureView.Action.SIGN, panel.currentAction());
        assertVisible("#sig-section-document", "#sig-section-keystore", "#sig-section-sign");
        assertHidden("#sig-section-validate", "#sig-section-create", "#sig-section-expert");
        assertEquals(1, panel.lookupAll("#sig-alias").size(), "alias and passwords exist once");
        assertNotNull(panel.lookup("#sign-card-run"));
    }

    @Test
    void everyActionShowsTheInputsItNeedsAndItsPrimaryButton() {
        java.util.List<String> shown = new java.util.ArrayList<>();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setOnActionShown(shown::add);
            panel.selectAction(SignatureView.Action.VALIDATE);
            return null;
        });
        assertVisible("#sig-section-document", "#sig-section-validate");
        assertHidden("#sig-section-keystore", "#sig-section-create");
        assertEquals("Validate Signature", ((Button) panel.lookup("#sig-validate-run")).getText());
        assertEquals("Validate (Details)", ((Button) panel.lookup("#sig-validate-details")).getText());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.selectAction(SignatureView.Action.CREATE);
            return null;
        });
        assertVisible("#sig-section-keystore", "#sig-section-create");
        assertHidden("#sig-section-document", "#sig-section-validate");
        assertEquals("Create Certificate", ((Button) panel.lookup("#sig-create-run")).getText());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.selectAction(SignatureView.Action.EXPERT);
            return null;
        });
        assertVisible("#sig-section-document", "#sig-section-expert");
        assertHidden("#sig-section-keystore");
        assertEquals("Validate (Trust)", ((Button) panel.lookup("#sig-trust-run")).getText());
        assertNotNull(panel.lookup("#sig-truststore-password"), "the trust store has its own password");

        assertEquals(java.util.List.of("sign", "validate", "create", "expert"), shown,
                "the side panel learns the current action, then every switch");
        assertEquals(java.util.List.of("sign", "validate", "create", "expert"),
                SignatureView.SECTIONS.stream().map(PageNavigator.Entry::key).toList(), "order of use");
    }

    @Test
    void switchingTheActionClearsTheStatus() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.signFile(null);
            return null;
        });
        assertFalse(panel.getStatusText().isBlank(), "a precondition message is shown");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.selectAction(SignatureView.Action.VALIDATE);
            return null;
        });
        assertTrue(panel.getStatusText().isBlank(), "the old result does not carry over to another action");
    }

    private void assertVisible(String... selectors) {
        for (String selector : selectors) {
            assertTrue(panel.lookup(selector).isVisible(), selector + " must be shown");
        }
    }

    private void assertHidden(String... selectors) {
        for (String selector : selectors) {
            assertFalse(panel.lookup(selector).isVisible(), selector + " must be hidden");
        }
    }

    @Test
    void keystoreRowShowsTheChosenFile(@TempDir Path tmp) throws Exception {
        Path ks = tmp.resolve("keystore.jks");
        Files.writeString(ks, "stub");
        Label name = (Label) panel.lookup("#sig-keystore-name");
        assertNotNull(name, "keystore section must show the keystore file");
        assertTrue(name.getText().startsWith("No keystore"), "an empty slot names the action that fills it");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setKeystore(ks.toFile());
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals("keystore.jks", name.getText());
    }

    @Test
    void signFormRendersCertificateDetails() {
        java.util.concurrent.atomic.AtomicReference<SignatureView> ref = new java.util.concurrent.atomic.AtomicReference<>();
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            SignatureView view = new SignatureView(host);
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

    @Test
    void missingCredentialFieldsAreHighlightedAndClearOnEdit() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setCredentials("", "secret", "");
            panel.createCertificate();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(panel.aliasField().getStyleClass().contains("fxt-field-error"),
                "the blank alias field must be highlighted");
        assertFalse(panel.keystorePasswordField().getStyleClass().contains("fxt-field-error"),
                "the filled password field must not be highlighted");

        // Typing into the field clears its highlight without another action.
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.aliasField().setText("my-alias");
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(panel.aliasField().getStyleClass().contains("fxt-field-error"),
                "editing the field must clear its highlight");
    }

    @Test
    void signingWithoutKeystoreHighlightsTheKeystoreEntry(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("to-sign.xml");
        Files.writeString(xml, "<root/>");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.setKeystore(null);
            panel.signFile(xml.toFile());
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(panel.getStatusText().contains("keystore"),
                "the status must name the missing keystore: " + panel.getStatusText());
        Label keystoreName = (Label) panel.lookup("#sig-keystore-name");
        assertNotNull(keystoreName);
        assertTrue(keystoreName.getStyleClass().contains("fxt-field-error"),
                "the keystore source entry must be highlighted");
    }

    @Test
    void droppingStoresOnTheSourceRowsLoadsThem(@TempDir Path tmp) throws Exception {
        javafx.scene.layout.Region keystoreRow = (javafx.scene.layout.Region) panel.lookup("#sig-keystore-row");
        javafx.scene.layout.Region trustRow = (javafx.scene.layout.Region) panel.lookup("#sig-truststore-row");
        assertNotNull(keystoreRow, "the KEYSTORE row must exist");
        assertNotNull(trustRow, "the TRUST STORE row must exist");
        assertNotNull(keystoreRow.getOnDragOver(), "the KEYSTORE row must accept file drags");
        assertNotNull(trustRow.getOnDragOver(), "the TRUST STORE row must accept file drags");

        Path keystore = tmp.resolve("signing.jks");
        Files.writeString(keystore, "x");
        Path trustStore = tmp.resolve("anchors.p12");
        Files.writeString(trustStore, "x");

        // Build the mocked drag events BEFORE the timed FX block: creating the first Mockito mock
        // in a JVM costs ~2.4 s of ByteBuddy code generation, which alone exceeds the budget below.
        var keystoreDrop = dropEventWithFiles(keystore.toFile());
        var trustDrop = dropEventWithFiles(trustStore.toFile());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            keystoreRow.getOnDragDropped().handle(keystoreDrop);
            trustRow.getOnDragDropped().handle(trustDrop);
            return null;
        });

        assertEquals("signing.jks", ((Label) panel.lookup("#sig-keystore-name")).getText(),
                "the dropped keystore must be selected");
        assertEquals("anchors.p12", ((Label) panel.lookup("#sig-truststore-name")).getText(),
                "the dropped trust store must be selected");
    }

    /** Mocks a DRAG_DROPPED event carrying OS files (TestFX cannot simulate real file drags). */
    private static javafx.scene.input.DragEvent dropEventWithFiles(java.io.File... files) {
        javafx.scene.input.Dragboard dragboard = org.mockito.Mockito.mock(javafx.scene.input.Dragboard.class);
        org.mockito.Mockito.when(dragboard.hasFiles()).thenReturn(true);
        org.mockito.Mockito.when(dragboard.getFiles()).thenReturn(java.util.List.of(files));
        javafx.scene.input.DragEvent event = org.mockito.Mockito.mock(javafx.scene.input.DragEvent.class);
        org.mockito.Mockito.when(event.getDragboard()).thenReturn(dragboard);
        return event;
    }

}
