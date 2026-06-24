package org.fxt.freexmltoolkit.controls.v2.editor.commands;

/**
 * A merged command formed when two consecutive, mergeable commands are collapsed into a single
 * undo step. It reverts to the <em>first</em> command's original state on {@link #undo()} and
 * re-applies the <em>second</em> command's final state on {@link #execute()} (redo).
 * <p>
 * This is correct for absolute-value commands (e.g. property setters) whose {@code execute()}
 * sets a value outright rather than applying a relative delta — undoing the first restores the
 * original value and redoing the second restores the final value, regardless of the intermediate
 * step. It is <strong>not</strong> suitable for structural add/remove commands; those must
 * provide their own {@code mergeWith}.
 *
 * @since 2.0
 */
public final class XsdMergedCommand implements XsdCommand {

    private final XsdCommand first;
    private final XsdCommand second;

    /**
     * Creates a merged command. Both {@code first} and {@code second} are expected to have already
     * executed (the command manager merges after executing).
     *
     * @param first  the earlier command (its {@code undo} restores the original state)
     * @param second the later command (its {@code execute} restores the final state)
     */
    public XsdMergedCommand(XsdCommand first, XsdCommand second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public boolean execute() {
        return second.execute();
    }

    @Override
    public boolean undo() {
        return first.undo();
    }

    @Override
    public String getDescription() {
        return second.getDescription();
    }

    @Override
    public boolean canUndo() {
        return first.canUndo() && second.canUndo();
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        return second.canMergeWith(other);
    }

    @Override
    public XsdCommand mergeWith(XsdCommand other) {
        return new XsdMergedCommand(first, second.mergeWith(other));
    }
}
