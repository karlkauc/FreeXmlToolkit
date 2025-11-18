package org.fxt.freexmltoolkit.controls.v2.editor.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.XmlEditor;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;

import java.util.Objects;

/**
 * Implementation of XmlSchemaProvider that adapts from XmlEditor.
 * This decouples XmlCodeEditorV2 from the specific XmlEditor implementation.
 */
public class XmlSchemaProviderImpl implements XmlSchemaProvider {

    private static final Logger logger = LogManager.getLogger(XmlSchemaProviderImpl.class);

    private final XmlEditor xmlEditor;

    /**
     * Creates a new schema provider.
     *
     * @param xmlEditor the XML editor instance
     */
    public XmlSchemaProviderImpl(XmlEditor xmlEditor) {
        this.xmlEditor = Objects.requireNonNull(xmlEditor, "XmlEditor cannot be null");
        logger.debug("XmlSchemaProviderImpl created");
    }

    @Override
    public boolean hasSchema() {
        return xmlEditor.getXsdFile() != null;
    }

    @Override
    public XsdDocumentationData getXsdDocumentationData() {
        return xmlEditor.getXsdDocumentationData();
    }

    @Override
    public String getXsdFilePath() {
        var xsdFile = xmlEditor.getXsdFile();
        return xsdFile != null ? xsdFile.getAbsolutePath() : null;
    }

    @Override
    public XsdExtendedElement findBestMatchingElement(String xpath) {
        return xmlEditor.findBestMatchingElement(xpath);
    }
}
