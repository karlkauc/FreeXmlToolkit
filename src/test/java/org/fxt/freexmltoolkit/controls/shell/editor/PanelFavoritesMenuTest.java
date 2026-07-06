package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.domain.FileFavorite;
import org.fxt.freexmltoolkit.service.FavoritesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Every file-pick row in the side panels must offer the favorites star menu:
 * Schematron in the Validation panel, XSL/XML in the FOP panel, keystore and
 * trust store in the Signature panel (parity with the XSD/XSLT rows that
 * already had it). Also covers the new KEYSTORE favorite type mapping.
 */
@ExtendWith(ApplicationExtension.class)
class PanelFavoritesMenuTest {

    private EditorHost host;
    private ValidationPanel validationPanel;
    private FopPanel fopPanel;
    private SignaturePanel signaturePanel;

    @Start
    void start(Stage stage) {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
        host = new EditorHost();
        validationPanel = new ValidationPanel(host);
        fopPanel = new FopPanel(host);
        signaturePanel = new SignaturePanel(host);
        stage.setScene(new Scene(new HBox(host, validationPanel, fopPanel, signaturePanel), 1400, 700));
        stage.show();
    }

    @Test
    void keystoreExtensionsMapToTheKeystoreFavoriteType() {
        assertEquals(FileFavorite.FileType.KEYSTORE, FileFavorite.FileType.fromExtension("signer.jks"));
        assertEquals(FileFavorite.FileType.KEYSTORE, FileFavorite.FileType.fromExtension("signer.p12"));
        assertEquals(FileFavorite.FileType.KEYSTORE, FileFavorite.FileType.fromExtension("signer.pfx"));
        assertEquals(FileFavorite.FileType.KEYSTORE, FileFavorite.FileType.fromExtension("signer.keystore"));
    }

    @Test
    void schematronRowListsSchematronFavorites(@TempDir Path tmp) throws Exception {
        Path sch = write(tmp, "InvoiceRules.sch", "<schema xmlns=\"http://purl.oclc.org/dsdl/schematron\"/>");
        FavoritesService svc = FavoritesService.getInstance();
        try {
            svc.addFavorite(sch.toAbsolutePath().toString(), "InvoiceRules", null);
            List<String> names = WaitForAsyncUtils.waitForAsyncFx(2000,
                    () -> validationPanel.schematronFavoriteNames());
            assertTrue(names.stream().anyMatch(n -> n.contains("InvoiceRules")),
                    "Schematron star menu must list the favorite, was: " + names);
        } finally {
            svc.removeFavoriteByPath(sch.toAbsolutePath().toString());
        }
    }

    @Test
    void fopRowsListXsltAndXmlFavorites(@TempDir Path tmp) throws Exception {
        Path xsl = write(tmp, "invoice-fo.xsl", "<xsl:stylesheet version=\"2.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"/>");
        Path xml = write(tmp, "invoice.xml", "<invoice/>");
        FavoritesService svc = FavoritesService.getInstance();
        try {
            svc.addFavorite(xsl.toAbsolutePath().toString(), "InvoiceFo", null);
            svc.addFavorite(xml.toAbsolutePath().toString(), "InvoiceData", null);
            List<String> xslNames = WaitForAsyncUtils.waitForAsyncFx(2000, () -> fopPanel.xslFavoriteNames());
            List<String> xmlNames = WaitForAsyncUtils.waitForAsyncFx(2000, () -> fopPanel.xmlFavoriteNames());
            assertTrue(xslNames.stream().anyMatch(n -> n.contains("InvoiceFo")),
                    "FOP stylesheet star menu must list the XSLT favorite, was: " + xslNames);
            assertTrue(xmlNames.stream().anyMatch(n -> n.contains("InvoiceData")),
                    "FOP input star menu must list the XML favorite, was: " + xmlNames);
            assertFalse(xslNames.stream().anyMatch(n -> n.contains("InvoiceData")),
                    "cross-type isolation: XML favorites must not appear in the XSL menu");
        } finally {
            svc.removeFavoriteByPath(xsl.toAbsolutePath().toString());
            svc.removeFavoriteByPath(xml.toAbsolutePath().toString());
        }
    }

    @Test
    void keystoreRowListsKeystoreFavoritesAndPickingBindsIt(@TempDir Path tmp) throws Exception {
        Path jks = write(tmp, "signer.jks", "stub");
        FavoritesService svc = FavoritesService.getInstance();
        try {
            svc.addFavorite(jks.toAbsolutePath().toString(), "SignerKeystore", null);
            List<String> names = WaitForAsyncUtils.waitForAsyncFx(2000,
                    () -> signaturePanel.keystoreFavoriteNames());
            assertTrue(names.stream().anyMatch(n -> n.contains("SignerKeystore")),
                    "keystore star menu must list the favorite, was: " + names);

            WaitForAsyncUtils.waitForAsyncFx(2000, () -> {
                signaturePanel.setKeystore(jks.toFile());
                return null;
            });
            assertEquals(jks.toFile(), signaturePanel.currentKeystoreFile());
        } finally {
            svc.removeFavoriteByPath(jks.toAbsolutePath().toString());
        }
    }

    private Path write(Path dir, String name, String content) throws Exception {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
