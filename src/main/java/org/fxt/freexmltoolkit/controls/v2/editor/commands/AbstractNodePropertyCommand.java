package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Base class for commands that change a single, undoable property of an {@link XsdNode}.
 * <p>
 * Subclasses provide the property's current value ({@link #readValue()}), how to write it
 * ({@link #writeValue(Object)}), and a human-readable property name. This base handles
 * execute/undo, dirty marking, and merging of consecutive edits to the same property on the
 * same node (so typing into a text field collapses into one undo step).
 *
 * @param <T> the property value type (e.g. {@code String}, an enum)
 * @since 2.0
 */
public abstract class AbstractNodePropertyCommand<T> implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(AbstractNodePropertyCommand.class);

    /** The editor context, used to mark the node dirty after a change. */
    protected final XsdEditorContext editorContext;
    /** The node whose property is changed. */
    protected final XsdNode node;

    private final T oldValue;
    private final T newValue;

    /**
     * Creates a command that will set the property to {@code newValue}, capturing the current
     * value as the undo target.
     *
     * @param editorContext the editor context (must not be null)
     * @param node          the target node (must not be null)
     * @param newValue      the value to apply on execute
     */
    protected AbstractNodePropertyCommand(XsdEditorContext editorContext, XsdNode node, T newValue) {
        if (editorContext == null) {
            throw new IllegalArgumentException("Editor context cannot be null");
        }
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        this.editorContext = editorContext;
        this.node = node;
        this.oldValue = readValue();
        this.newValue = newValue;
    }

    /** Creates a command with an explicit old value (used for merging). */
    private AbstractNodePropertyCommand(XsdEditorContext editorContext, XsdNode node, T oldValue, T newValue) {
        this.editorContext = editorContext;
        this.node = node;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    /** @return the property's current value on {@link #node}. */
    protected abstract T readValue();

    /** Writes {@code value} to the property on {@link #node}. */
    protected abstract void writeValue(T value);

    /** @return a short, human-readable property name for the command description. */
    protected abstract String propertyName();

    private void apply(T value) {
        writeValue(value);
        editorContext.markNodeDirty(node);
    }

    @Override
    public boolean execute() {
        try {
            apply(newValue);
            return true;
        } catch (Exception e) {
            logger.error("Failed to change {} of '{}'", propertyName(), node.getName(), e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            apply(oldValue);
            return true;
        } catch (Exception e) {
            logger.error("Failed to undo {} change of '{}'", propertyName(), node.getName(), e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Change " + propertyName() + " of " + (node.getName() != null ? node.getName() : "(unnamed)");
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        return other != null
                && other.getClass() == getClass()
                && this.node.getId().equals(((AbstractNodePropertyCommand<?>) other).node.getId());
    }

    @Override
    @SuppressWarnings("unchecked")
    public XsdCommand mergeWith(XsdCommand other) {
        if (!canMergeWith(other)) {
            throw new IllegalArgumentException("Cannot merge with " + other);
        }
        AbstractNodePropertyCommand<T> o = (AbstractNodePropertyCommand<T>) other;
        // Combine "old -> mine" + "mine -> o.new" into "old -> o.new" by cloning this concrete
        // command and overriding its value pair via the merge constructor.
        return new Merged<>(this, o.newValue);
    }

    /** @return the value applied on execute (for tests). */
    public T getNewValue() {
        return newValue;
    }

    /** @return the value restored on undo (for tests). */
    public T getOldValue() {
        return oldValue;
    }

    /**
     * A merged command that reuses a concrete command's read/write hooks but carries an explicit
     * old/new value pair spanning the merged range.
     */
    private static final class Merged<T> extends AbstractNodePropertyCommand<T> {
        private final AbstractNodePropertyCommand<T> delegate;

        Merged(AbstractNodePropertyCommand<T> origin, T newValue) {
            super(origin.editorContext, origin.node, origin.oldValue, newValue);
            this.delegate = origin;
        }

        @Override
        protected T readValue() {
            return delegate.readValue();
        }

        @Override
        protected void writeValue(T value) {
            delegate.writeValue(value);
        }

        @Override
        protected String propertyName() {
            return delegate.propertyName();
        }

        @Override
        public boolean canMergeWith(XsdCommand other) {
            // Defer to the concrete command's merge rules (e.g. same schema-property only).
            return delegate.canMergeWith(other);
        }
    }
}
