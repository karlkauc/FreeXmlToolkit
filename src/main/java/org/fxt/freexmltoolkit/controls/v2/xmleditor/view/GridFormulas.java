package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.CompositeXmlCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.InsertNodeCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetElementTextCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.SetProcessingInstructionCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.XmlCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;

/**
 * Grid formulas (XMLSpy: the Formula node type): an XQuery 3.1 expression evaluated with its
 * parent element as context, shown with its live result. A formula is persisted in the document
 * as a processing instruction {@code <?fxt-formula name="…" store="true|false" expression="…"?>},
 * so it is saved with the file and takes part in undo/redo. With {@code store="true"} the result
 * is also written into an element named after the formula, right after the instruction.
 */
public final class GridFormulas {

    /** The processing-instruction target of a formula. */
    public static final String TARGET = "fxt-formula";

    private static final Pattern FIELD = Pattern.compile("(\\w+)=\"((?:[^\"&]|&quot;|&amp;|&gt;)*)\"");

    private GridFormulas() {
    }

    /**
     * A formula definition.
     *
     * @param name       the formula's name (also the element name of a stored result)
     * @param expression the XQuery expression
     * @param store      whether the result is written into the document
     */
    public record Formula(String name, String expression, boolean store) {

        /** @return the processing-instruction data of this formula */
        public String toData() {
            return "name=\"" + escape(name) + "\" store=\"" + store + "\" expression=\"" + escape(expression) + "\"";
        }

        /** @return a copy with another name */
        public Formula withName(String newName) {
            return new Formula(newName, expression, store);
        }

        /** @return a copy with another expression */
        public Formula withExpression(String newExpression) {
            return new Formula(name, newExpression, store);
        }

        /** @return a copy with another store flag */
        public Formula withStore(boolean newStore) {
            return new Formula(name, expression, newStore);
        }
    }

    /**
     * The evaluated view of a formula row.
     *
     * @param formula the definition
     * @param result  the result text ({@code null} on error)
     * @param error   the error message ({@code null} on success)
     */
    public record Evaluated(Formula formula, String result, String error) {
    }

    /** @return whether the node is a formula instruction */
    public static boolean isFormula(XmlNode node) {
        return node instanceof XmlProcessingInstruction pi && TARGET.equals(pi.getTarget());
    }

    /** @return the formula stored in a processing instruction (fields missing = defaults) */
    public static Formula parse(String data) {
        String name = "formula";
        String expression = "";
        boolean store = false;
        if (data != null) {
            Matcher m = FIELD.matcher(data);
            while (m.find()) {
                String value = unescape(m.group(2));
                switch (m.group(1)) {
                    case "name" -> name = value;
                    case "expression" -> expression = value;
                    case "store" -> store = Boolean.parseBoolean(value);
                    default -> { /* unknown field: ignored */ }
                }
            }
        }
        return new Formula(name, expression, store);
    }

    /** @return a new formula instruction */
    public static XmlProcessingInstruction create(Formula formula) {
        return new XmlProcessingInstruction(TARGET, formula.toData());
    }

    /** @return the command that replaces the formula of an instruction */
    public static XmlCommand update(XmlProcessingInstruction pi, Formula formula) {
        return new SetProcessingInstructionCommand(pi, TARGET, formula.toData());
    }

    /** Evaluates a formula instruction against its parent element. */
    public static Evaluated evaluate(XmlProcessingInstruction pi) {
        Formula formula = parse(pi.getData());
        if (!(pi.getParent() instanceof XmlElement context)) {
            return new Evaluated(formula, null, "A formula needs a parent element");
        }
        try {
            return new Evaluated(formula, XmlGridQuery.evaluate(context, formula.expression()).text(), null);
        } catch (IllegalArgumentException e) {
            return new Evaluated(formula, null, e.getMessage());
        }
    }

    /**
     * The command that writes the formula's current result into the element after the
     * instruction (created when missing), or {@code null} when the formula has an error.
     */
    public static XmlCommand storeResult(XmlProcessingInstruction pi) {
        Evaluated evaluated = evaluate(pi);
        if (evaluated.error() != null || !(pi.getParent() instanceof XmlElement parent)) {
            return null;
        }
        String name = evaluated.formula().name();
        int index = parent.indexOf(pi);
        XmlNode next = index + 1 < parent.getChildCount() ? parent.getChild(index + 1) : null;
        if (next instanceof XmlText text && text.isWhitespace() && index + 2 < parent.getChildCount()) {
            next = parent.getChild(index + 2);
        }
        if (next instanceof XmlElement existing && existing.getName().equals(name) && !existing.hasElementChildren()) {
            return new SetElementTextCommand(existing, evaluated.result());
        }
        XmlElement result = new XmlElement(name);
        result.addChild(new XmlText(evaluated.result()));
        return new InsertNodeCommand(parent, result, index + 1);
    }

    /**
     * @return one undoable step that refreshes the stored result of every formula with
     *         {@code store="true"} in the document (empty when there is nothing to do)
     */
    public static CompositeXmlCommand recalculateAll(XmlDocument document) {
        List<XmlProcessingInstruction> formulas = new ArrayList<>();
        collect(document.getRootElement(), formulas);
        List<XmlCommand> parts = new ArrayList<>();
        for (XmlProcessingInstruction pi : formulas) {
            if (parse(pi.getData()).store()) {
                XmlCommand cmd = storeResult(pi);
                if (cmd != null) {
                    parts.add(cmd);
                }
            }
        }
        return new CompositeXmlCommand("Recalculate formulas", parts);
    }

    private static void collect(XmlElement element, List<XmlProcessingInstruction> out) {
        if (element == null) {
            return;
        }
        for (XmlNode child : element.getChildren()) {
            if (isFormula(child)) {
                out.add((XmlProcessingInstruction) child);
            } else if (child instanceof XmlElement el) {
                collect(el, out);
            }
        }
    }

    private static String escape(String s) {
        // ">" is escaped too: the data must never contain the "?>" that ends the instruction
        return s == null ? "" : s.replace("&", "&amp;").replace("\"", "&quot;").replace(">", "&gt;");
    }

    private static String unescape(String s) {
        return s.replace("&quot;", "\"").replace("&gt;", ">").replace("&amp;", "&");
    }
}
