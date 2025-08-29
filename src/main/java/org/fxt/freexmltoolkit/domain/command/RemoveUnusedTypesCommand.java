package org.fxt.freexmltoolkit.domain.command;

import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

public class RemoveUnusedTypesCommand implements XsdCommand {
    private final Document xsdDocument;
    private final XsdDomManipulator domManipulator;
    private final List<Element> removedTypes = new ArrayList<>();
    private boolean wasExecuted = false;

    public RemoveUnusedTypesCommand(Document xsdDocument, XsdDomManipulator domManipulator) {
        this.xsdDocument = xsdDocument;
        this.domManipulator = domManipulator;
    }

    @Override
    public boolean execute() {
        if (wasExecuted) {
            return false;
        }

        var allTypes = domManipulator.getAllTypeDefinitions(xsdDocument);

        for (Element typeElement : allTypes) {
            String typeName = typeElement.getAttribute("name");
            if (typeName != null && !typeName.isEmpty()) {
                var usages = domManipulator.findTypeUsages(xsdDocument, typeName);
                if (usages.isEmpty()) {
                    removedTypes.add(typeElement);
                    typeElement.getParentNode().removeChild(typeElement);
                }
            }
        }

        wasExecuted = true;
        return true;
    }

    @Override
    public boolean undo() {
        if (!wasExecuted) {
            return false;
        }

        Element schema = xsdDocument.getDocumentElement();
        for (Element removedType : removedTypes) {
            schema.appendChild(removedType);
        }

        removedTypes.clear();
        wasExecuted = false;
        return true;
    }

    @Override
    public String getDescription() {
        return "Remove unused type definitions (" + removedTypes.size() + " types)";
    }

    public int getRemovedTypesCount() {
        return removedTypes.size();
    }
}