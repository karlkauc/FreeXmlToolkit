package org.fxt.freexmltoolkit.service;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * Eine Wrapper-Klasse, die einen XMLStreamWriter um Einrückungen erweitert.
 */
public class IndentingXMLStreamWriter extends DelegatingXMLStreamWriter {
    private int indentLevel = 0;
    private final String indentChar = "  "; // 2 Leerzeichen

    public IndentingXMLStreamWriter(XMLStreamWriter delegate) {
        super(delegate);
    }

    @Override
    public void writeStartElement(String localName) throws XMLStreamException {
        writeIndent();
        super.writeStartElement(localName);
        indentLevel++;
    }

    @Override
    public void writeEndElement() throws XMLStreamException {
        indentLevel--;
        writeIndent();
        super.writeEndElement();
    }

    @Override
    public void writeEmptyElement(String localName) throws XMLStreamException {
        writeIndent();
        super.writeEmptyElement(localName);
    }

    private void writeIndent() throws XMLStreamException {
        super.writeCharacters("\n");
        for (int i = 0; i < indentLevel; i++) {
            super.writeCharacters(indentChar);
        }
    }
}