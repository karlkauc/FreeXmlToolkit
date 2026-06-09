package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlCData;

/** Command to set the content of a CDATA section, with undo. @since 2.0 */
public class SetCDataTextCommand implements XmlCommand {

    private final XmlCData cdata;
    private final String newText;
    private final String oldText;
    private boolean executed = false;

    public SetCDataTextCommand(XmlCData cdata, String newText) {
        this.cdata = cdata;
        this.newText = newText;
        this.oldText = cdata.getText();
    }

    @Override
    public boolean execute() {
        cdata.setText(newText);
        executed = true;
        return true;
    }

    @Override
    public boolean undo() {
        if (!executed) {
            return false;
        }
        cdata.setText(oldText);
        executed = false;
        return true;
    }

    @Override
    public String getDescription() {
        return "Edit CDATA";
    }
}
