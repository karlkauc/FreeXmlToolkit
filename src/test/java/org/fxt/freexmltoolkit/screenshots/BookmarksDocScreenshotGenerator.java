package org.fxt.freexmltoolkit.screenshots;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.Activity;
import org.fxt.freexmltoolkit.controls.shell.UnifiedShellView;
import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.service.bookmarks.BookmarkService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Writes {@code docs/img/unified-shell-bookmarks.png} (+ dark): a long XML document with plain, named,
 * numbered and orphaned bookmarks in the gutter, their markers over the scroll bar and the Bookmarks
 * panel. Run with the {@code docScreenshots} task (see {@link ShellDocScreenshotGenerator}).
 */
@ExtendWith(ApplicationExtension.class)
class BookmarksDocScreenshotGenerator {

    private static final File IMG_DIR = new File("docs/img");

    private Parent root;

    @Start
    void start(Stage stage) throws Exception {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                .set(org.fxt.freexmltoolkit.controls.shell.editor.FundsXmlHint.SHOWN_KEY, "true");
        for (String font : new String[]{"Inter-Regular", "Inter-Medium", "Inter-SemiBold", "Inter-Bold",
                "JetBrainsMono-Regular", "JetBrainsMono-Medium", "JetBrainsMono-Bold"}) {
            javafx.scene.text.Font.loadFont(getClass().getResourceAsStream("/css/fonts/" + font + ".ttf"), 10);
        }
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/pages/tab_unified_shell.fxml"));
        root = loader.load();
        stage.setScene(new Scene(root, 1400, 860));
        stage.setX(0);
        stage.setY(0);
        stage.show();
    }

    @Test
    void generateBookmarkScreenshots() throws Exception {
        IMG_DIR.mkdirs();
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> root.lookup(".fxt-shell") != null);
        UnifiedShellView shell = (UnifiedShellView) root.lookup(".fxt-shell");
        EditorHost host = shell.getEditorHost();
        onFx(() -> org.fxt.freexmltoolkit.controls.shell.ThemeManager.apply(root.getScene(), false));

        Path dir = DocFiles.work("bookmarks");
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<orders>\n");
        for (int i = 1; i <= 60; i++) {
            xml.append("  <order id=\"").append(1000 + i).append("\">\n")
                    .append("    <customer>Customer ").append(i).append("</customer>\n")
                    .append("    <total currency=\"EUR\">").append(i * 17 % 500).append(".00</total>\n")
                    .append("  </order>\n");
        }
        xml.append("</orders>\n");
        Path orders = dir.resolve("orders.xml");
        Files.writeString(orders, xml);
        Path invoice = dir.resolve("invoice.xsl");
        Files.writeString(invoice, "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\">\n"
                + "  <xsl:template match=\"/\">\n    <invoice/>\n  </xsl:template>\n</xsl:stylesheet>\n");

        BookmarkService service = BookmarkService.getInstance();
        String key = BookmarkService.keyOf(orders);
        service.toggle(key, 3, "<order id=\"1001\">");
        service.rename(key, 3, "First order");
        service.toggle(key, 8, "<customer>Customer 2</customer>");
        service.setNumber(key, 12, 1, "<total currency=\"EUR\">51.00</total>");
        service.rename(key, 12, "Check this total");
        service.toggle(key, 120, "<order id=\"1030\">");
        service.rename(key, 120, "Middle");
        service.toggle(key, 200, "<order id=\"9999\">"); // not in the file: orphaned once opened
        service.toggle(key, 230, "<order id=\"1058\">");
        String xslKey = BookmarkService.keyOf(invoice);
        service.setNumber(xslKey, 2, 2, "<xsl:template match=\"/\">");
        service.rename(xslKey, 2, "Root template");

        try {
            onFx(() -> host.openFile(invoice));
            WaitForAsyncUtils.waitFor(8, TimeUnit.SECONDS, () -> host.getActiveText().map(t -> t.contains("invoice")).orElse(false));
            onFx(() -> host.openFile(orders));
            WaitForAsyncUtils.waitFor(8, TimeUnit.SECONDS, () -> host.getActiveText().map(t -> t.contains("Customer 60")).orElse(false));
            onFx(() -> shell.getSelectionModel().select(Activity.BOOKMARKS));
            onFx(() -> host.goToLine(12));
            settle(1200);
            shot(shell, "unified-shell-bookmarks");
            onFx(() -> org.fxt.freexmltoolkit.controls.shell.ThemeManager.apply(root.getScene(), true));
            settle(800);
            shot(shell, "unified-shell-bookmarks-dark");
            onFx(() -> org.fxt.freexmltoolkit.controls.shell.ThemeManager.apply(root.getScene(), false));
        } finally {
            service.clear(key);
            service.clear(xslKey);
        }
    }

    private void onFx(Runnable action) {
        ScreenshotFx.run("bookmarks-screenshot", action);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static void settle(long millis) {
        WaitForAsyncUtils.sleep(millis, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static void shot(javafx.scene.Node target, String name) throws Exception {
        var img = ScreenshotFx.call("bookmarks-screenshot", () -> target.snapshot(new SnapshotParameters(), null));
        File out = new File(IMG_DIR, name + ".png");
        ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out);
        System.out.println("[bookmarks-screenshot] wrote " + out.getAbsolutePath());
    }
}
