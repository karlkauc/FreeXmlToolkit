package org.fxt.freexmltoolkit.controls.shell.inspector;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.shell.editor.EditorHost;
import org.fxt.freexmltoolkit.controls.shell.editor.ViewMode;
import org.fxt.freexmltoolkit.service.FavoritesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The inspector laid out after Figma "03 · Unified Editor" (node 33:2), on the FundsXML 3.0.6
 * fixture with its XSD bound and the caret in the Text view: node header subtitle and icon,
 * Namespace / Position rows, schema base type + derivation, Order, the value check with example
 * chips (a chip click fixes the value), USED IN, the favourite star, and an XSD node's facts.
 */
@ExtendWith(ApplicationExtension.class)
class InspectorFigmaLayoutTest {

    private static final File XML = new File("src/test/resources/FundsXML_306.xml");
    private static final File XSD = new File("src/test/resources/FundsXML_306.xsd");

    private EditorHost host;
    private InspectorPanel inspector;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        inspector = new InspectorPanel(host);
        stage.setScene(new Scene(new HBox(host, inspector), 1300, 900));
        stage.show();
    }

    private void openFundsXmlWithSchema() throws Exception {
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(XML.toPath()));
        WaitForAsyncUtils.waitFor(8, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("TypeOfDataSupplier")).orElse(false));
        // Binding the ~1.4 MB XSD takes a while on the FX thread: fire it; the callers wait for
        // the schema facts to surface.
        WaitForAsyncUtils.asyncFx(() -> host.setSchemaForActiveDocument(XSD));
    }

    @Test
    void schemaBoundLeafElementShowsTheFigmaFacts() throws Exception {
        openFundsXmlWithSchema();
        // Line 13: <TypeOfDataSupplier>Vendor</TypeOfDataSupplier> — an enumeration (KAG, AKAG, OeKB, VENDOR).
        // The XSD provider loads off-thread; once it is there the inspector picks the caret node up
        // again on its own (no further caret move).
        WaitForAsyncUtils.asyncFx(() -> host.goToLine(13, 30));
        WaitForAsyncUtils.waitFor(40, TimeUnit.SECONDS,
                () -> "TypeOfDataSupplier".equals(inspector.getNodeNameText()) && !inspector.getOrderText().isEmpty()
                        && inspector.getUsedInCount() == 1 && inspector.getExampleValueCount() == 4);
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("Element · xs:string", inspector.getHeaderSubtitleText());
        assertEquals("bi-code-slash", inspector.getHeaderIconLiteral());
        assertTrue(inspector.getNamespaceText().endsWith("(default)"), inspector.getNamespaceText());
        assertTrue(inspector.getPositionText().startsWith("Ln 13 · Col"), inspector.getPositionText());
        assertEquals("xs:string", inspector.getBaseTypeText());
        assertEquals("restriction", inspector.getDerivationText());
        assertEquals("3 of 3 · sequence", inspector.getOrderText());
        assertEquals(1, inspector.getUsedInCount(), "the containing DataSupplier element");

        // "Vendor" is not one of the enumerated values: the check warns, the chips offer them.
        assertFalse(inspector.isValueCheckOk());
        assertTrue(inspector.getValueCheckText().contains("VENDOR"), inspector.getValueCheckText());
        assertEquals(4, inspector.getExampleValueCount());
        Button vendor = WaitForAsyncUtils.waitForAsyncFx(2000, () -> inspector.lookupAll(".fxt-inspector-chip").stream()
                .map(n -> (Button) n).filter(b -> "VENDOR".equals(b.getText())).findFirst().orElseThrow());
        WaitForAsyncUtils.waitForAsyncFx(2000, vendor::fire);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.getActiveText()
                .map(t -> t.contains("<TypeOfDataSupplier>VENDOR</TypeOfDataSupplier>")).orElse(false));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, inspector::isValueCheckOk);
        assertEquals("Value matches the schema facets", inspector.getValueCheckText());
    }

    @Test
    void positionIsHiddenOutsideTheTextView() throws Exception {
        openFundsXmlWithSchema();
        WaitForAsyncUtils.asyncFx(() -> host.goToLine(11, 17));
        WaitForAsyncUtils.waitFor(40, TimeUnit.SECONDS, () -> "Short".equals(inspector.getNodeNameText()));
        assertFalse(inspector.getPositionText().isEmpty());
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.setActiveViewMode(ViewMode.TREE));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> inspector.getPositionText().isEmpty());
    }

    @Test
    void theStarTogglesTheActiveFileAsFavourite(@TempDir Path home) throws Exception {
        String oldHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        FavoritesService.resetInstanceForTests();
        try {
            WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(XML.toPath()));
            WaitForAsyncUtils.waitFor(8, TimeUnit.SECONDS, () -> host.getActiveText().isPresent());
            Button star = (Button) inspector.lookup("#inspector-favorite");
            assertFalse(star.isDisabled(), "a saved file can be starred");
            assertFalse(inspector.isFavoriteStarActive());

            WaitForAsyncUtils.waitForAsyncFx(2000, star::fire);
            assertTrue(inspector.isFavoriteStarActive());
            assertTrue(FavoritesService.getInstance().isFavorite(XML.getAbsolutePath()));

            WaitForAsyncUtils.waitForAsyncFx(2000, star::fire);
            assertFalse(inspector.isFavoriteStarActive());
            assertFalse(FavoritesService.getInstance().isFavorite(XML.getAbsolutePath()));
        } finally {
            System.setProperty("user.home", oldHome);
            FavoritesService.resetInstanceForTests();
        }
    }

    @Test
    void anXsdTypeShowsItsIconAndUsages(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("types.xsd");
        java.nio.file.Files.writeString(xsd, """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:t" xmlns="urn:t">
                  <xs:complexType name="PartyType">
                    <xs:sequence><xs:element name="name" type="xs:string"/></xs:sequence>
                  </xs:complexType>
                  <xs:element name="root">
                    <xs:complexType><xs:sequence>
                      <xs:element name="buyer" type="PartyType"/>
                      <xs:element name="seller" type="PartyType"/>
                    </xs:sequence></xs:complexType>
                  </xs:element>
                </xs:schema>
                """);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xsd));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
                () -> host.getActiveText().map(t -> t.contains("PartyType")).orElse(false));
        WaitForAsyncUtils.waitForAsyncFx(5000, () -> host.setActiveViewMode(ViewMode.TREE));
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> {
            WaitForAsyncUtils.asyncFx(() -> host.revealSchemaNodeByXPath("/xs:schema/xs:complexType[@name='PartyType']"));
            Thread.sleep(400);
            return "PartyType".equals(inspector.getNodeNameText()) && inspector.getUsedInCount() == 2;
        });
        assertEquals("bi-diagram-3", inspector.getHeaderIconLiteral());
        assertEquals("Complex type", inspector.getHeaderSubtitleText());
        assertEquals("urn:t", inspector.getNamespaceText());
        Label name = (Label) inspector.lookup(".fxt-inspector-usage-name");
        assertNotNull(name);
    }
}
