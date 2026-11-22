package org.fxt.freexmltoolkit.screenshots;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.SpreadsheetConverterDialog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Captures the Spreadsheet Converter images for {@code docs/guides/edit-xml/index.md}: the export as a table with its
 * preview, and the import of a plain CSV file. Synthetic sample data.
 *
 * <pre>{@code
 * xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots --tests "*SpreadsheetConverterDocScreenshotGenerator*"
 * }</pre>
 */
@ExtendWith(ApplicationExtension.class)
class SpreadsheetConverterDocScreenshotGenerator {

    private static final File IMG_DIR = new File("docs/img");

    private static final String ORDERS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Orders created="2026-11-15">
              <Order id="A-1001"><Date>2026-11-02</Date><Customer><Name>Berg GmbH</Name><City>Wien</City></Customer>
                <Item><Product>Desk</Product><Qty>2</Qty></Item><Item><Product>Lamp</Product><Qty>4</Qty></Item></Order>
              <Order id="A-1002"><Date>2026-11-03</Date><Customer><Name>Ott &amp; Söhne</Name><City>Graz</City></Customer>
                <Item><Product>Chair</Product><Qty>6</Qty></Item></Order>
              <Order id="A-1003"><Date>2026-11-05</Date><Customer><Name>Huber KG</Name><City>Linz</City></Customer>
                <Item><Product>Shelf</Product><Qty>1</Qty></Item><Item><Product>Desk</Product><Qty>1</Qty></Item></Order>
              <Order id="A-1004"><Date>2026-11-07</Date><Customer><Name>Gruber OG</Name><City>Salzburg</City></Customer>
                <Item><Product>Lamp</Product><Qty>3</Qty></Item></Order>
            </Orders>
            """;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        for (String font : new String[]{"Inter-Regular", "Inter-Medium", "Inter-SemiBold", "Inter-Bold",
                "JetBrainsMono-Regular", "JetBrainsMono-Medium", "JetBrainsMono-Bold"}) {
            javafx.scene.text.Font.loadFont(getClass().getResourceAsStream("/css/fonts/" + font + ".ttf"), 10);
        }
        stage.setScene(new Scene(new StackPane(), 400, 300));
        stage.show();
    }

    @Test
    void generateSpreadsheetConverterScreenshots() throws Exception {
        IMG_DIR.mkdirs();
        SpreadsheetConverterDialog dialog = ScreenshotFx.call("spreadsheet-screenshot", () -> {
            SpreadsheetConverterDialog d = new SpreadsheetConverterDialog();
            d.setSource(() -> ORDERS);
            d.show();
            return d;
        });
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> ScreenshotFx.call("spreadsheet-screenshot",
                () -> !((TableView<?>) dialog.getDialogPane().lookup("#spreadsheet-preview-table")).getItems().isEmpty()));
        settle(600);
        write(dialog, "spreadsheet-converter-export");

        Path csv = Files.createTempDirectory("fxt-shot").resolve("customers.csv");
        Files.writeString(csv, "Customer no;Name;City;@status\n1001;Berg GmbH;Wien;active\n1002;Ott & Söhne;Graz;active\n"
                + "1003;Huber KG;Linz;paused\n");
        ScreenshotFx.run("spreadsheet-screenshot", () -> {
            dialog.setDirection(SpreadsheetConverterDialog.Direction.SPREADSHEET_TO_XML);
            dialog.setActiveInput(csv.toFile());
            ((javafx.scene.control.TextField) dialog.getDialogPane().lookup("#spreadsheet-root-name")).setText("Customers");
            ((javafx.scene.control.TextField) dialog.getDialogPane().lookup("#spreadsheet-record-name")).setText("Customer");
        });
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> ScreenshotFx.call("spreadsheet-screenshot",
                () -> ((TextArea) dialog.getDialogPane().lookup("#spreadsheet-preview-xml")).getText().contains("<Customer ")));
        settle(600);
        write(dialog, "spreadsheet-converter-import");
        ScreenshotFx.run("spreadsheet-screenshot", dialog::close);
    }

    private void settle(long millis) {
        WaitForAsyncUtils.sleep(millis, TimeUnit.MILLISECONDS);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void write(SpreadsheetConverterDialog dialog, String name) throws Exception {
        var image = ScreenshotFx.call("spreadsheet-screenshot",
                () -> dialog.getDialogPane().snapshot(new SnapshotParameters(), null));
        File out = new File(IMG_DIR, name + ".png");
        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", out);
        System.out.println("[spreadsheet-screenshot] wrote " + out.getAbsolutePath());
    }
}
