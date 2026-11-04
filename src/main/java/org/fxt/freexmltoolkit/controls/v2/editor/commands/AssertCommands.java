package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.model.*;

/**
 * Commands for XSD 1.1 {@code xs:assert} on complex types: add and edit (delete is
 * {@link DeleteNodeCommand}).
 *
 * @since 2.6
 */
public final class AssertCommands {

    private AssertCommands() {
    }

    /**
     * @return the node new asserts of {@code owner} go into: the extension/restriction of a
     *         complex type with complex or simple content, else the complex type itself
     *         (an element's inline complex type for an element); null when none applies
     */
    public static XsdNode assertContainer(XsdNode owner) {
        XsdNode type = owner instanceof XsdElement element ? XsdEditRules.inlineType(element) : owner;
        if (!(type instanceof XsdComplexType complexType)) {
            return null;
        }
        for (XsdNode child : complexType.getChildren()) {
            if (child instanceof XsdComplexContent || child instanceof XsdSimpleContent) {
                for (XsdNode derivation : child.getChildren()) {
                    if (derivation instanceof XsdExtension || derivation instanceof XsdRestriction) {
                        return derivation;
                    }
                }
            }
        }
        return complexType;
    }

    /** @return the asserts of {@code owner}'s container, in document order */
    public static java.util.List<XsdAssert> assertsOf(XsdNode owner) {
        XsdNode container = assertContainer(owner);
        if (container == null) {
            return java.util.List.of();
        }
        return container.getChildren().stream()
                .filter(XsdAssert.class::isInstance).map(XsdAssert.class::cast).toList();
    }

    /** @return the check of an assert's test expression */
    public static EditCheck checkTest(String test) {
        return test == null || test.isBlank()
                ? EditCheck.blocked("Enter the XPath test of the assertion.")
                : EditCheck.allowed();
    }

    /** Adds an {@code xs:assert} at the end of the owner's assert container. */
    public static final class Add implements XsdCommand {
        private final XsdNode container;
        private final XsdAssert created;

        public Add(XsdNode owner, String test, String xpathDefaultNamespace) {
            this.container = assertContainer(owner);
            if (container == null) {
                throw new IllegalArgumentException("Assertions belong to complex types");
            }
            this.created = new XsdAssert(test == null ? "" : test.trim());
            if (xpathDefaultNamespace != null && !xpathDefaultNamespace.isBlank()) {
                created.setXpathDefaultNamespace(xpathDefaultNamespace.trim());
            }
        }

        @Override
        public boolean execute() {
            if (!checkTest(created.getTest()).isAllowed()) {
                return false;
            }
            container.addChild(created);
            return true;
        }

        @Override
        public boolean undo() {
            return container.removeChild(created);
        }

        @Override
        public String getDescription() {
            return "Add assertion";
        }

        /** @return the new assert */
        public XsdAssert getCreated() {
            return created;
        }
    }

    /** Changes test and xpathDefaultNamespace of an {@code xs:assert}. */
    public static final class Edit implements XsdCommand {
        private final XsdAssert target;
        private final String oldTest;
        private final String oldNamespace;
        private final String newTest;
        private final String newNamespace;

        public Edit(XsdAssert target, String test, String xpathDefaultNamespace) {
            if (target == null) {
                throw new IllegalArgumentException("Assert is required");
            }
            this.target = target;
            this.oldTest = target.getTest();
            this.oldNamespace = target.getXpathDefaultNamespace();
            this.newTest = test == null ? "" : test.trim();
            this.newNamespace = xpathDefaultNamespace == null || xpathDefaultNamespace.isBlank()
                    ? null : xpathDefaultNamespace.trim();
        }

        @Override
        public boolean execute() {
            if (!checkTest(newTest).isAllowed()) {
                return false;
            }
            target.setTest(newTest);
            target.setXpathDefaultNamespace(newNamespace);
            return true;
        }

        @Override
        public boolean undo() {
            target.setTest(oldTest);
            target.setXpathDefaultNamespace(oldNamespace);
            return true;
        }

        @Override
        public String getDescription() {
            return "Edit assertion";
        }
    }
}
