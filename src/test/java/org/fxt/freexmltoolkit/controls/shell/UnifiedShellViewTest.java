package org.fxt.freexmltoolkit.controls.shell;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.Start;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.util.WaitForAsyncUtils;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Renders the {@link UnifiedShellView} skeleton headlessly (Monocle) and verifies
 * its structure wires up against the design tokens. When run with
 * {@code -Dfxt.shell.snapshot=true} it also writes light/dark PNGs to the system
 * temp dir (used for review checkpoints); the normal suite skips image output.
 */
@ExtendWith(ApplicationExtension.class)
class UnifiedShellViewTest {

    private UnifiedShellView shell;
    private Scene scene;

    @Start
    void start(Stage stage) {
        shell = new UnifiedShellView();
        scene = new Scene(shell, 1280, 800);
        scene.getStylesheets().addAll(
                getClass().getResource("/css/design-tokens.css").toExternalForm(),
                getClass().getResource("/css/unified-shell.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    @Test
    void activityBarRendersOneButtonPerActivity() {
        WaitForAsyncUtils.waitForFxEvents();
        Set<javafx.scene.Node> buttons = shell.lookupAll(".fxt-activity-button");
        assertEquals(Activity.values().length, buttons.size(),
                "Activity Bar must render one button per activity");
    }

    @Test
    void inspectorRendersTheFourRequiredSections() {
        WaitForAsyncUtils.waitForFxEvents();
        Set<javafx.scene.Node> sections = shell.lookupAll(".fxt-inspector-section");
        assertEquals(4, sections.size(), "Inspector must show the four required sections");
        boolean hasNodeXpath = sections.stream()
                .map(n -> ((TitledPane) n).getText())
                .anyMatch("Node & XPath"::equals);
        assertTrue(hasNodeXpath, "Inspector must contain the 'Node & XPath' section");
    }

    @Test
    void selectingAnActivitySwapsTheSidePanel() {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> shell.getSelectionModel().select(Activity.SCHEMA));
        WaitForAsyncUtils.waitForFxEvents();
        Label title = (Label) shell.lookup(".fxt-side-panel-title");
        assertNotNull(title, "side panel title must exist");
        assertEquals("SCHEMA", title.getText(), "side panel must follow the active activity");
    }

    @Test
    void writesLightAndDarkSnapshotsWhenRequested() throws Exception {
        if (!Boolean.getBoolean("fxt.shell.snapshot")
                && !"true".equals(System.getenv("FXT_SHELL_SNAPSHOT"))) {
            return; // image output is opt-in; keep the normal suite side-effect free
        }
        File dir = new File(System.getProperty("java.io.tmpdir"));

        // Open a real XML document so the snapshot shows the editor in context.
        File sample = File.createTempFile("fxt_shell_sample", ".xml");
        java.nio.file.Files.writeString(sample.toPath(),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<FundsXML4 xmlns=\"http://www.fundsxml.org\">\n"
                        + "    <ControlData>\n"
                        + "        <UniqueDocumentID>EAM_FUND_2024_0926</UniqueDocumentID>\n"
                        + "        <DocumentGenerated>2024-09-26T18:37:12</DocumentGenerated>\n"
                        + "    </ControlData>\n"
                        + "</FundsXML4>\n");
        WaitForAsyncUtils.waitForAsyncFx(3000, () -> shell.getEditorHost().openFile(sample.toPath()));
        WaitForAsyncUtils.waitFor(3, java.util.concurrent.TimeUnit.SECONDS,
                () -> shell.getEditorHost().getActiveText().map(t -> t.contains("FundsXML4")).orElse(false));
        WaitForAsyncUtils.waitForFxEvents();
        snapshot(new File(dir, "fxt_shell_light.png"));

        WaitForAsyncUtils.waitForAsyncFx(3000, () -> {
            shell.getStyleClass().add("fxt-theme-dark");
            shell.applyCss();
            ((Region) scene.getRoot()).layout();
        });
        snapshot(new File(dir, "fxt_shell_dark.png"));
    }

    private void snapshot(File target) throws Exception {
        WritableImage image = WaitForAsyncUtils.waitForAsyncFx(5000, () -> {
            shell.applyCss();
            shell.layout();
            return scene.snapshot(null);
        });
        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", target);
        assertTrue(target.exists() && target.length() > 0, "snapshot should be written: " + target);
    }
}
