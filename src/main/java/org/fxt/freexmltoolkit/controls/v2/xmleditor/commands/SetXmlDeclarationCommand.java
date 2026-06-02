package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;

/**
 * Command to set the XML declaration of a document: version, encoding and standalone flag
 * ({@code true}="yes", {@code false}="no", {@code null}=omitted). Undo restores all three.
 *
 * @since 2.0
 */
public class SetXmlDeclarationCommand implements XmlCommand {

    private final XmlDocument document;
    private final String newVersion;
    private final String newEncoding;
    private final Boolean newStandalone;
    private final String oldVersion;
    private final String oldEncoding;
    private final Boolean oldStandalone;
    private boolean executed = false;

    public SetXmlDeclarationCommand(XmlDocument document, String version, String encoding, Boolean standalone) {
        this.document = document;
        this.newVersion = (version == null || version.isBlank()) ? "1.0" : version.trim();
        this.newEncoding = (encoding == null || encoding.isBlank()) ? null : encoding.trim();
        this.newStandalone = standalone;
        this.oldVersion = document.getVersion();
        this.oldEncoding = document.getEncoding();
        this.oldStandalone = document.getStandalone();
    }

    @Override
    public boolean execute() {
        document.setVersion(newVersion);
        document.setEncoding(newEncoding);
        document.setStandalone(newStandalone);
        executed = true;
        return true;
    }

    @Override
    public boolean undo() {
        if (!executed) {
            return false;
        }
        document.setVersion(oldVersion);
        document.setEncoding(oldEncoding);
        document.setStandalone(oldStandalone);
        executed = false;
        return true;
    }

    @Override
    public String getDescription() {
        return "Edit XML Declaration";
    }
}
