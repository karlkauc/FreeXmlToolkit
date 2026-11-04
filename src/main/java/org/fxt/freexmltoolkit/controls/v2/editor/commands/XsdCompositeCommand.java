package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import java.util.ArrayList;
import java.util.List;

/**
 * Several commands as one undo step. Execution runs them in order; if one fails, the
 * ones already executed are undone and the composite reports failure. Undo runs in
 * reverse order.
 *
 * @since 2.6
 */
public class XsdCompositeCommand implements XsdCommand {

    private final String description;
    private final List<XsdCommand> commands;

    /**
     * @param description the text shown in the undo history
     * @param commands    the parts, executed in order
     */
    public XsdCompositeCommand(String description, List<? extends XsdCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            throw new IllegalArgumentException("A composite command needs at least one part");
        }
        this.description = description;
        this.commands = List.copyOf(commands);
    }

    @Override
    public boolean execute() {
        List<XsdCommand> done = new ArrayList<>();
        for (XsdCommand command : commands) {
            if (!command.execute()) {
                for (int i = done.size() - 1; i >= 0; i--) {
                    done.get(i).undo();
                }
                return false;
            }
            done.add(command);
        }
        return true;
    }

    @Override
    public boolean undo() {
        boolean ok = true;
        for (int i = commands.size() - 1; i >= 0; i--) {
            ok &= commands.get(i).undo();
        }
        return ok;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public boolean canUndo() {
        return commands.stream().allMatch(XsdCommand::canUndo);
    }

    /** @return the parts, in execution order */
    public List<XsdCommand> getCommands() {
        return commands;
    }
}
