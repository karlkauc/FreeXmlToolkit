package org.fxt.freexmltoolkit.controls.v2.editor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.XmlEditor;
import org.fxt.freexmltoolkit.controls.v2.editor.services.XmlSchemaProvider;
import org.fxt.freexmltoolkit.controls.v2.editor.services.XmlSchemaProviderImpl;

/**
 * Factory for creating XmlCodeEditorV2 instances.
 * Provides convenient factory methods for different use cases.
 */
public class XmlCodeEditorV2Factory {

    private static final Logger logger = LogManager.getLogger(XmlCodeEditorV2Factory.class);

    private XmlCodeEditorV2Factory() {
        // Utility class
    }

    /**
     * Creates an editor with a custom schema provider.
     *
     * @param schemaProvider the schema provider
     * @return the editor instance
     */
    public static XmlCodeEditorV2 create(XmlSchemaProvider schemaProvider) {
        logger.debug("Creating XmlCodeEditorV2 with custom schema provider");
        return new XmlCodeEditorV2(schemaProvider);
    }

    /**
     * Creates an editor that uses an XmlEditor as schema source.
     *
     * @param xmlEditor the parent XML editor
     * @return the editor instance
     */
    public static XmlCodeEditorV2 createForXmlEditor(XmlEditor xmlEditor) {
        logger.debug("Creating XmlCodeEditorV2 for XmlEditor");
        XmlSchemaProvider provider = new XmlSchemaProviderImpl(xmlEditor);
        return new XmlCodeEditorV2(provider);
    }

    /**
     * Creates an editor without schema support.
     *
     * @return the editor instance
     */
    public static XmlCodeEditorV2 createWithoutSchema() {
        logger.debug("Creating XmlCodeEditorV2 without schema");
        XmlSchemaProvider noSchemaProvider = new XmlSchemaProvider() {
            @Override
            public boolean hasSchema() {
                return false;
            }

            @Override
            public org.fxt.freexmltoolkit.domain.XsdDocumentationData getXsdDocumentationData() {
                return null;
            }

            @Override
            public String getXsdFilePath() {
                return null;
            }

            @Override
            public org.fxt.freexmltoolkit.domain.XsdExtendedElement findBestMatchingElement(String xpath) {
                return null;
            }
        };
        return new XmlCodeEditorV2(noSchemaProvider);
    }
}
