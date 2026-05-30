package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.scene.Scene;
import javafx.stage.Stage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the EditorHost can open an in-app PDF preview tab.
 */
@ExtendWith(ApplicationExtension.class)
class EditorHostPdfPreviewTest {

    private EditorHost host;

    @Start
    void start(Stage stage) {
        host = new EditorHost();
        stage.setScene(new Scene(host, 1000, 700));
        stage.show();
    }

    @Test
    void opensAPdfPreviewWithTheRightPageCount(@TempDir Path tmp) throws Exception {
        File pdf = tmp.resolve("doc.pdf").toFile();
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.addPage(new PDPage());
            doc.addPage(new PDPage());
            doc.save(pdf);
        }

        PdfPreview preview = WaitForAsyncUtils.waitForAsyncFx(3000, () -> host.openPdfPreview(pdf));
        WaitForAsyncUtils.waitForFxEvents();

        assertNotNull(preview, "a preview should be created for a readable PDF");
        assertEquals(3, preview.getPageCount());
        assertEquals(0, preview.getPageIndex(), "preview starts on the first page");
        assertNotNull(host.lookup(".fxt-pdf-preview"), "the preview must be in the scene");
    }

    @Test
    void writesSnapshotWhenRequested(@TempDir Path tmp) throws Exception {
        if (!"true".equals(System.getenv("FXT_SHELL_SNAPSHOT"))) {
            return;
        }
        File pdf = tmp.resolve("doc.pdf").toFile();
        // A content-rich PDF so the snapshot shows a real page.
        DocumentationRunner.exportPdf(new File("src/test/resources/purchageOrder.xsd"), pdf);
        PdfPreview preview = WaitForAsyncUtils.waitForAsyncFx(3000, () -> host.openPdfPreview(pdf));
        assertNotNull(preview);
        WaitForAsyncUtils.waitFor(6, java.util.concurrent.TimeUnit.SECONDS, () -> {
            var iv = (javafx.scene.image.ImageView) host.lookup(".fxt-pdf-page");
            return iv != null && iv.getImage() != null;
        });
        WaitForAsyncUtils.sleep(300, java.util.concurrent.TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
        javafx.scene.image.WritableImage img = WaitForAsyncUtils.waitForAsyncFx(3000,
                () -> host.snapshot(new javafx.scene.SnapshotParameters(), null));
        javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(img, null), "png",
                new java.io.File(System.getProperty("java.io.tmpdir"), "fxt_pdf_preview.png"));
    }

    @Test
    void returnsNullForAnUnreadablePdf(@TempDir Path tmp) throws Exception {
        File notPdf = tmp.resolve("broken.pdf").toFile();
        java.nio.file.Files.writeString(notPdf.toPath(), "this is not a pdf");
        PdfPreview preview = WaitForAsyncUtils.waitForAsyncFx(3000, () -> host.openPdfPreview(notPdf));
        assertNull(preview);
    }
}
