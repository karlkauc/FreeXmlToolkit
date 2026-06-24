package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdField;
import org.fxt.freexmltoolkit.controls.v2.model.XsdIdentityConstraint;
import org.fxt.freexmltoolkit.controls.v2.model.XsdKey;
import org.fxt.freexmltoolkit.controls.v2.model.XsdKeyRef;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSelector;
import org.fxt.freexmltoolkit.controls.v2.model.XsdUnique;

/**
 * Command to add an identity constraint ({@code xs:key}, {@code xs:keyref} or {@code xs:unique})
 * to an XSD element. The new constraint is created with its {@code xs:selector} and one or more
 * {@code xs:field} children and appended after the element's type definition.
 *
 * @since 2.0
 */
public class AddIdentityConstraintCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(AddIdentityConstraintCommand.class);

    /** The kind of identity constraint to create. */
    public enum Kind {
        KEY, KEYREF, UNIQUE
    }

    private final XsdEditorContext editorContext;
    private final XsdElement element;
    private final Kind kind;
    private final String name;
    private final String selectorXpath;
    private final List<String> fieldXpaths;
    private final String refer;

    private XsdIdentityConstraint created;

    /**
     * Creates a new add-identity-constraint command.
     *
     * @param editorContext the editor context
     * @param element       the element to add the constraint to
     * @param kind          key / keyref / unique
     * @param name          the constraint name
     * @param selectorXpath the selector XPath (e.g. {@code .//item})
     * @param fieldXpaths   one or more field XPaths (e.g. {@code @id})
     * @param refer         the {@code refer} target (keyref only; ignored otherwise)
     * @throws IllegalArgumentException if required arguments are missing
     */
    public AddIdentityConstraintCommand(XsdEditorContext editorContext, XsdNode element, Kind kind,
                                        String name, String selectorXpath, List<String> fieldXpaths, String refer) {
        if (editorContext == null) {
            throw new IllegalArgumentException("Editor context cannot be null");
        }
        if (!(element instanceof XsdElement)) {
            throw new IllegalArgumentException("Identity constraints can only be added to elements, not to "
                    + (element == null ? "null" : element.getClass().getSimpleName()));
        }
        if (kind == null) {
            throw new IllegalArgumentException("Constraint kind cannot be null");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Constraint name cannot be empty");
        }
        if (selectorXpath == null || selectorXpath.trim().isEmpty()) {
            throw new IllegalArgumentException("Selector XPath cannot be empty");
        }
        if (fieldXpaths == null || fieldXpaths.stream().noneMatch(f -> f != null && !f.trim().isEmpty())) {
            throw new IllegalArgumentException("At least one field XPath is required");
        }
        this.editorContext = editorContext;
        this.element = (XsdElement) element;
        this.kind = kind;
        this.name = name.trim();
        this.selectorXpath = selectorXpath.trim();
        this.fieldXpaths = fieldXpaths.stream().filter(f -> f != null && !f.trim().isEmpty()).map(String::trim).toList();
        this.refer = refer == null ? null : refer.trim();
    }

    private XsdIdentityConstraint build() {
        XsdIdentityConstraint constraint = switch (kind) {
            case KEY -> new XsdKey(name);
            case UNIQUE -> new XsdUnique(name);
            case KEYREF -> {
                XsdKeyRef keyRef = new XsdKeyRef(name);
                keyRef.setRefer(refer);
                yield keyRef;
            }
        };
        constraint.addChild(new XsdSelector(selectorXpath));
        for (String field : fieldXpaths) {
            constraint.addChild(new XsdField(field));
        }
        return constraint;
    }

    @Override
    public boolean execute() {
        try {
            if (created == null) {
                created = build();
            }
            element.addChild(created);
            editorContext.markNodeDirty(element);
            return true;
        } catch (Exception e) {
            logger.error("Failed to add identity constraint to '{}'", element.getName(), e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            if (created != null) {
                element.removeChild(created);
                editorContext.markNodeDirty(element);
            }
            return true;
        } catch (Exception e) {
            logger.error("Failed to undo identity-constraint add on '{}'", element.getName(), e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Add " + kind.name().toLowerCase() + " '" + name + "' to "
                + (element.getName() != null ? element.getName() : "(unnamed)");
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    /** @return the constraint created by this command (for tests); null before execute. */
    public XsdIdentityConstraint getCreated() {
        return created;
    }
}
