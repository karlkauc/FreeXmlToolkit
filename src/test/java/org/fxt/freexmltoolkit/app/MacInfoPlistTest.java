package org.fxt.freexmltoolkit.app;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * The macOS bundle's Info.plist template (release/macos/Info.plist, handed to jpackage via
 * resource-dir): the application must claim the system types macOS assigns to .xml and .json, and
 * declare its own types for the other extensions as imported types building on public.xml. Exported
 * own types for .xml (what jpackage's file associations produced) are ignored by Launch Services, so
 * "Open with" did not hand XML files to the application.
 */
class MacInfoPlistTest {

    private static final Path PLIST = Path.of("release/macos/Info.plist");

    private static String text;
    private static Document plist;
    private static final XPath XPATH = XPathFactory.newInstance().newXPath();

    @BeforeAll
    static void parse() throws Exception {
        text = Files.readString(PLIST);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // the Apple DTD is declared but never fetched
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setExpandEntityReferences(false);
        plist = factory.newDocumentBuilder().parse(PLIST.toFile());
    }

    private static List<String> strings(String xpath) throws Exception {
        NodeList nodes = (NodeList) XPATH.evaluate(xpath, plist, XPathConstants.NODESET);
        List<String> values = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            values.add(nodes.item(i).getTextContent().strip());
        }
        return values;
    }

    /** The array or value following {@code <key>name</key>} inside the given dict expression. */
    private static String value(String dict, String key) {
        return dict + "/key[.='" + key + "']/following-sibling::*[1]";
    }

    @Test
    void claimsTheSystemTypesOfXmlAndJson() throws Exception {
        List<String> handled = strings(value("/plist/dict", "CFBundleDocumentTypes")
                + "/dict/key[.='LSItemContentTypes']/following-sibling::array[1]/string");
        assertTrue(handled.contains("public.xml"), handled.toString());
        assertTrue(handled.contains("public.json"), handled.toString());
    }

    @Test
    void ownTypesAreImportedAndBuildOnPublicXml() throws Exception {
        String imported = value("/plist/dict", "UTImportedTypeDeclarations") + "/dict";
        Map<String, String> typeByExtension = Map.of("xsd", "org.fxt.freexmltoolkit.xsd",
                "xsl", "org.fxt.freexmltoolkit.xslt", "xslt", "org.fxt.freexmltoolkit.xslt",
                "sch", "org.fxt.freexmltoolkit.schematron", "schematron", "org.fxt.freexmltoolkit.schematron");
        List<String> handled = strings(value("/plist/dict", "CFBundleDocumentTypes")
                + "/dict/key[.='LSItemContentTypes']/following-sibling::array[1]/string");
        for (var entry : typeByExtension.entrySet()) {
            String type = imported + "[key[.='UTTypeIdentifier']/following-sibling::string[1]='" + entry.getValue() + "']";
            assertEquals(List.of("public.xml"), strings(value(type, "UTTypeConformsTo") + "/string"), entry.getValue());
            assertTrue(strings(value(type, "UTTypeTagSpecification")
                    + "/key[.='public.filename-extension']/following-sibling::array[1]/string")
                    .contains(entry.getKey()), entry.getKey());
            assertTrue(handled.contains(entry.getValue()), "a document type for " + entry.getValue());
        }
    }

    @Test
    void declaresNoExportedTypesAndNoAppleOnlyKeys() {
        assertFalse(text.contains("UTExportedTypeDeclarations"), "own exported types for .xml are ignored by macOS");
        assertFalse(text.contains("LSIsAppleDefaultForType"), "only honoured for Apple's applications");
        assertFalse(text.contains("DEPLOY_FILE_ASSOCIATIONS"), "file associations come from this file only");
    }

    @Test
    void keepsJpackagesPlaceholders() {
        for (String placeholder : List.of("DEPLOY_LAUNCHER_NAME", "DEPLOY_ICON_FILE", "DEPLOY_BUNDLE_IDENTIFIER",
                "DEPLOY_BUNDLE_NAME", "DEPLOY_BUNDLE_SHORT_VERSION", "DEPLOY_BUNDLE_CFBUNDLE_VERSION",
                "DEPLOY_BUNDLE_COPYRIGHT", "DEPLOY_APP_CATEGORY")) {
            assertTrue(text.contains(placeholder), placeholder);
        }
    }
}
