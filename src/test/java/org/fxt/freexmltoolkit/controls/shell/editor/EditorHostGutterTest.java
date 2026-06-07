package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class EditorHostGutterTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    @Test
    void attachesGutterToXsltEditor(@TempDir Path tmp) throws Exception {
        Path xsl = tmp.resolve("sheet.xslt");
        Files.writeString(xsl,
                "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\"/>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsl));
        WaitForAsyncUtils.waitForFxEvents();
        boolean applied = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.setActiveEditorGutterFactory(line -> (Node) new Region()));
        assertTrue(applied, "XSLT editor should accept a gutter factory");
    }

    @Test
    void jsonEditorRejectsGutter(@TempDir Path tmp) throws Exception {
        Path json = tmp.resolve("data.json");
        Files.writeString(json, "{}");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(json));
        WaitForAsyncUtils.waitForFxEvents();
        boolean applied = WaitForAsyncUtils.waitForAsyncFx(2000,
                () -> host.setActiveEditorGutterFactory(line -> (Node) new Region()));
        assertFalse(applied, "JSON editor has no gutter support");
    }
}
