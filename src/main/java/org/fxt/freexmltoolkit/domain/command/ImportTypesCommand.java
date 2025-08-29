package org.fxt.freexmltoolkit.domain.command;

import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

public class ImportTypesCommand implements XsdCommand {
    private final Document targetDocument;
    private final Document sourceDocument;
    private final List<String> typeNames;
    private final XsdDomManipulator domManipulator;
    private final List<Element> importedTypes = new ArrayList<>();
    private boolean wasExecuted = false;

    public ImportTypesCommand(Document targetDocument, Document sourceDocument, List<String> typeNames, XsdDomManipulator domManipulator) {
        this.targetDocument = targetDocument;
        this.sourceDocument = sourceDocument;
        this.typeNames = typeNames;
        this.domManipulator = domManipulator;
    }

    @Override
    public boolean execute() {
        if (wasExecuted) {
            return false;
        }

        Element targetSchema = targetDocument.getDocumentElement();

        for (String typeName : typeNames) {
            Element sourceType = domManipulator.findTypeDefinition(sourceDocument, typeName);
            if (sourceType == null) {
                continue;
            }

            if (domManipulator.findTypeDefinition(targetDocument, typeName) != null) {
                continue;
            }

            Element importedType = (Element) targetDocument.importNode(sourceType, true);
            targetSchema.appendChild(importedType);
            importedTypes.add(importedType);
        }

        wasExecuted = true;
        return true;
    }

    @Override
    public boolean undo() {
        if (!wasExecuted) {
            return false;
        }

        for (Element importedType : importedTypes) {
            if (importedType.getParentNode() != null) {
                importedType.getParentNode().removeChild(importedType);
            }
        }

        importedTypes.clear();
        wasExecuted = false;
        return true;
    }

    @Override
    public String getDescription() {
        return "Import " + importedTypes.size() + " type definitions";
    }

    public int getImportedTypesCount() {
        return importedTypes.size();
    }
}