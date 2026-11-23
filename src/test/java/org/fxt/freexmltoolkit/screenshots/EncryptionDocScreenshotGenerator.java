package org.fxt.freexmltoolkit.screenshots;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.Activity;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.OpenDocument;
import org.fxt.freexmltoolkit.controls.shell.editor.SignatureView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * Captures the XML Encryption images for {@code docs/guides/sign-encrypt/encryption.md}: the Encrypt
 * action of the Sign &amp; Encrypt page with four recipients ({@code encrypt-recipients}) and the report
 * of Inspect Encryption for a document encrypted for three recipients ({@code encrypt-inspect}). Uses the
 * demo keys of {@code release/examples/encryption} and a synthetic order.
 *
 * <pre>{@code
 * xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots --tests "*EncryptionDocScreenshotGenerator*"
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class EncryptionDocScreenshotGenerator {

    private static final String ORDER = """
            <?xml version="1.0" encoding="UTF-8"?>
            <order xmlns="urn:example:shop" id="2026-1104">
              <customer>
                <name>Example Trading Ltd</name>
                <email>orders@trading.example</email>
              </customer>
              <items>
                <item sku="BK-101" quantity="2">XML in a Nutshell</item>
                <item sku="BK-207" quantity="1">XSLT 3.0 Cookbook</item>
              </items>
              <payment>
                <method>invoice</method>
                <reference>INV-2026-1104</reference>
              </payment>
            </order>
            """;

    private final DocShell doc = new DocShell("encryption-screenshot");

    @Start
    void start(Stage stage) throws Exception {
        doc.start(stage);
    }

    @Test
    void generateEncryptionScreenshots() throws Exception {
        doc.boot();
        EditorHost host = doc.host();
        File keys = new File(DocFiles.examples(), "encryption");
        Path order = keys.toPath().resolve("purchase-order.xml");
        Files.writeString(order, ORDER);

        // --- Encrypt: the document for two certificates, a shared key and a password ---
        doc.scene("encrypt-recipients", () -> {
            doc.open(order, "urn:example:shop");
            doc.activity(Activity.SIGNATURE);
            SignatureView view = doc.findType(SignatureView.class);
            doc.onFx(() -> {
                view.useActiveDocument();
                view.selectAction(SignatureView.Action.ENCRYPT);
            });
            doc.call(view, "addCertificateRecipient", new File(keys, "demo-rsa.pem"));
            doc.call(view, "addCertificateRecipient", new File(keys, "demo-ec.pem"));
            doc.call(view, "addKeyFileRecipient", new File(keys, "demo-shared.key"), "demo-shared-2026");
            doc.call(view, "addPasswordRecipient", (Object) "example".toCharArray());
            doc.settle(800);
            doc.shot("encrypt-recipients");
        });

        // --- Inspect Encryption: what a document encrypts, for whom, with which algorithms ---
        doc.scene("encrypt-inspect", () -> {
            Path encrypted = keys.toPath().resolve("order-three-recipients.xml");
            doc.open(encrypted, "EncryptedData");
            doc.activity(Activity.SIGNATURE);
            SignatureView view = doc.findType(SignatureView.class);
            doc.onFx(() -> {
                view.useActiveDocument(); // while the encrypted document is the active tab
                host.selectTabShowing(view);
                view.selectAction(SignatureView.Action.INSPECT);
                view.onInspect();
            });
            doc.waitUntilFx(60, () -> report(host) != null && view.getStatusText() != null
                    && !view.getStatusText().isBlank() && !view.getStatusText().startsWith("Inspecting"));
            if (!doc.fx(() -> host.getDocumentText(report(host)).orElse("")).contains("Recipient")
                    && !doc.fx(() -> host.getDocumentText(report(host)).orElse("")).contains("AES")) {
                throw new IllegalStateException("the report does not describe the encryption");
            }
            doc.settle(600);
            doc.onFx(() -> {
                host.selectDocument(report(host));
                doc.shell().setInspectorVisible(false); // the report's long lines need the room
            });
            doc.settle(1000);
            doc.shot("encrypt-inspect");
            doc.onFx(() -> doc.shell().setInspectorVisible(true));
        });
    }

    private static OpenDocument report(EditorHost host) {
        return host.getOpenDocuments().stream()
                .filter(d -> "Encryption-Report.txt".equals(d.getDisplayName())).findFirst().orElse(null);
    }
}
