package org.fxt.freexmltoolkit.domain.command;

import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class InlineTypeCommand implements XsdCommand {
    private final Document xsdDocument;
    private final String typeName;
    private final XsdDomManipulator domManipulator;
    private Element originalTypeDefinition;
    private boolean wasExecuted = false;

    public InlineTypeCommand(Document xsdDocument, String typeName, XsdDomManipulator domManipulator) {
        this.xsdDocument = xsdDocument;
        this.typeName = typeName;
        this.domManipulator = domManipulator;
    }

    @Override
    public boolean execute() {
        if (wasExecuted) {
            return false;
        }

        originalTypeDefinition = domManipulator.findTypeDefinition(xsdDocument, typeName);
        if (originalTypeDefinition == null) {
            throw new IllegalArgumentException("Type definition not found: " + typeName);
        }

        var usages = domManipulator.findTypeUsages(xsdDocument, typeName);
        if (usages.isEmpty()) {
            throw new IllegalStateException("Cannot inline type with no usages: " + typeName);
        }

        for (Element usage : usages) {
            domManipulator.inlineTypeDefinition(usage, originalTypeDefinition);
        }

        originalTypeDefinition.getParentNode().removeChild(originalTypeDefinition);
        wasExecuted = true;
        return true;
    }

    @Override
    public boolean undo() {
        if (!wasExecuted) {
            return false;
        }

        var schema = xsdDocument.getDocumentElement();
        schema.appendChild(originalTypeDefinition);

        var usages = domManipulator.findElementsWithInlinedContent(xsdDocument, typeName);
        for (Element usage : usages) {
            domManipulator.restoreTypeReference(usage, typeName);
        }

        wasExecuted = false;
        return true;
    }

    @Override
    public String getDescription() {
        return "Inline type definition: " + typeName;
    }
}