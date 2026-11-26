package org.fxt.freexmltoolkit.screenshots;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ExampleFiles;
import org.fxt.freexmltoolkit.controls.shell.editor.SignatureView;
import org.fxt.freexmltoolkit.i18n.I18n;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The screen part of the tutorial video "Sign and encrypt XML" (storyboard {@code videos/sign-encrypt.json},
 * after {@code docs/guides/sign-encrypt/}): the forms of the Sign &amp; Encrypt page, and a real encryption of
 * the example order for a password, decrypted again.
 *
 * <pre>{@code
 * python3 scripts/make-video.py sign-encrypt --lang de
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class SignEncryptDocVideo {

    private static final String PASSWORD = "Video-Demo-2026";

    private final DocShell doc = new DocShell("video");
    private DocVideoRecorder video;

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                .clearLastOpenFiles();
        I18n.initialize(null);
        doc.start(stage, DocVideoRecorder.WIDTH, DocVideoRecorder.HEIGHT);
    }

    @Test
    void record() throws Exception {
        doc.boot();
        EditorHost host = doc.host();
        Path folder = ExampleFiles.defaultTarget();
        ExampleFiles.install(folder);
        Path xml = folder.resolve("order.xml");
        doc.onFx(() -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("PO-1042")).orElse(false));
        doc.settle(1500);

        video = new DocVideoRecorder(doc);
        VideoTyping keys = new VideoTyping(doc, video);
        video.start();
        try {
            video.scene("page", () -> {
                video.click(doc.<Node>find("#activity-signature"));
                video.pause(1800);
                video.zoom(doc.<Node>find("#leftPanelWrapper"));
                video.spoken(0.5);
                video.zoomOut();
                video.spoken(0.95);
            });

            video.scene("certificate", () -> {
                show(SignatureView.Action.CREATE);
                zoomEditor(video);
                video.moveTo(doc.<Node>find("#sig-create-key-type"));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("sign", () -> {
                show(SignatureView.Action.SIGN);
                zoomEditor(video);
                for (String id : new String[] {"#sig-keystore-row", "#sign-type-enveloped", "#sign-type-enveloping",
                        "#sign-type-detached", "#sign-method", "#sign-digest"}) {
                    moveIfShown(video, id);
                    video.pause(500);
                }
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("sign-run", () -> {
                show(SignatureView.Action.SIGN);
                SignatureView view = doc.findType(SignatureView.class);
                java.nio.file.Path keystore = folder.resolve("demo-rsa.p12");
                try {
                    java.nio.file.Files.copy(java.nio.file.Path.of("release/examples/encryption/demo-rsa.p12"), keystore,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
                doc.onFx(() -> {
                    view.setKeystore(keystore.toFile());
                    view.setCredentials("demo-rsa", "changeit", "changeit");
                });
                video.pause(800);
                zoomEditor(video);
                video.moveTo(doc.<Node>find("#sig-keystore-row"));
                video.spoken(0.35);
                clickIfShown(video, "#sign-card-run");
                java.nio.file.Path signed = xml.resolveSibling("order.signed.xml");
                try {
                    doc.waitUntilFx(30, () -> java.nio.file.Files.exists(signed));
                } catch (Exception e) {
                    System.out.println("[video] no signed file");
                }
                video.pause(1500);
                video.zoomOut();
                Node tab = tabLabeled("order.signed.xml");
                if (tab != null) {
                    video.click(tab);
                    video.pause(1200);
                    int at = host.getActiveText().orElse("").indexOf("Signature");
                    if (at > 0) {
                        doc.onFx(() -> {
                            var area = host.getActiveCodeArea();
                            if (area != null) {
                                area.showParagraphAtTop(Math.max(0, area.offsetToPosition(at,
                                        org.fxmisc.richtext.model.TwoDimensional.Bias.Forward).getMajor() - 2));
                            }
                        });
                    }
                    video.pause(800);
                    zoomEditor(video);
                }
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("verify-run", () -> {
                show(SignatureView.Action.SIGN);
                video.pause(600);
                Node link = doc.find("#sig-validate-signed");
                if (link != null && doc.fx(link::isVisible)) {
                    video.click(link);
                    video.pause(3000);
                } else {
                    show(SignatureView.Action.VALIDATE);
                    clickIfShown(video, "#sig-validate-run");
                    video.pause(3000);
                }
                zoomEditor(video);
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("verify", () -> {
                show(SignatureView.Action.VALIDATE);
                zoomEditor(video);
                video.spoken(0.6);
                show(SignatureView.Action.EXPERT);
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("encrypt", () -> {
                // the signed file is the active document now: encrypt the original order again
                doc.onFx(() -> host.openFile(xml));
                doc.waitUntilFx(10, () -> host.getActiveText().orElse("").contains("PO-1042")
                        && !host.getActiveText().orElse("").contains("Signature"));
                show(SignatureView.Action.ENCRYPT);
                zoomEditor(video);
                for (String id : new String[] {"#enc-target-document", "#enc-target-caret", "#enc-target-xpath", "#enc-content-only"}) {
                    moveIfShown(video, id);
                    video.pause(600);
                }
                clickIfShown(video, "#enc-target-document");
                video.spoken(0.95);
            });

            video.scene("recipients", () -> {
                for (String id : new String[] {"#enc-add-certificate", "#enc-add-keystore", "#enc-add-key-file"}) {
                    moveIfShown(video, id);
                    video.pause(500);
                }
                video.spoken(0.5);
                clickIfShown(video, "#enc-add-password");
                video.pause(1200);
                Window dialog = keys.dialog();
                if (dialog != null) {
                    for (String id : new String[] {"#enc-password-1", "#enc-password-2"}) {
                        Node field = doc.fx(() -> dialog.getScene().getRoot().lookup(id));
                        video.click(field);
                        video.typeIntoFocused(PASSWORD);
                    }
                    clickDefault(video, dialog);
                }
                video.spoken(0.95);
            });

            video.scene("algorithms", () -> {
                clickIfShown(video, "#enc-advanced");
                video.pause(1000);
                zoomEditor(video);
                for (String id : new String[] {"#enc-key-transport", "#enc-kdf", "#enc-key-wrap"}) {
                    moveIfShown(video, id);
                    video.pause(500);
                }
                video.spoken(0.6);
                moveIfShown(video, "#enc-data-alg");
                video.spoken(0.95);
                clickIfShown(video, "#enc-advanced");
                video.zoomOut();
            });

            video.scene("run-encrypt", () -> {
                clickIfShown(video, "#enc-run");
                java.nio.file.Path encrypted = xml.resolveSibling("order.encrypted.xml");
                try {
                    doc.waitUntilFx(30, () -> java.nio.file.Files.exists(encrypted));
                } catch (Exception e) {
                    System.out.println("[video] no encrypted file: "
                            + doc.fx(() -> ((javafx.scene.control.Labeled) doc.find("#sig-status")).getText()));
                }
                video.pause(1200);
                // the result opens behind the page: show its tab (the page takes it as its document next time)
                Node tab = doc.fx(() -> doc.stage().getScene().getRoot().lookupAll(".tab").stream()
                        .filter(t -> t.lookupAll(".tab-label").stream().anyMatch(l -> l instanceof javafx.scene.control.Labeled lb
                                && lb.getText() != null && lb.getText().contains("order.encrypted.xml")))
                        .findFirst().orElse(null));
                if (tab != null) {
                    video.click(tab);
                } else {
                    doc.onFx(() -> host.openFile(encrypted));
                }
                try {
                    doc.waitUntilFx(15, () -> host.getActiveText().orElse("").contains("EncryptedData"));
                } catch (Exception e) {
                    System.out.println("[video] encrypted document not shown");
                }
                video.pause(1500);
                video.zoomOut();
                Bounds editor = video.screenBounds(doc.find("#editorHost"));
                video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth(), editor.getHeight() * 0.6));
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("decrypt", () -> {
                video.click(doc.<Node>find("#activity-signature"));
                video.pause(1200);
                show(SignatureView.Action.DECRYPT);
                clickIfShown(video, "#dec-key-password");
                Node password = doc.find("#dec-password");
                if (password != null && doc.fx(password::isVisible)) {
                    video.click(password);
                    video.typeIntoFocused(PASSWORD);
                }
                video.spoken(0.3);
                clickIfShown(video, "#dec-run");
                video.pause(3000);
                video.spoken(0.6);
                show(SignatureView.Action.INSPECT);
                clickIfShown(video, "#inspect-run");
                video.pause(2500);
                zoomEditor(video);
                video.spoken(0.95);
                video.zoomOut();
            });

            video.scene("editor-menu", () -> {
                doc.onFx(() -> host.openFile(xml));
                doc.waitUntilFx(10, () -> host.getActiveText().orElse("").contains("PO-1042"));
                var area = host.getActiveCodeArea();
                int at = host.getActiveText().orElse("").indexOf("<customer>") + 3;
                Bounds c = doc.fx(() -> area.getCharacterBoundsOnScreen(at, at + 1).orElse(null));
                if (c != null) {
                    video.click(c.getCenterX(), c.getCenterY());
                    video.pause(400);
                    video.rightClick(c.getCenterX(), c.getCenterY());
                    video.pause(900);
                    try {
                        Node item = video.inPopup("#editor-encrypt-element");
                        video.moveTo(item);
                        video.pause(800);
                    } catch (Exception e) {
                        System.out.println("[video] no Encrypt Element in the editor menu");
                    }
                }
                video.spoken(0.95);
                new VideoTyping(doc, video).hidePopups();
            });

            video.scene("cli", () -> video.spoken(0.95));
        } finally {
            video.stop();
        }
    }

    /** Picks the section in the side panel, as a user does: that also brings the page to the front. */
    private void show(SignatureView.Action action) {
        Node entry = doc.find("#sig-nav-" + action.key());
        if (entry != null && doc.fx(() -> entry.localToScreen(entry.getBoundsInLocal()) != null)) {
            video.click(entry);
            doc.settle(900);
            return;
        }
        SignatureView view = doc.findType(SignatureView.class);
        if (view != null) {
            doc.onFx(() -> view.selectAction(action));
            doc.settle(900);
        }
    }

    private void moveIfShown(DocVideoRecorder video, String id) {
        Node n = doc.find(id);
        if (n != null && doc.fx(() -> n.isVisible() && n.localToScreen(n.getBoundsInLocal()) != null)) {
            video.moveTo(n);
        }
    }

    private void clickIfShown(DocVideoRecorder video, String id) {
        Node n = doc.find(id);
        if (n != null && doc.fx(() -> n.isVisible() && n.localToScreen(n.getBoundsInLocal()) != null)) {
            video.click(n);
        } else {
            System.out.println("[video] not shown: " + id);
        }
    }

    private void zoomEditor(DocVideoRecorder video) {
        Bounds editor = video.screenBounds(doc.find("#editorHost"));
        video.zoom(new BoundingBox(editor.getMinX(), editor.getMinY(), editor.getWidth(), editor.getHeight() * 0.8));
    }

    private void clickDefault(DocVideoRecorder video, Window dialog) {
        Node ok = doc.fx(() -> dialog.getScene().getRoot().lookupAll(".button").stream()
                .filter(n -> n instanceof javafx.scene.control.Button b && b.isDefaultButton() && !b.isDisabled())
                .findFirst().orElse(null));
        if (ok != null) {
            video.click(ok);
        } else {
            System.out.println("[video] OK disabled in password dialog");
            doc.onFx(dialog::hide);
        }
        video.pause(600);
    }

    private Node tabLabeled(String text) {
        return doc.fx(() -> doc.stage().getScene().getRoot().lookupAll(".tab").stream()
                .filter(t -> t.lookupAll(".tab-label").stream().anyMatch(l -> l instanceof javafx.scene.control.Labeled lb
                        && lb.getText() != null && lb.getText().contains(text)))
                .findFirst().orElse(null));
    }
}
