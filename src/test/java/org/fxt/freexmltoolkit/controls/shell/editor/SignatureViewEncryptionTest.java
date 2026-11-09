package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/** The Encrypt, Decrypt and Inspect actions of the Sign &amp; Encrypt page. */
@ExtendWith(ApplicationExtension.class)
class SignatureViewEncryptionTest {

    private EditorHost host;
    private SignatureView page;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        page = new SignatureView(host);
        stage.setScene(new Scene(new HBox(host, page), 1200, 800));
        stage.show();
    }

    private boolean visible(String id) {
        Node node = page.getContent().lookup(id);
        return node != null && node.isVisible() && node.isManaged();
    }

    @Test
    void encryptForAPasswordThenDecryptAndInspect(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("order.xml");
        Files.writeString(xml, "<order><card>4111</card><n>x</n></order>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            page.selectAction(SignatureView.Action.ENCRYPT);
            page.setDocument(xml.toFile());
            return null;
        });
        assertTrue(visible("#sig-section-encrypt") && visible("#sig-section-keystore") && visible("#enc-recipients-empty"));
        assertFalse(visible("#sig-section-sign"));

        // no recipient: a precondition
        WaitForAsyncUtils.waitForAsyncFx(2000, page::onEncrypt);
        assertTrue(page.getStatusText().contains("recipient"), page.getStatusText());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            page.addPasswordRecipient("s3cret".toCharArray());
            page.onEncrypt();
            return null;
        });
        Path encrypted = tmp.resolve("order.encrypted.xml");
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> Files.exists(encrypted)
                && WaitForAsyncUtils.waitForAsyncFx(2000, page::getStatusText).contains("Encrypted"));
        String text = Files.readString(encrypted);
        assertTrue(text.contains("EncryptedData") && !text.contains("4111") && text.contains("DerivedKey"), text);
        assertEquals(1, page.recipientsForTests().size());

        // decrypt with the password: a new, unsaved tab with the original
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            page.selectAction(SignatureView.Action.DECRYPT);
            page.setDocument(encrypted.toFile());
            page.selectDecryptionSource("password");
            return null;
        });
        assertFalse(visible("#sig-section-keystore"), "the keystore is only shown for the keystore source");
        assertTrue(visible("#dec-password"));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            page.setDecryptionPassword("s3cret");
            page.onDecrypt();
            return null;
        });
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.getActiveDocument().map(d -> d.getDisplayName().contains("decrypted")).orElse(false)));
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveText().orElse("")).contains("<card>4111</card>"));

        // a wrong password is the user's input
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            page.setDecryptionPassword("wrong");
            page.onDecrypt();
            return null;
        });
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                page::getStatusText).contains("None of the given keys"));

        // inspect: a report tab
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            page.selectAction(SignatureView.Action.INSPECT);
            page.onInspect();
            return null;
        });
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.getActiveText().orElse("").contains("EncryptedData element(s)")));
        assertTrue(WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.getActiveText().orElse("")).contains("PBKDF2"));
    }

    @Test
    void anXPathTargetAndAKeyFile(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("order.xml");
        Files.writeString(xml, "<order xmlns=\"urn:o\"><card>4111</card><n>x</n></order>");
        Path key = Files.writeString(tmp.resolve("k.key"), "000102030405060708090a0b0c0d0e0f");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            page.selectAction(SignatureView.Action.ENCRYPT);
            page.setDocument(xml.toFile());
            page.addKeyFileRecipient(key.toFile(), "partner");
            javafx.scene.control.ToggleButton xpath =
                    (javafx.scene.control.ToggleButton) page.getContent().lookup("#enc-target-xpath");
            xpath.setSelected(true);
            ((javafx.scene.control.TextField) page.getContent().lookup("#enc-xpath")).setText("//*[local-name()='card']");
            page.onEncrypt();
            return null;
        });
        Path encrypted = tmp.resolve("order.encrypted.xml");
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> Files.exists(encrypted)
                && WaitForAsyncUtils.waitForAsyncFx(2000, page::getStatusText).contains("Encrypted"));
        String text = Files.readString(encrypted);
        assertTrue(text.contains("<n>x</n>") && !text.contains("4111") && text.contains("partner")
                && text.contains("kw-aes128"), text);
    }

    @Test
    void encryptElementFromTheEditorMenu(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("order.xml");
        Files.writeString(xml, "<p:order xmlns:p=\"urn:o\"><p:card>4111</p:card><p:card>5500</p:card></p:order>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            page.setDocument(xml.toFile());
            page.encryptElement("/p:order/p:card[2]");
            page.addPasswordRecipient("pw".toCharArray());
            page.onEncrypt();
            return null;
        });
        assertEquals(SignatureView.Action.ENCRYPT, page.currentAction());
        Path encrypted = tmp.resolve("order.encrypted.xml");
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> Files.exists(encrypted)
                && WaitForAsyncUtils.waitForAsyncFx(2000, page::getStatusText).contains("Encrypted"));
        String text = Files.readString(encrypted);
        assertTrue(text.contains("4111") && !text.contains("5500"), "only the second card: " + text);
    }

    @Test
    void theLegacyWarningFollowsTheChoice() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> page.selectAction(SignatureView.Action.ENCRYPT));
        assertFalse(visible("#enc-legacy-warning"));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            @SuppressWarnings("unchecked")
            var data = (javafx.scene.control.ComboBox<org.fxt.freexmltoolkit.service.crypto.XmlEncryptionAlgorithms.Algorithm>)
                    page.getContent().lookup("#enc-data-alg");
            data.setValue(org.fxt.freexmltoolkit.service.crypto.XmlEncryptionAlgorithms.Algorithm.TRIPLEDES_CBC);
        });
        assertTrue(visible("#enc-legacy-warning"));
    }

    @Test
    void caretXPathBecomesNamespaceFree() {
        assertEquals("/*[local-name()='order']/*[local-name()='item'][2]",
                EncryptionRecipients.localNameXPath("/p:order/item[2]"));
        assertNull(EncryptionRecipients.localNameXPath("/"));
        assertEquals(java.util.Map.of("a", "1", "b", "x=y"), EncryptionRecipients.properties("a=1\n b = x=y \nnot a pair"));
    }
}
