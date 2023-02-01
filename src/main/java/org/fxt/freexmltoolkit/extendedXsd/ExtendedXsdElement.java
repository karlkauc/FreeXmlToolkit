package org.fxt.freexmltoolkit.extendedXsd;

import org.xmlet.xsdparser.xsdelements.XsdDocumentation;
import org.xmlet.xsdparser.xsdelements.XsdElement;

import java.util.List;

public class ExtendedXsdElement {
    XsdElement xsdElement;
    int level;
    List<XsdDocumentation> xsdDocumentation;

    String currentXpath;

    public XsdElement getXsdElement() {
        return xsdElement;
    }

    public void setXsdElement(XsdElement xsdElement) {
        this.xsdElement = xsdElement;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public List<XsdDocumentation> getXsdDocumentation() {
        return xsdDocumentation;
    }

    public void setXsdDocumentation(List<XsdDocumentation> xsdDocumentation) {
        this.xsdDocumentation = xsdDocumentation;
    }

    public String getCurrentXpath() {
        return currentXpath;
    }

    public void setCurrentXpath(String currentXpath) {
        this.currentXpath = currentXpath;
    }
}
