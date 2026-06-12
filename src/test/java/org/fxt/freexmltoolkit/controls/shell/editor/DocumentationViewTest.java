package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.stage.Stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * TestFX verification of the main-area documentation generator: full HTML
 * generation with live progress messages, option capture from the form, and
 * the language scan.
 */
@ExtendWith(ApplicationExtension.class)
class DocumentationViewTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root">
                <xs:annotation>
                  <xs:documentation xml:lang="en">An English doc.</xs:documentation>
                  <xs:documentation xml:lang="de">Eine deutsche Doku.</xs:documentation>
                </xs:annotation>
                <xs:complexType><xs:sequence>
                  <xs:element name="item" type="xs:string"/>
                </xs:sequence></xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    private DocumentationView view;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        view = new DocumentationView(new EditorHost());
        stage.setScene(new Scene(view, 1100, 750));
        stage.show();
    }

    @Test
    void generatesHtmlWithProgressMessages(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);
        Path out = tmp.resolve("docs");
        Files.createDirectories(out);

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.generate(new DocumentationView.DocOptions(xsd.toFile(), out.toFile(), "HTML",
                    true, false, false, false, false, "SVG", Set.of(), null, false, null));
            return null;
        });
        WaitForAsyncUtils.waitFor(60, TimeUnit.SECONDS,
                () -> view.getStatusText().startsWith("Generated")
                        || view.getStatusText().startsWith("ERROR"));

        assertTrue(view.getStatusText().startsWith("Generated"), view.getStatusText());
        assertTrue(new File(out.toFile(), "index.html").exists(), "HTML site must be written");
        assertFalse(view.progressMessages().isEmpty(),
                "the PROGRESS log must show the pipeline's task messages");
    }

    @Test
    void capturesTheFormOptions(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.setFiles(xsd.toFile(), tmp.toFile());
            return null;
        });
        var options = view.currentOptions();
        assertEquals("HTML", options.format(), "HTML is the default format");
        assertTrue(options.useMarkdown(), "Markdown renderer defaults to on");
        assertTrue(options.openAfter(), "open-after defaults to on");
        assertEquals("SVG", options.imageFormat());
        assertEquals(xsd.toFile(), options.xsd());
        assertNull(options.favicon(), "no favicon by default");

        Path favicon = tmp.resolve("logo.png");
        Files.writeString(favicon, "stub");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.setFavicon(favicon.toFile());
            return null;
        });
        assertEquals(favicon.toFile(), view.currentOptions().favicon(),
                "the chosen favicon must flow into the generation options");
    }

    @Test
    void scansTheDocumentationLanguages(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.setFiles(xsd.toFile(), null);
            view.scanLanguages();
            return null;
        });
        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS,
                () -> !view.currentOptions().languages().isEmpty());

        Set<String> languages = view.currentOptions().languages();
        assertTrue(languages.contains("en") && languages.contains("de"),
                "both documentation languages must be discovered: " + languages);
    }

    @Test
    void refusesToGenerateWithoutOutput(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("schema.xsd");
        Files.writeString(xsd, XSD);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
            view.generate(new DocumentationView.DocOptions(xsd.toFile(), null, "HTML",
                    true, false, false, false, false, "SVG", Set.of(), null, false, null));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(view.getStatusText().contains("output"), view.getStatusText());
    }
}
