package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javafx.scene.Scene;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Verifies the Explorer's one-click Schematron validation, which is a context action on the
 * selected files (UX plan 3.1): the sticky Schematron, the "Validate with …" entries and the
 * delegation to the shell-wired callback.
 */
@ExtendWith(ApplicationExtension.class)
class ExplorerPanelSchematronTest {

    private EditorHost host;
    private ExplorerPanel panel;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        // A clean recent store so name/menu assertions are deterministic.
        org.fxt.freexmltoolkit.service.PropertiesServiceImpl.getInstance()
                .clearRecentSchematronFiles();
        host = new EditorHost();
        panel = new ExplorerPanel(host);
        stage.setScene(new Scene(new HBox(host, panel), 1100, 600));
        stage.show();
    }

    @Test
    void contextMenuNamesTheSchematronThatWillBeUsed(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("active.xml");
        Files.writeString(xml, "<root/>");
        Path sch = tmp.resolve("rules.sch");
        Files.writeString(sch, "<x/>");
        openActive(xml);

        List<String> before = WaitForAsyncUtils.waitForAsyncFx(2000, this::contextTexts);
        assertTrue(before.contains("Validate with Schematron…"), "unset: the entry asks for one: " + before);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.useSchematron(sch.toFile());
            return null;
        });
        assertEquals("rules.sch", panel.currentSchematronName());
        List<String> after = WaitForAsyncUtils.waitForAsyncFx(2000, this::contextTexts);
        assertTrue(after.contains("Validate with rules.sch"), after.toString());
        assertTrue(after.contains("Transform with XSLT…") || after.stream().anyMatch(t -> t.startsWith("Transform with ")),
                "the transform context action sits next to it: " + after);
        assertEquals(sch.toFile(), host.getActiveSchematron(), "using a Schematron binds it to the active document");
    }

    @Test
    void otherSchematronSubmenuOffersRecentsFavoritesChooseAndClear(@TempDir Path tmp) throws Exception {
        Path sch = tmp.resolve("rules.sch");
        Files.writeString(sch, "<x/>");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.useSchematron(sch.toFile());
            return null;
        });
        List<String> texts = WaitForAsyncUtils.waitForAsyncFx(2000, () -> panel.schematronMenuItemTexts());
        assertEquals("rules.sch", texts.get(0), "the recent Schematron comes first: " + texts);
        assertTrue(texts.containsAll(List.of("Favorites", "Choose Schematron…", "Clear recent")), texts.toString());

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.clearRecentSchematron();
            return null;
        });
        assertNull(panel.currentSchematronName(), "clearing the recents resets the sticky choice");
    }

    @Test
    void validateActionDelegatesActiveDocumentAndSchematron(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("active.xml");
        Files.writeString(xml, "<root/>");
        Path sch = tmp.resolve("rules.sch");
        Files.writeString(sch, "<x/>");

        AtomicReference<List<File>> gotFiles = new AtomicReference<>();
        AtomicReference<File> gotSchematron = new AtomicReference<>();
        panel.setSchematronValidateAction((files, schematron) -> {
            gotFiles.set(files);
            gotSchematron.set(schematron);
        });

        openActive(xml);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            panel.useSchematron(sch.toFile());
            panel.runValidationOnSelection();
            return null;
        });

        assertNotNull(gotFiles.get(), "Validate must invoke the shell-wired action");
        assertEquals(List.of(xml.toFile()), gotFiles.get(),
                "with no tree selection the active document is the fallback input");
        assertEquals(sch.toFile(), gotSchematron.get());
    }

    private void openActive(Path xml) throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));
    }

    private List<String> contextTexts() {
        return panel.fileContextMenuItems().stream().map(MenuItem::getText)
                .filter(java.util.Objects::nonNull).toList();
    }
}
