package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.i18n.I18n;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * In a narrow window with the longer German texts, the controls of the Sign &amp; Encrypt page
 * keep their full width (wrapping instead of being cut to "Zertifikat…" / "S…").
 */
@ExtendWith(ApplicationExtension.class)
class SignatureViewNarrowLayoutTest {

    private SignatureView page;

    @Start
    void start(Stage stage) {
        I18n.setLocale(Locale.GERMAN);
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        page = new SignatureView(new EditorHost());
        stage.setScene(new Scene(page, 520, 800));
        stage.show();
    }

    @AfterEach
    void restoreLocale() {
        I18n.setLocale(Locale.ENGLISH);
    }

    private void assertFullWidth(String id) {
        Control control = WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            Node node = page.getContent().lookup(id);
            return node instanceof Control c ? c : null;
        });
        assertNotNull(control, id);
        double width = control.getWidth();
        double pref = WaitForAsyncUtils.waitForAsyncFx(2000, () -> control.prefWidth(-1));
        assertTrue(width + 0.5 >= pref, id + " is cut: " + width + " < " + pref);
    }

    private void show(SignatureView.Action action) {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            page.selectAction(action);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void recipientButtonsWrapInsteadOfBeingCut() {
        show(SignatureView.Action.ENCRYPT);
        for (String id : new String[]{"#enc-add-certificate", "#enc-add-keystore", "#enc-add-key-file",
                "#enc-add-password", "#enc-data-alg", "#sign-doc-browse"}) {
            assertFullWidth(id);
        }
    }

    @Test
    void keyTypeChoiceKeepsItsWidth() {
        show(SignatureView.Action.CREATE);
        assertFullWidth("#sig-create-key-type");
    }
}
