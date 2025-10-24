package org.fxt.freexmltoolkit.controls.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Element;

/**
 * Command for converting an XSD schema from version 1.0 to 1.1.
 * Adds the necessary vc:minVersion attribute and namespace declaration.
 */
public class ConvertToXsd11Command implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(ConvertToXsd11Command.class);
    private static final String VC_NS = "http://www.w3.org/2007/XMLSchema-versioning";
    private static final String VC_PREFIX = "vc";
    private static final String MIN_VERSION = "1.1";

    private final XsdDomManipulator domManipulator;

    // Store old values for undo
    private boolean hadVcNamespace;
    private String oldMinVersion;
    private Element schemaElement;

    public ConvertToXsd11Command(XsdDomManipulator domManipulator) {
        this.domManipulator = domManipulator;
    }

    @Override
    public boolean execute() {
        try {
            // Get the root schema element
            schemaElement = domManipulator.getDocument().getDocumentElement();

            if (schemaElement == null || !"schema".equals(schemaElement.getLocalName())) {
                logger.error("Root element is not a schema element");
                return false;
            }

            // Check if already XSD 1.1
            String currentVersion = schemaElement.getAttributeNS(VC_NS, "minVersion");
            if (MIN_VERSION.equals(currentVersion)) {
                logger.info("Schema is already XSD 1.1");
                return true;
            }

            // Store old values for undo
            hadVcNamespace = schemaElement.hasAttributeNS("http://www.w3.org/2000/xmlns/", VC_PREFIX);
            oldMinVersion = schemaElement.getAttributeNS(VC_NS, "minVersion");

            // Add vc namespace declaration if not present
            if (!hadVcNamespace) {
                schemaElement.setAttributeNS("http://www.w3.org/2000/xmlns/",
                        "xmlns:" + VC_PREFIX, VC_NS);
            }

            // Add vc:minVersion="1.1"
            schemaElement.setAttributeNS(VC_NS, VC_PREFIX + ":minVersion", MIN_VERSION);

            logger.info("Converted schema to XSD 1.1");
            return true;

        } catch (Exception e) {
            logger.error("Error converting to XSD 1.1", e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            if (schemaElement != null) {
                // Remove vc:minVersion attribute or restore old value
                if (oldMinVersion == null || oldMinVersion.isEmpty()) {
                    schemaElement.removeAttributeNS(VC_NS, "minVersion");
                } else {
                    schemaElement.setAttributeNS(VC_NS, VC_PREFIX + ":minVersion", oldMinVersion);
                }

                // Remove vc namespace declaration if it wasn't there before
                if (!hadVcNamespace) {
                    schemaElement.removeAttributeNS("http://www.w3.org/2000/xmlns/", VC_PREFIX);
                }

                logger.info("Reverted XSD 1.1 conversion");
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("Error undoing XSD 1.1 conversion", e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Convert schema to XSD 1.1";
    }
}
