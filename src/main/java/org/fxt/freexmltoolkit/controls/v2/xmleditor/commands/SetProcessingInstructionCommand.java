package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction;

/** Command to set a processing instruction's target and data, with undo. @since 2.0 */
public class SetProcessingInstructionCommand implements XmlCommand {

    private final XmlProcessingInstruction pi;
    private final String newTarget;
    private final String newData;
    private final String oldTarget;
    private final String oldData;
    private boolean executed = false;

    public SetProcessingInstructionCommand(XmlProcessingInstruction pi, String target, String data) {
        this.pi = pi;
        this.newTarget = target;
        this.newData = data;
        this.oldTarget = pi.getTarget();
        this.oldData = pi.getData();
    }

    @Override
    public boolean execute() {
        if (newTarget != null && !newTarget.isBlank()) {
            pi.setTarget(newTarget.trim());
        }
        pi.setData(newData == null ? "" : newData);
        executed = true;
        return true;
    }

    @Override
    public boolean undo() {
        if (!executed) {
            return false;
        }
        pi.setTarget(oldTarget);
        pi.setData(oldData);
        executed = false;
        return true;
    }

    @Override
    public String getDescription() {
        return "Edit Processing Instruction";
    }
}
