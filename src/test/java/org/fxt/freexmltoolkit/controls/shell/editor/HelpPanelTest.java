package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * Verifies that the Help activity side panel offers the same documentation
 * destinations the retired legacy Help tab embedded as WebViews — the FXT
 * documentation site, the FundsXML website, and the FundsXML4 schema docs —
 * now as external-browser quick links (parity before the legacy tab is removed).
 */
@ExtendWith(ApplicationExtension.class)
class HelpPanelTest {

    private HelpPanel panel;

    @Start
    void start(Stage stage) {
        panel = new HelpPanel();
        stage.setScene(new Scene(panel, 320, 600));
        stage.show();
    }

    @Test
    void exposesTheLegacyDocumentationQuickLinks() {
        List<String> urls = panel.getQuickLinkUrls();
        assertTrue(urls.contains("https://karlkauc.github.io/FreeXmlToolkit"),
                "FXT documentation link must be offered");
        assertTrue(urls.contains("http://www.fundsxml.org"),
                "FundsXML website link must be offered");
        assertTrue(urls.contains("https://fundsxml.github.io/"),
                "FundsXML4 schema documentation link must be offered");
    }
}
