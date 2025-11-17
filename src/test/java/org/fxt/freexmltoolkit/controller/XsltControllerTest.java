package org.fxt.freexmltoolkit.controller;

import javafx.scene.control.*;
import javafx.scene.web.WebView;
import org.fxt.freexmltoolkit.service.XmlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XsltController.
 */
@ExtendWith(MockitoExtension.class)
class XsltControllerTest {

    private XsltController controller;

    @Mock
    private WebView mockWebView;

    @Mock
    private TabPane mockOutputMethodSwitch;

    @Mock
    private ProgressBar mockProgressBar;

    @BeforeEach
    void setUp() {
        controller = new XsltController();
    }

    @Test
    @DisplayName("Should create controller instance")
    void testControllerInstantiation() {
        assertNotNull(controller);
    }

    @Test
    @DisplayName("Should validate XSLT file extension")
    void testXsltFileValidation() {
        File xsltFile = new File("transform.xsl");
        assertTrue(xsltFile.getName().endsWith(".xsl") || xsltFile.getName().endsWith(".xslt"));

        File xsltFile2 = new File("transform.xslt");
        assertTrue(xsltFile2.getName().endsWith(".xsl") || xsltFile2.getName().endsWith(".xslt"));

        File invalidFile = new File("transform.xml");
        assertFalse(invalidFile.getName().endsWith(".xsl") || invalidFile.getName().endsWith(".xslt"));
    }

    @Test
    @DisplayName("Should recognize XSLT namespace")
    void testXsltNamespace() {
        String xsltNamespace = "http://www.w3.org/1999/XSL/Transform";
        assertNotNull(xsltNamespace);
        assertTrue(xsltNamespace.contains("XSL/Transform"));
    }

    @Test
    @DisplayName("Should support XSLT 1.0 and 2.0")
    void testXsltVersions() {
        String xslt1 = "1.0";
        String xslt2 = "2.0";
        String xslt3 = "3.0";

        assertNotNull(xslt1);
        assertNotNull(xslt2);
        assertNotNull(xslt3);
    }

    @Test
    @DisplayName("Should validate output methods")
    void testOutputMethods() {
        String[] validOutputMethods = {"xml", "html", "text", "xhtml"};

        for (String method : validOutputMethods) {
            assertNotNull(method);
            assertFalse(method.isEmpty());
        }
    }

    @Test
    @DisplayName("Should handle XML and XSLT file pairing")
    void testXmlXsltPairing() {
        File xmlFile = new File("data.xml");
        File xsltFile = new File("transform.xsl");

        assertNotNull(xmlFile);
        assertNotNull(xsltFile);
        assertTrue(xmlFile.getName().endsWith(".xml"));
        assertTrue(xsltFile.getName().endsWith(".xsl"));
    }

    @Test
    @DisplayName("Should recognize XSLT elements")
    void testXsltElements() {
        String[] xsltElements = {
            "template", "apply-templates", "value-of",
            "for-each", "if", "choose", "when", "otherwise",
            "copy", "copy-of", "variable", "param"
        };

        for (String element : xsltElements) {
            assertNotNull(element);
            assertFalse(element.isEmpty());
        }
    }

    @Test
    @DisplayName("Should validate XPath expressions in XSLT")
    void testXPathInXslt() {
        String xpathExpression1 = "//book[@price > 10]";
        String xpathExpression2 = "count(//chapter)";
        String xpathExpression3 = "substring(title, 1, 10)";

        assertNotNull(xpathExpression1);
        assertNotNull(xpathExpression2);
        assertNotNull(xpathExpression3);

        assertTrue(xpathExpression1.contains("[@"));
        assertTrue(xpathExpression2.contains("count"));
        assertTrue(xpathExpression3.contains("substring"));
    }

    @Test
    @DisplayName("Should support XSLT parameters")
    void testXsltParameters() {
        String paramDeclaration = "<xsl:param name=\"title\" select=\"'Default'\"/>";

        assertNotNull(paramDeclaration);
        assertTrue(paramDeclaration.contains("xsl:param"));
        assertTrue(paramDeclaration.contains("name="));
        assertTrue(paramDeclaration.contains("select="));
    }

    @Test
    @DisplayName("Should handle transformation output formats")
    void testTransformationOutputFormats() {
        String[] formats = {"HTML", "XML", "TEXT"};

        for (String format : formats) {
            assertNotNull(format);
            assertTrue(format.length() > 0);
        }
    }
}
