package org.fxt.freexmltoolkit.domain.command;

import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class CloneTypeCommand implements XsdCommand {
    private final Document xsdDocument;
    private final String sourceTypeName;
    private final String newTypeName;
    private final XsdDomManipulator domManipulator;
    private Element clonedTypeDefinition;
    private boolean wasExecuted = false;

    public CloneTypeCommand(Document xsdDocument, String sourceTypeName, String newTypeName, XsdDomManipulator domManipulator) {
        this.xsdDocument = xsdDocument;
        this.sourceTypeName = sourceTypeName;
        this.newTypeName = newTypeName;
        this.domManipulator = domManipulator;
    }

    @Override
    public boolean execute() {
        if (wasExecuted) {
            return false;
        }

        Element sourceType = domManipulator.findTypeDefinition(xsdDocument, sourceTypeName);
        if (sourceType == null) {
            throw new IllegalArgumentException("Source type definition not found: " + sourceTypeName);
        }

        if (domManipulator.findTypeDefinition(xsdDocument, newTypeName) != null) {
            throw new IllegalArgumentException("Target type name already exists: " + newTypeName);
        }

        clonedTypeDefinition = (Element) sourceType.cloneNode(true);
        clonedTypeDefinition.setAttribute("name", newTypeName);

        Element schema = xsdDocument.getDocumentElement();
        schema.appendChild(clonedTypeDefinition);

        wasExecuted = true;
        return true;
    }

    @Override
    public boolean undo() {
        if (!wasExecuted || clonedTypeDefinition == null) {
            return false;
        }

        clonedTypeDefinition.getParentNode().removeChild(clonedTypeDefinition);
        wasExecuted = false;
        return true;
    }

    @Override
    public String getDescription() {
        return "Clone type " + sourceTypeName + " to " + newTypeName;
    }
}