package org.fxt.freexmltoolkit.service;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

/**
 * A service to flatten an XSD file by resolving all <xs:include> statements.
 */
public class XsdFlattenerService {

    private static final String XML_SCHEMA_NS = "http://www.w3.org/2001/XMLSchema";

    /**
     * Flattens an XSD file by resolving all its <xs:include> directives.
     * The process is repeated until no more include tags are found.
     *
     * @param sourceXsd      The main XSD file to flatten.
     * @param destinationXsd The file where the flattened XSD will be saved.
     * @return A string representation of the flattened XSD.
     * @throws Exception if any error occurs during parsing or transformation.
     */
    public String flatten(File sourceXsd, File destinationXsd) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true); // Important for handling namespaces like 'xs:'
        DocumentBuilder builder = factory.newDocumentBuilder();

        Document mainDoc = builder.parse(sourceXsd);
        Element schemaRoot = mainDoc.getDocumentElement();

        // Repeatedly process includes until none are left. This handles nested includes.
        while (processIncludes(schemaRoot, sourceXsd.getParentFile(), builder)) ;

        // Convert the modified DOM document back to a string
        String flattenedContent = toXmlString(mainDoc);

        // Save the flattened content to the destination file
        Files.writeString(destinationXsd.toPath(), flattenedContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        return flattenedContent;
    }

    /**
     * Finds and processes one level of <xs:include> elements within a schema root.
     *
     * @param schemaRoot The <xs:schema> root element.
     * @param baseDir    The base directory for resolving relative schema locations.
     * @param builder    The DocumentBuilder to parse included files.
     * @return true if includes were found and processed, false otherwise.
     * @throws Exception on parsing errors.
     */
    private boolean processIncludes(Element schemaRoot, File baseDir, DocumentBuilder builder) throws Exception {
        NodeList includeNodes = schemaRoot.getElementsByTagNameNS(XML_SCHEMA_NS, "include");
        if (includeNodes.getLength() == 0) {
            return false; // No more includes to process
        }

        // Process the first found include element. The while-loop in the public method will re-run this.
        Element includeElement = (Element) includeNodes.item(0);
        String location = includeElement.getAttribute("schemaLocation");
        if (location.isBlank()) {
            // If location is missing, just remove the tag and continue.
            schemaRoot.removeChild(includeElement);
            return true;
        }

        File includedFile = new File(baseDir, location);
        if (!includedFile.exists()) {
            throw new Exception("Included XSD file not found: " + includedFile.getAbsolutePath());
        }

        // Parse the included document
        Document includedDoc = builder.parse(includedFile);
        Element includedSchemaRoot = includedDoc.getDocumentElement();

        // Import all top-level elements from the included schema into the main document
        NodeList childrenToImport = includedSchemaRoot.getChildNodes();
        for (int i = 0; i < childrenToImport.getLength(); i++) {
            Node child = childrenToImport.item(i);
            // We only care about element nodes (like <xs:complexType>, <xs:simpleType>, etc.)
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                // Import the node into the main document's context
                Node importedNode = schemaRoot.getOwnerDocument().importNode(child, true);
                // Insert the imported node *before* the <xs:include> tag
                schemaRoot.insertBefore(importedNode, includeElement);
            }
        }

        // Finally, remove the processed <xs:include> element
        schemaRoot.removeChild(includeElement);

        return true; // Indicates that an include was processed
    }

    /**
     * Converts a DOM Document to a formatted XML string.
     *
     * @param doc The document to convert.
     * @return A formatted XML string.
     * @throws Exception on transformation errors.
     */
    private String toXmlString(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        // This prevents a security warning in newer JDKs
        tf.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.setOutputProperty("{http://apache.org/xml/features/disallow-doctype-decl", "false");

        Writer out = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(out));
        return out.toString();
    }
}